import {gql} from '@apollo/client';

export const GET_SETTINGS = gql`
    query {
        jcrAccountCreationNotificationSettings {
            recipient
            sender
            subject
            body
        }
    }
`;

export const SAVE_SETTINGS = gql`
    mutation JcrAccountCreationNotificationSaveSettings($recipient: String, $sender: String, $subject: String, $body: String) {
        jcrAccountCreationNotificationSaveSettings(recipient: $recipient, sender: $sender, subject: $subject, body: $body)
    }
`;
