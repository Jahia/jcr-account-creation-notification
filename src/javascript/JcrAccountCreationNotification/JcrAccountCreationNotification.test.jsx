import React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {useMutation, useQuery} from '@apollo/client';
import {JcrAccountCreationNotificationAdmin} from './JcrAccountCreationNotification';

const SETTINGS = {
    jcrAccountCreationNotificationSettings: {
        recipient: 'admin@example.com',
        sender: 'noreply@example.com',
        subject: '[{server}] Notice',
        body: '<p>Hello</p>'
    }
};

const mockQuery = ({loading = false, error, data} = {}) =>
    useQuery.mockReturnValue({loading, error, data});

describe('JcrAccountCreationNotificationAdmin', () => {
    beforeEach(() => {
        useQuery.mockReset();
        useMutation.mockReset();
        useMutation.mockReturnValue([jest.fn(), {loading: false}]);
    });

    it('shows a loader while the settings query is loading', () => {
        mockQuery({loading: true});
        render(<JcrAccountCreationNotificationAdmin/>);
        expect(screen.getByTestId('loader')).toBeInTheDocument();
    });

    it('renders an accessible error state when the query fails', () => {
        mockQuery({error: new Error('boom')});
        render(<JcrAccountCreationNotificationAdmin/>);
        const alert = screen.getByRole('alert');
        expect(alert).toHaveTextContent('label.loadError');
    });

    it('hydrates the form from the query data', () => {
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        expect(screen.getByLabelText('label.recipient')).toHaveValue('admin@example.com');
        expect(screen.getByLabelText('label.sender')).toHaveValue('noreply@example.com');
    });

    it('renders the body hint before the editor in DOM order', () => {
        mockQuery({data: SETTINGS});
        const {container} = render(<JcrAccountCreationNotificationAdmin/>);
        const hint = container.querySelector('#jacn-body-hint');
        const editor = container.querySelector('textarea');

        const hintBeforeEditor = hint.compareDocumentPosition(editor) & Node.DOCUMENT_POSITION_FOLLOWING;
        expect(hintBeforeEditor).toBeTruthy();
    });

    it('shows a validation error for an invalid recipient on blur', () => {
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        const recipient = screen.getByLabelText('label.recipient');
        fireEvent.change(recipient, {target: {value: 'not-an-email'}});
        fireEvent.blur(recipient);
        expect(screen.getByText('label.invalidEmail')).toBeInTheDocument();
        expect(recipient).toHaveAttribute('aria-invalid', 'true');
    });

    it('clears the validation error once the email becomes valid', () => {
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        const recipient = screen.getByLabelText('label.recipient');
        fireEvent.change(recipient, {target: {value: 'bad'}});
        fireEvent.blur(recipient);
        expect(screen.getByText('label.invalidEmail')).toBeInTheDocument();
        fireEvent.change(recipient, {target: {value: 'good@example.com'}});
        expect(screen.queryByText('label.invalidEmail')).not.toBeInTheDocument();
    });

    it('does not call the mutation when the recipient is invalid', () => {
        const save = jest.fn();
        useMutation.mockReturnValue([save, {loading: false}]);
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        fireEvent.change(screen.getByLabelText('label.recipient'), {target: {value: 'bad'}});
        fireEvent.blur(screen.getByLabelText('label.recipient'));
        fireEvent.click(screen.getByText('label.save'));
        expect(save).not.toHaveBeenCalled();
    });

    it('calls the mutation with the form values on save', async () => {
        const save = jest.fn().mockResolvedValue({data: {jcrAccountCreationNotificationSaveSettings: true}});
        useMutation.mockReturnValue([save, {loading: false}]);
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        fireEvent.click(screen.getByText('label.save'));
        await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
        expect(save).toHaveBeenCalledWith({
            variables: {
                recipient: 'admin@example.com',
                sender: 'noreply@example.com',
                subject: '[{server}] Notice',
                body: '<p>Hello</p>'
            }
        });
    });

    it('guards against double-submit', async () => {
        let resolve;
        const save = jest.fn(() => new Promise(r => {
            resolve = r;
        }));
        useMutation.mockReturnValue([save, {loading: false}]);
        mockQuery({data: SETTINGS});
        render(<JcrAccountCreationNotificationAdmin/>);
        const button = screen.getByText('label.save');
        fireEvent.click(button);
        fireEvent.click(button);
        expect(save).toHaveBeenCalledTimes(1);
        resolve({data: {jcrAccountCreationNotificationSaveSettings: true}});
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('label.saveSuccess'));
    });
});
