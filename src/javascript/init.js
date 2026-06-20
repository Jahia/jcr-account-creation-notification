import {registry} from '@jahia/ui-extender';
import register from './JcrAccountCreationNotification/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'jcr-account-creation-notification', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('jcr-account-creation-notification');
            register();
        }
    });
}
