package org.jahia.modules.jcraccountcreationnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.modules.jcraccountcreationnotification.JcrAccountCreationNotificationConfig;
import org.jahia.osgi.BundleUtils;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("JcrAccountCreationNotificationQueries")
@GraphQLDescription("JCR Account Creation Notification queries")
public class JcrAccountCreationNotificationQueryExtension {

    private JcrAccountCreationNotificationQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("jcrAccountCreationNotificationSettings")
    @GraphQLDescription("Returns the current JCR account creation notification mail settings")
    @GraphQLRequiresPermission("admin")
    public static GqlSettings settings() {
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
