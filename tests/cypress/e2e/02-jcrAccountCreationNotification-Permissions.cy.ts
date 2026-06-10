import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `jcrAccountCreationNotificationAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("jcrAccountCreationNotificationAdmin")` is enforced as
 *    `session.getNode("/").hasPermission("jcrAccountCreationNotificationAdmin")` (root-node ACL check).
 *  - Frontend: `requiredPermission: 'jcrAccountCreationNotificationAdmin'` in register.jsx gates the admin route.
 *  - RBAC content: the module ships the assignable `jcr-account-creation-notification-administrator` role
 *    (src/main/import/roles.xml) granting only `administrationAccess` + that permission.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('JCR Account Creation Notification — permission enforcement', () => {
    const ROLE_NAME = 'jcr-account-creation-notification-administrator';
    const DENIED_USER = 'jacnDeniedUser';
    const ALLOWED_USER = 'jacnAllowedUser';
    const PASSWORD = 'JacnPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/jcrAccountCreationNotification';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const querySettingsAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo({query: getSettings});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            querySettingsAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            querySettingsAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                const settings = (result as {data: {jcrAccountCreationNotificationSettings: {subject: string; body: string}}})
                    .data.jcrAccountCreationNotificationSettings;
                expect(settings, 'settings payload').to.have.property('subject');
                expect(settings, 'settings payload').to.have.property('body');
            });
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.contains('JCR Account Creation Notification Settings').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.contains('JCR Account Creation Notification Settings').should('be.visible');
        });
    });
});
