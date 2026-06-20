package org.jahia.modules.jcraccountcreationnotification;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;

import java.util.Dictionary;
import java.util.regex.Pattern;

/**
 * Holds OSGi ConfigurationAdmin settings for the JCR account-creation notification module.
 *
 * <p>Thread-safety: {@link #updated(Dictionary)} may be called on the OSGi CM thread while
 * {@link JcrAccountCreationListener#onEvent} reads the configuration on a JCR observation thread.
 * A single {@code volatile} reference to an immutable {@link Snapshot} eliminates torn reads:
 * the observation thread always sees either the old snapshot or the new one, never a mix of
 * fields from both.
 */
// S3077: volatile reference to an immutable Snapshot — single-write safe-publication; do not remove volatile
@SuppressWarnings("java:S3077")
@Component(
        immediate = true,
        service = {JcrAccountCreationNotificationConfig.class, ManagedService.class},
        property = Constants.SERVICE_PID + "=" + JcrAccountCreationNotificationConfig.PID
)
public class JcrAccountCreationNotificationConfig implements ManagedService {

    /** OSGi configuration PID — single source of truth used by this class and the mutation extension. */
    public static final String PID = "org.jahia.modules.jcraccountcreationnotification";

    /**
     * Shared email-validation pattern.
     * <ul>
     *   <li>Listener ({@link JcrAccountCreationListener}): required — empty/null skips notification.</li>
     *   <li>Mutation extension: optional — empty/null clears the override and falls back to MailService default.</li>
     * </ul>
     */
    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static final String DEFAULT_SUBJECT = "[{server}] JCR account creation notification";

    public static final String DEFAULT_BODY =
            "<p>Hi,</p>"
            + "<p>We're sending this email following the creation of a JCR user account.</p>"
            + "<p>Username: {username}<br>Created by: {creator}<br>Creation time: {time}</p>"
            + "<p>This email is meant to raise awareness about the security of your services"
            + " and to help you to protect them.</p>"
            + "<p>Regards,</p>";

    /** Default snapshot used at startup and when the configuration is deleted (dictionary == null). */
    private static final Snapshot DEFAULT_SNAPSHOT = new Snapshot(null, null, DEFAULT_SUBJECT, DEFAULT_BODY);

    /**
     * Immutable value snapshot of the four config fields.
     * Replacing this single reference is one volatile write; readers take one volatile read
     * and then access plain final fields — no torn state is possible.
     */
    static final class Snapshot {
        final String recipient;
        final String sender;
        final String subject;
        final String body;

        Snapshot(String recipient, String sender, String subject, String body) {
            this.recipient = recipient;
            this.sender = sender;
            this.subject = subject;
            this.body = body;
        }
    }

    private volatile Snapshot snapshot = DEFAULT_SNAPSHOT;

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary == null) {
            // Configuration deleted — reset to defaults so stale values are never served.
            snapshot = DEFAULT_SNAPSHOT;
            return;
        }
        final String recipient = (String) dictionary.get("recipient");
        final String sender = (String) dictionary.get("sender");
        final String configSubject = (String) dictionary.get("subject");
        final String subject = (configSubject != null && !configSubject.isEmpty()) ? configSubject : DEFAULT_SUBJECT;
        final String configBody = (String) dictionary.get("body");
        final String body = (configBody != null && !configBody.isEmpty()) ? configBody : DEFAULT_BODY;
        snapshot = new Snapshot(recipient, sender, subject, body);
    }

    public String getRecipient() {
        return snapshot.recipient;
    }

    public String getSender() {
        return snapshot.sender;
    }

    public String getSubject() {
        return snapshot.subject;
    }

    public String getBody() {
        return snapshot.body;
    }
}
