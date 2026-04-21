import {registry} from '@jahia/ui-extender';
import {JcrAccountCreationNotificationAdmin} from './JcrAccountCreationNotification';
import React from 'react';

export default () => {
    console.debug('%c jcr-account-creation-notification: activation in progress', 'color: #006633');
    registry.add('adminRoute', 'jcrAccountCreationNotification', {
        targets: ['administration-server-configuration:21'],
        requiredPermission: 'admin',
        label: 'jcr-account-creation-notification:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(JcrAccountCreationNotificationAdmin)
    });
};
