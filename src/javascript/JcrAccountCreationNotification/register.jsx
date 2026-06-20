import {registry} from '@jahia/ui-extender';
import {JcrAccountCreationNotificationAdmin} from './JcrAccountCreationNotification';
import React from 'react';

export default () => {
    registry.add('adminRoute', 'jcrAccountCreationNotification', {
        targets: ['administration-server-configuration:21'],
        requiredPermission: 'jcrAccountCreationNotificationAdmin',
        label: 'jcr-account-creation-notification:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(JcrAccountCreationNotificationAdmin)
    });
};
