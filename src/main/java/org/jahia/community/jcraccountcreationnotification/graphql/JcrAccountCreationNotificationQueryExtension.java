package org.jahia.community.jcraccountcreationnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("JCR account creation notification queries")
public class JcrAccountCreationNotificationQueryExtension {

    private JcrAccountCreationNotificationQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("jcrAccountCreationNotification")
    @GraphQLDescription("JCR account creation notification query namespace")
    public static JcrAccountCreationNotificationQuery jcrAccountCreationNotification() {
        return new JcrAccountCreationNotificationQuery();
    }
}
