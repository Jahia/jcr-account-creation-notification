package org.jahia.modules.jcraccountcreationnotification;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.security.JahiaLoginModule;
import org.jahia.services.content.JCREventIterator;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.mail.MailService;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * OSGi component that registers a JCR {@link EventListener} on {@code /users} and sends an
 * email notification whenever a {@code jnt:user} node is added.
 *
 * <p>The component is {@code immediate=true} and publishes no service ({@code service={}});
 * it self-registers as a JCR observation listener in {@link #activate()}.
 *
 * <p>Fields read on JCR event threads ({@code observationSession}, {@code config},
 * {@code mailService}) are declared {@code volatile} so that writes performed during OSGi
 * bind/activate are visible to the event thread without a data race.  The {@link Reference}
 * policy is {@code STATIC} (the default) which guarantees the component is stopped and
 * restarted whenever a dependency changes, keeping the volatile approach simple and correct.
 */
// S3077: volatile references to thread-safe/immutable objects — safe-publication idiom; do not remove volatile
@SuppressWarnings("java:S3077")
@Component(immediate = true, service = {})
public final class JcrAccountCreationListener implements EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(JcrAccountCreationListener.class);
    private static final String USERS_PATH = "/users";
    private static final String JNT_USER = "jnt:user";
    private static final String CRLF_PATTERN = "[\\r\\n]";
    private static final String HEADER_REPLACEMENT = " ";
    private static final String LOG_CONTROL_PATTERN = "[\\r\\n\\t]";
    private static final String LOG_REPLACEMENT = "_";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    // volatile: written by OSGi activate/bind thread, read by JCR observation thread
    private volatile Session observationSession;
    private volatile MailService mailService;
    private volatile JcrAccountCreationNotificationConfig config;

    @Reference(policy = ReferencePolicy.STATIC)
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Reference(policy = ReferencePolicy.STATIC)
    public void setConfig(JcrAccountCreationNotificationConfig config) {
        this.config = config;
    }

    /**
     * Test seam (package-private): supplies an observation session without a full JCR login.
     * Production sets this in {@link #activate()}; behaviour tests use it to exercise
     * {@link #onEvent(EventIterator)} past the not-registered guard.
     */
    void setObservationSession(Session observationSession) {
        this.observationSession = observationSession;
    }

    @Activate
    public void activate() {
        try {
            observationSession = JCRSessionFactory.getInstance().login(JahiaLoginModule.getSystemCredentials(), "default");
            observationSession.getWorkspace().getObservationManager()
                    .addEventListener(this, Event.NODE_ADDED, USERS_PATH, true, null, new String[]{JNT_USER}, false);
            LOGGER.info("JCR account creation listener registered on {}", USERS_PATH);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to register JCR account creation listener", e);
        }
    }

    @Deactivate
    public void deactivate() {
        if (observationSession != null) {
            try {
                observationSession.getWorkspace().getObservationManager().removeEventListener(this);
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to unregister JCR account creation listener", e);
            }
            observationSession.logout();
            observationSession = null;
            LOGGER.info("JCR account creation listener unregistered");
        }
    }

    @Override
    public void onEvent(EventIterator events) {
        if (observationSession == null) {
            LOGGER.warn("JCR account creation listener is not registered (activation failed); event ignored");
            return;
        }
        if (events instanceof JCREventIterator
                && ((JCREventIterator) events).getOperationType() == JCRObservationManager.IMPORT) {
            return;
        }
        if (!mailService.isEnabled()) {
            return;
        }
        while (events.hasNext()) {
            final Event event = events.nextEvent();
            try {
                handleUserCreation(event);
            } catch (RepositoryException e) {
                // A RepositoryException (e.g. dead session) will repeat for every remaining
                // event in this batch and cannot be recovered within a single onEvent call.
                // Log once at WARN and stop processing the rest of the batch.
                LOGGER.warn("Stopping event-batch processing: repository error on event (session may be dead)", e);
                break;
            } catch (Exception e) {
                // Catch all other exceptions — an uncaught RuntimeException escaping to the
                // JCR observation manager can cause the listener to be silently deregistered,
                // which would stop all future notifications without any obvious indication.
                LOGGER.error("Error processing JCR user creation event", e);
            }
        }
    }

    void handleUserCreation(Event event) throws RepositoryException {
        final String path = event.getPath();
        final String username = StringUtils.substringAfterLast(path, "/");
        final String creator = StringUtils.defaultString(event.getUserID(), "system");
        final String creationTime = DATE_FORMATTER.format(Instant.ofEpochMilli(event.getDate()));
        final String serverName = resolveServerName();

        // Single volatile read of snapshot — all four fields come from the same consistent config version.
        final JcrAccountCreationNotificationConfig.Snapshot snap = this.config.getSnapshot();
        final String sender = sanitizeHeader(StringUtils.defaultIfEmpty(snap.sender, mailService.defaultSender()));
        final String recipient = sanitizeHeader(StringUtils.defaultIfEmpty(snap.recipient, mailService.defaultRecipient()));
        final String subject = sanitizeHeader(StringUtils.defaultString(snap.subject).replace("{server}", serverName));
        final String body = StringUtils.defaultString(snap.body)
                .replace("{username}", escapeHtml(username))
                .replace("{creator}", escapeHtml(creator))
                .replace("{time}", escapeHtml(creationTime));

        if (!isValidEmail(recipient)) {
            LOGGER.warn("Skipping JCR account creation notification: recipient is missing or has an invalid email format");
            return;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Sending JCR account creation notification for user '{}'", sanitizeForLog(username));
        }
        try {
            mailService.sendMessage(sender, recipient, null, null, subject, null, body);
        } catch (RuntimeException e) {
            // A mail/SMTP failure must not break JCR account creation; log and continue.
            LOGGER.error("Failed to send JCR account creation notification", e);
        }
    }

    /**
     * Validates that the given string is a non-empty, syntactically plausible email address.
     * Uses {@link JcrAccountCreationNotificationConfig#EMAIL_PATTERN} — the canonical pattern
     * shared with the mutation extension (which uses the same regex with optional semantics).
     * Here the check is required: a missing recipient skips the notification entirely.
     */
    static boolean isValidEmail(String email) {
        return email != null && !email.isEmpty()
                && JcrAccountCreationNotificationConfig.EMAIL_PATTERN.matcher(email).matches();
    }

    static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll(LOG_CONTROL_PATTERN, LOG_REPLACEMENT);
    }

    static String sanitizeHeader(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll(CRLF_PATTERN, HEADER_REPLACEMENT);
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String resolveServerName() {
        final SettingsBean settings = SettingsBean.getInstance();
        if (settings != null) {
            final String name = settings.getServer();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
