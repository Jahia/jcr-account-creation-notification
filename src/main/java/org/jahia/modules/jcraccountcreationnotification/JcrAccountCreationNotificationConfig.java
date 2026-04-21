package org.jahia.modules.jcraccountcreationnotification;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.annotations.Component;

import java.util.Dictionary;

@Component(
        immediate = true,
        service = {JcrAccountCreationNotificationConfig.class, ManagedService.class},
        property = Constants.SERVICE_PID + "=org.jahia.modules.jcraccountcreationnotification"
)
public class JcrAccountCreationNotificationConfig implements ManagedService {

    public static final String DEFAULT_SUBJECT = "[{server}] JCR account creation notification";

    public static final String DEFAULT_BODY =
            "<p>Hi,</p>"
            + "<p>We're sending this email following the creation of a JCR user account.</p>"
            + "<p>Username: {username}<br>Created by: {creator}<br>Creation time: {time}</p>"
            + "<p>This email is meant to raise awareness about the security of your services"
            + " and to help you to protect them.</p>"
            + "<p>Regards,</p>";

    private String recipient = null;
    private String sender = null;
    private String subject = DEFAULT_SUBJECT;
    private String body = DEFAULT_BODY;

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary == null) {
            return;
        }
        recipient = (String) dictionary.get("recipient");
        sender = (String) dictionary.get("sender");
        final String configSubject = (String) dictionary.get("subject");
        subject = (configSubject != null && !configSubject.isEmpty()) ? configSubject : DEFAULT_SUBJECT;
        final String configBody = (String) dictionary.get("body");
        body = (configBody != null && !configBody.isEmpty()) ? configBody : DEFAULT_BODY;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
