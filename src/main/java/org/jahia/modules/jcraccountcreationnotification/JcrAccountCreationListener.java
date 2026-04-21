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

@Component(immediate = true, service = JcrAccountCreationListener.class)
public final class JcrAccountCreationListener implements EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(JcrAccountCreationListener.class);
    private static final String USERS_PATH = "/users";
    private static final String JNT_USER = "jnt:user";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy/MM/dd 'at' HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private Session observationSession;
    private MailService mailService;
    private JcrAccountCreationNotificationConfig config;

    @Reference
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Reference
    public void setConfig(JcrAccountCreationNotificationConfig config) {
        this.config = config;
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
                LOGGER.error("Error processing JCR user creation event", e);
            }
        }
    }

    private void handleUserCreation(Event event) throws RepositoryException {
        final String path = event.getPath();
        final String username = StringUtils.substringAfterLast(path, "/");
        final String creator = StringUtils.defaultString(event.getUserID(), "system");
        final String creationTime = DATE_FORMATTER.format(Instant.ofEpochMilli(event.getDate()));
        final String serverName = resolveServerName();

        final String sender = StringUtils.defaultIfEmpty(config.getSender(), mailService.defaultSender());
        final String recipient = StringUtils.defaultIfEmpty(config.getRecipient(), mailService.defaultRecipient());
        final String subject = config.getSubject().replace("{server}", serverName);
        final String body = config.getBody()
                .replace("{username}", username)
                .replace("{creator}", creator)
                .replace("{time}", creationTime);

        LOGGER.info("Sending JCR account creation notification for user '{}'", username);
        mailService.sendMessage(sender, recipient, null, null, subject, null, body);
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
