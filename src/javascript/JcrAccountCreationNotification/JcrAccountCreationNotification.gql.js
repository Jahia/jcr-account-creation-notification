import {gql} from '@apollo/client';

export const GET_SETTINGS = gql`
    query {
        jcrAccountCreationNotification {
            settings {
                recipient
                sender
                subject
                body
            }
        }
    }
`;

export const SAVE_SETTINGS = gql`
    mutation JcrAccountCreationNotificationSaveSettings($recipient: String, $sender: String, $subject: String, $body: String) {
        jcrAccountCreationNotification {
            saveSettings(recipient: $recipient, sender: $sender, subject: $subject, body: $body)
        }
    }
`;
