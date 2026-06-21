package org.jahia.modules.jcraccountcreationnotification.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("JCR account creation notification mutations")
public class JcrAccountCreationNotificationMutationExtension {

    private JcrAccountCreationNotificationMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("jcrAccountCreationNotification")
    @GraphQLDescription("JCR account creation notification mutation namespace")
    public static JcrAccountCreationNotificationMutation jcrAccountCreationNotification() {
        return new JcrAccountCreationNotificationMutation();
    }
}
