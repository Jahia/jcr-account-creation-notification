import {DocumentNode} from 'graphql';

describe('JCR Account Creation Notification', () => {
    const adminPath = '/jahia/administration/jcrAccountCreationNotification';
    const testUsername = 'test-jacn-user';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveSettings.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createUser: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createUser.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const deleteUser: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteUser.graphql');

    before(() => {
        cy.login();
    });

    // Restore neutral settings after the suite so other tests are not affected
    after(() => {
        cy.apollo({
            mutation: saveSettings,
            variables: {
                recipient: null,
                sender: null,
                subject: '[{server}] JCR account creation notification',
                body: '<p>Hi,</p><p>We\'re sending this email following the creation of a JCR user account.</p><p>Username: {username}<br>Created by: {creator}<br>Creation time: {time}</p><p>Regards,</p>'
            }
        });
    });

    // ─── Settings API ────────────────────────────────────────────────────────────

    describe('Settings API', () => {
        it('returns all settings fields via GraphQL', () => {
            cy.apollo({query: getSettings})
                .its('data.jcrAccountCreationNotificationSettings')
                .should(s => {
                    expect(s).to.have.property('recipient');
                    expect(s).to.have.property('sender');
                    expect(s).to.have.property('subject');
                    expect(s).to.have.property('body');
                });
        });

        it('saves settings and returns true', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    subject: '[{server}] Test notification',
                    body: '<p>Test body {username} {creator} {time}</p>'
                }
            })
                .its('data.jcrAccountCreationNotificationSaveSettings')
                .should('eq', true);
        });

        it('saves settings and reads them back consistently', () => {
            const testSubject = '[test] JCR account creation on {server}';
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: 'roundtrip@jahia.test',
                    subject: testSubject,
                    body: '<p>User: {username}</p>'
                }
            });
            cy.apollo({query: getSettings})
                .its('data.jcrAccountCreationNotificationSettings')
                .should(s => {
                    expect(s.recipient).to.eq('roundtrip@jahia.test');
                    expect(s.subject).to.eq(testSubject);
                });
        });

        it('clears optional recipient and sender when saved as null', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: null,
                    sender: null,
                    subject: '[{server}] Null-fields test',
                    body: '<p>Body</p>'
                }
            });
            cy.apollo({query: getSettings})
                .its('data.jcrAccountCreationNotificationSettings')
                .should(s => {
                    expect(s.recipient).to.be.null;
                    expect(s.sender).to.be.null;
                });
        });

        it('rejects an invalid recipient email address', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: 'not-a-valid-email',
                    subject: '[{server}] Test',
                    body: '<p>Body</p>'
                },
                errorPolicy: 'all'
            }).should(result => {
                const saved = result.data?.jcrAccountCreationNotificationSaveSettings;
                const hasErrors = result.errors && result.errors.length > 0;
                expect(saved === false || hasErrors).to.be.true;
            });
        });
    });

    // ─── Admin UI ────────────────────────────────────────────────────────────────

    describe('Admin UI', () => {
        it('shows the admin panel title', () => {
            cy.login();
            cy.visit(adminPath);
            cy.contains('JCR Account Creation Notification Settings').should('be.visible');
        });

        it('shows recipient and sender input fields', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#jacn-recipient').should('be.visible');
            cy.get('#jacn-sender').should('be.visible');
        });

        it('shows subject input field', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#jacn-subject').should('be.visible');
        });

        it('shows the CKEditor for the body field', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('.ck-editor').should('be.visible');
        });

        it('shows the save button', () => {
            cy.login();
            cy.visit(adminPath);
            cy.contains('button', 'Save settings').should('be.visible');
        });

        it('shows success alert after saving', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#jacn-subject').clear();
            cy.get('#jacn-subject').type('[{{}server}] UI test');
            cy.contains('button', 'Save settings').click();
            cy.get('[class*="jacn_alert--success"]').should('be.visible');
        });

        it('shows a validation error for an invalid recipient email', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#jacn-recipient').clear();
            cy.get('#jacn-recipient').type('not-an-email');
            cy.get('#jacn-recipient').blur();
            cy.get('[class*="jacn_errorMsg"]').should('be.visible');
            cy.contains('button', 'Save settings').should('be.disabled');
        });

        it('clears the validation error once the email is corrected', () => {
            cy.login();
            cy.visit(adminPath);
            cy.get('#jacn-recipient').clear();
            cy.get('#jacn-recipient').type('valid@example.com');
            cy.get('[class*="jacn_errorMsg"]').should('not.exist');
            cy.contains('button', 'Save settings').should('not.be.disabled');
            cy.get('#jacn-recipient').clear();
        });
    });

    // ─── Email notification ──────────────────────────────────────────────────────

    describe('Email notification', () => {
        const testRecipient = 'admin@jahia.test';
        const testSubjectTemplate = 'JCR account creation notification';
        const testBody = '<p>User: {username} created by {creator} at {time}</p>';

        before(() => {
            cy.login();

            cy.apollo({
                mutation: saveSettings,
                variables: {
                    recipient: testRecipient,
                    subject: testSubjectTemplate,
                    body: testBody
                }
            });

            cy.mailpitDeleteAllEmails();
        });

        after(() => {
            cy.apollo({
                mutation: deleteUser,
                variables: {path: `/users/${testUsername}`}
            });
        });

        it('sends a notification email when a JCR user is created', () => {
            cy.apollo({
                mutation: createUser,
                variables: {name: testUsername}
            });

            // Poll until the email arrives (up to 30 s)
            cy.mailpitHasEmailsByTo(testRecipient, 0, 50, {timeout: 30000});

            cy.mailpitGetMail().then(mail => {
                const toAddresses: string[] = Cypress._.map(mail.To, 'Address');
                expect(toAddresses).to.include(testRecipient);

                // Subject token {server} was replaced
                expect(mail.Subject).to.not.contain('{server}');

                // Static part of the subject is present
                expect(mail.Subject).to.contain('JCR account creation notification');
            });
        });

        it('email body has username, creator and time tokens replaced', () => {
            cy.mailpitGetMail().then(mail => {
                const body: string = mail.HTML || mail.Text || '';

                expect(body).to.not.contain('{username}');
                expect(body).to.not.contain('{creator}');
                expect(body).to.not.contain('{time}');

                // The created username is in the body
                expect(body).to.contain(testUsername);
            });
        });
    });
});
