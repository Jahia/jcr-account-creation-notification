package org.jahia.community.jcraccountcreationnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.community.jcraccountcreationnotification.JcrAccountCreationNotificationConfig;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * GraphQL mutation namespace for JCR account-creation notification settings.
 *
 * <p>graphql-dxm instantiates GraphQL extension classes itself (not via CDI/OSGi DS),
 * so {@code @Reference} injection is not available here. {@link BundleUtils#getOsgiService}
 * is therefore used to look up {@link ConfigurationAdmin} at call time — this is the
 * established pattern for graphql-dxm extensions in this module.
 */
@GraphQLName("JcrAccountCreationNotificationMutation")
@GraphQLDescription("JCR account creation notification mutations")
public class JcrAccountCreationNotificationMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(JcrAccountCreationNotificationMutation.class);

    /**
     * Validates an optional email field: {@code null} or empty is accepted (clears the override);
     * a non-empty value must match the shared pattern.
     *
     * @see JcrAccountCreationNotificationConfig#EMAIL_PATTERN
     */
    private static boolean isValidEmail(String email) {
        return email == null || email.isEmpty()
                || JcrAccountCreationNotificationConfig.EMAIL_PATTERN.matcher(email).matches();
    }

    @GraphQLField
    @GraphQLName("saveSettings")
    @GraphQLDescription("Saves the JCR account creation notification mail settings")
    @GraphQLRequiresPermission("jcrAccountCreationNotificationAdmin")
    public Boolean saveSettings(
            @GraphQLName("recipient") @GraphQLDescription("Custom recipient email (optional, leave empty to use MailService default)") String recipient,
            @GraphQLName("sender") @GraphQLDescription("Custom sender email (optional, leave empty to use MailService default)") String sender,
            @GraphQLName("subject") @GraphQLDescription("Subject template — token: {server}. Send empty to remove the override and restore the module default subject.") String subject,
            @GraphQLName("body") @GraphQLDescription("Body template — tokens: {username}, {creator}, {time}. Send empty to remove the override and restore the module default body.") String body) {
        if (!isValidEmail(recipient)) {
            throw new IllegalArgumentException("Invalid recipient email address");
        }
        if (!isValidEmail(sender)) {
            throw new IllegalArgumentException("Invalid sender email address");
        }
        // BundleUtils.getOsgiService is used instead of @Reference because graphql-dxm
        // instantiates extension classes outside the OSGi DS container.
        final ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
        if (configAdmin == null) {
            return Boolean.FALSE;
        }
        try {
            final Configuration config = configAdmin.getConfiguration(
                    JcrAccountCreationNotificationConfig.PID, null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            // recipient and sender: remove key when empty so MailService default is used.
            putOrRemove(props, "recipient", recipient);
            putOrRemove(props, "sender", sender);
            // subject and body: remove key when empty so the component default is restored.
            putOrRemove(props, "subject", subject);
            putOrRemove(props, "body", body);
            config.update(props);
            return Boolean.TRUE;
        } catch (IOException e) {
            LOGGER.error("Failed to save JCR account creation notification settings", e);
            return Boolean.FALSE;
        }
    }
    /**
     * Puts {@code key} into {@code props} when {@code value} is non-empty;
     * removes it otherwise, so the OSGi CM consumer sees the absence as "use default".
     */
    private static void putOrRemove(Dictionary<String, Object> props, String key, String value) {
        if (value != null && !value.isEmpty()) {
            props.put(key, value);
        } else {
            props.remove(key);
        }
    }

}
