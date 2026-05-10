# jcr-account-creation-notification

Jahia OSGi module that sends an email notification whenever a new JCR user account (`jnt:user`) is created under `/users`. Admin UI at `/jahia/administration/jcrAccountCreationNotification`.

## Key Facts

- **artifactId**: `jcr-account-creation-notification` | **version**: `2.0.1-SNAPSHOT`
- **Java package**: `org.jahia.modules.jcraccountcreationnotification`
- **jahia-depends**: `default,graphql-dxm-provider,richtext-ckeditor5`
- **OSGi config PID**: `org.jahia.modules.jcraccountcreationnotification`

## Architecture

| Class | Role |
|-------|------|
| `JcrAccountCreationListener` | `EventListener`; registers on JCR `NODE_ADDED` at `/users` (node type `jnt:user`) via `JCRObservationManager` on `@Activate`; deregisters on `@Deactivate` |
| `JcrAccountCreationNotificationConfig` | `ManagedService` + config value holder; provides `recipient`, `sender`, `subject`, `body` |
| `JcrAccountCreationNotificationQueryExtension` | GraphQL query |
| `JcrAccountCreationNotificationMutationExtension` | GraphQL mutation |

The listener observes the `default` workspace. It skips notifications when `MailService` is disabled.

### Email Template Variables

| Variable | Replaced with |
|---|---|
| `{username}` | The new user's name |
| `{creator}` | Username of the session that triggered the creation |
| `{time}` | Formatted timestamp (`yyyy/MM/dd 'at' HH:mm:ss z`) |

Default subject: `[{server}] JCR account creation notification`

## GraphQL API

| Operation | Name | Notes |
|-----------|------|-------|
| Query | `jcrAccountCreationNotificationSettings` → `{recipient, sender, subject, body}` | Returns defaults when config not yet written |
| Mutation | `jcrAccountCreationNotificationSaveSettings(recipient, sender, subject, body)` → Boolean | Writes to OSGi config file |

Both require `admin` permission.

## Build

```bash
mvn clean install
yarn build
yarn lint
```

- Admin route target: `administration-server-configuration:21`
- CSS prefix: `jacn_`
- CKEditor5 provided by the `richtext-ckeditor5` Module Federation remote (not bundled locally)

## Tests (Cypress Docker)

```bash
cd tests
cp .env.example .env          # fill JAHIA_IMAGE, JAHIA_LICENSE, SMTP config
yarn install
./ci.build.sh && ./ci.startup.sh
```

- Tests: `tests/cypress/e2e/01-jcrAccountCreationNotification.cy.ts`
- Includes a **mailpit** container for capturing sent emails
- Tests cover: GraphQL API (read/save settings), admin UI, email delivery (creates a user via JCR mutation, asserts email arrives in mailpit)
- `assets/provisioning.yml` installs dependencies and may configure SMTP pointing to mailpit

## Gotchas

- The listener only fires for `NODE_ADDED` events on `jnt:user` nodes directly under `/users` — accounts created under `/sites/{siteKey}/users` fire a separate observation path and are **not** caught by this listener
- On `@Activate`, if login to the `default` workspace fails, the listener is silently not registered — check logs for `Failed to register JCR account creation listener`
- The `observationSession` is held open for the module lifetime; it is closed in `@Deactivate` — do not close it elsewhere
- `{server}` in the subject template is the hostname from `InetAddress.getLocalHost().getHostName()` — may return a container ID in Docker
- CSS Modules: match in Cypress with `[class*="jacn_..."]`
