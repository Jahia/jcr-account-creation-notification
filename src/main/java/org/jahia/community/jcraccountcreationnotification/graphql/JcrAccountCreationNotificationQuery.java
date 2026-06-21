package org.jahia.community.jcraccountcreationnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.community.jcraccountcreationnotification.JcrAccountCreationNotificationConfig;
import org.jahia.osgi.BundleUtils;

/**
 * GraphQL query namespace for JCR account-creation notification settings.
 *
 * <p>graphql-dxm instantiates GraphQL extension classes itself (not via CDI/OSGi DS),
 * so {@code @Reference} injection is not available here. {@link BundleUtils#getOsgiService}
 * is therefore used to look up {@link JcrAccountCreationNotificationConfig} at call time —
 * this is the established pattern for graphql-dxm extensions in this module.
 */
@GraphQLName("JcrAccountCreationNotificationQuery")
@GraphQLDescription("JCR account creation notification queries")
public class JcrAccountCreationNotificationQuery {

    @GraphQLField
    @GraphQLName("settings")
    @GraphQLDescription("Returns the current JCR account creation notification mail settings")
    @GraphQLRequiresPermission("jcrAccountCreationNotificationAdmin")
    public GqlSettings settings() {
        // BundleUtils.getOsgiService is used instead of @Reference because graphql-dxm
        // instantiates extension classes outside the OSGi DS container.
        final JcrAccountCreationNotificationConfig config =
                BundleUtils.getOsgiService(JcrAccountCreationNotificationConfig.class, null);
        if (config == null) {
            return GqlSettings.defaults();
        }
        return new GqlSettings(config.getRecipient(), config.getSender(), config.getSubject(), config.getBody());
    }

    @GraphQLName("JcrAccountCreationNotificationSettings")
    @GraphQLDescription("JCR account creation notification mail settings")
    public static class GqlSettings {

        private final String recipient;
        private final String sender;
        private final String subject;
        private final String body;

        public GqlSettings(String recipient, String sender, String subject, String body) {
            this.recipient = recipient;
            this.sender = sender;
            this.subject = subject;
            this.body = body;
        }

        public static GqlSettings defaults() {
            return new GqlSettings(null, null,
                    JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT,
                    JcrAccountCreationNotificationConfig.DEFAULT_BODY);
        }

        @GraphQLField
        @GraphQLName("recipient")
        @GraphQLDescription("Custom recipient email, null if using the MailService default")
        public String getRecipient() {
            return recipient;
        }

        @GraphQLField
        @GraphQLName("sender")
        @GraphQLDescription("Custom sender email, null if using the MailService default")
        public String getSender() {
            return sender;
        }

        @GraphQLField
        @GraphQLName("subject")
        @GraphQLDescription("Subject template. Token: {server}")
        public String getSubject() {
            return subject;
        }

        @GraphQLField
        @GraphQLName("body")
        @GraphQLDescription("Body template. Tokens: {username}, {creator}, {time}")
        public String getBody() {
            return body;
        }
    }
}
