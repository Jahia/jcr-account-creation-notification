# jcr-account-creation-notification

Sends an email notification whenever a JCR user account (`jnt:user`) is created under `/users`. Uses the mail service already configured in Jahia, with fully customisable subject and body templates. Bulk imports are excluded — only interactive user creations trigger the notification.

## Requirements

- Jahia 8.2.1.0 or later
- `graphql-dxm-provider` module
- `richtext-ckeditor5` module
- A mail server configured in Jahia (**Administration → Server settings → Configuration → Mail server**)

## Installation

- In Jahia, go to **Administration → Server settings → System components → Modules**
- Upload the JAR `jcr-account-creation-notification-X.X.X.jar`
- Check that the module is started

## Configuration

Go to **Administration → Server settings → Configuration → JCR Account Creation Notification**.

| Field | Description | Default |
|-------|-------------|---------|
| **Recipient** | Email address that receives the notification. Leave empty to use the Jahia mail service default. | *(Jahia default)* |
| **Sender** | From address used for the notification. Leave empty to use the Jahia mail service default. | *(Jahia default)* |
| **Subject** | Subject template. Supports `{server}` (hostname) token. | `[{server}] JCR account creation notification` |
| **Body** | HTML body template. Supports `{username}`, `{creator}`, `{time}` tokens. Editable with a rich-text editor. | *(HTML template)* |

Settings can also be managed via file or GraphQL — see the sections below.

### File-based configuration

Create or edit `org.jahia.modules.jcraccountcreationnotification.cfg` in the Jahia configuration directory:

```properties
# Optional — leave commented to use the Jahia mail service default
#recipient=security@example.com
#sender=noreply@example.com

subject=[{server}] JCR account creation notification
body=<p>Hi,</p><p>Username: {username}<br>Created by: {creator}<br>Creation time: {time}</p><p>Regards,</p>
```

## GraphQL API

All operations require the `admin` permission.

### Query

```graphql
query {
    jcrAccountCreationNotificationSettings {
        recipient   # String (null = Jahia default)
        sender      # String (null = Jahia default)
        subject
        body
    }
}
```

### Mutation

```graphql
mutation {
    jcrAccountCreationNotificationSaveSettings(
        recipient: "security@example.com"  # optional
        sender: "noreply@example.com"      # optional
        subject: "[{server}] New JCR account"
        body: "<p>User: {username} — created by {creator} at {time}</p>"
    )
}
```

Returns `true` on success, `false` on error. Passing `null` for `recipient` or `sender` resets them to the Jahia mail service default. Invalid email addresses are rejected.