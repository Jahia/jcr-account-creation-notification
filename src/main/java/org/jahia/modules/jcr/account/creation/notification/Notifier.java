package org.jahia.modules.jcr.account.creation.notification;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.jcr.RepositoryException;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(Notifier.class);
    private static final Notifier INSTANCE = new Notifier();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss z");

    public static Notifier getInstance() {
        return INSTANCE;
    }

    public void notifyAboutNewJcrUser(AddedNodeFact addedNodeFact) {
        try {
            final MailService mailService = MailService.getInstance();
            final String userCreator = addedNodeFact.getNode().getPropertyAsString("j:lastPublishedBy");
            final String userCreated = addedNodeFact.getPath();
            LOGGER.info(String.format("JCR account %s created by %s", userCreated, userCreator));
            if (mailService.isEnabled()) {
                final Date creationDate = new Date();
                final String sender = mailService.defaultSender();
                final String recipient = mailService.defaultRecipient();
                final String subject = "JCR account creation notification";
                final String body = "Hi,\n"
                        + "\n"
                        + "We're sending this email following the creation of a JCR user.\n"
                        + "\n"
                        + "    User creator      : %s\n"
                        + "    Creation time     : %s\n"
                        + "    User created      : %s\n"
                        + "\n"
                        + "\n"
                        + "This email is meant to raise awareness about the security of your services \n"
                        + "and to help you to protect them.\n"
                        + "\n"
                        + "Regards,";

                mailService.sendMessage(sender, recipient, null, null, subject,
                        String.format(body, userCreator, dateFormat.format(creationDate), userCreated));
            }
        } catch (RepositoryException ex) {
            LOGGER.error("Impossible to get path of the created JCR user", ex);
        }
    }
}
