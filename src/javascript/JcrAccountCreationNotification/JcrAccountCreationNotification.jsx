import React, {useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import {CKEditor} from '@ckeditor/ckeditor5-react';
import {
    Autoformat,
    Bold,
    ClassicEditor,
    Essentials,
    Italic,
    Link,
    List,
    ListProperties,
    Paragraph,
    RemoveFormat,
    SourceEditing,
    Strikethrough,
    TextTransformation,
    Underline
} from 'ckeditor5';
import styles from './JcrAccountCreationNotification.scss';
import {GET_SETTINGS, SAVE_SETTINGS} from './JcrAccountCreationNotification.gql';

const isValidEmail = val => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);

const editorConfig = {
    licenseKey: 'GPL',
    plugins: [
        Autoformat,
        Bold,
        Essentials,
        Italic,
        Link,
        List,
        ListProperties,
        Paragraph,
        RemoveFormat,
        SourceEditing,
        Strikethrough,
        TextTransformation,
        Underline
    ],
    toolbar: {
        items: [
            'undo',
            'redo',
            '|',
            'bold',
            'italic',
            'underline',
            'strikethrough',
            'removeFormat',
            '|',
            'link',
            '|',
            'bulletedList',
            'numberedList',
            '|',
            'sourceEditing'
        ]
    },
    menuBar: {isVisible: false},
    list: {
        properties: {
            styles: false,
            startIndex: false,
            reversed: false
        }
    },
    link: {
        defaultProtocol: 'https://'
    }
};

export const JcrAccountCreationNotificationAdmin = () => {
    const {t} = useTranslation('jcr-account-creation-notification');
    const [saveStatus, setSaveStatus] = useState(null);
    const [errors, setErrors] = useState({recipient: '', sender: ''});

    const [formState, setFormState] = useState({
        recipient: '',
        sender: '',
        subject: '',
        body: ''
    });

    const {loading} = useQuery(GET_SETTINGS, {
        fetchPolicy: 'network-only',
        onCompleted: data => {
            const s = data?.jcrAccountCreationNotificationSettings;
            if (s) {
                setFormState({
                    recipient: s.recipient ?? '',
                    sender: s.sender ?? '',
                    subject: s.subject ?? '',
                    body: s.body ?? ''
                });
            }
        }
    });

    const [saveSettings, {loading: saving}] = useMutation(SAVE_SETTINGS);

    const validateEmailField = value => (value && !isValidEmail(value)) ? t('label.invalidEmail') : '';

    const handleChange = field => e => {
        setSaveStatus(null);
        const value = e.target.value;
        setFormState(prev => ({...prev, [field]: value}));
        if (errors[field] !== undefined) {
            setErrors(prev => ({...prev, [field]: validateEmailField(value)}));
        }
    };

    const handleBlur = field => () => {
        setErrors(prev => ({...prev, [field]: validateEmailField(formState[field])}));
    };

    const handleBodyChange = (event, editor) => {
        setSaveStatus(null);
        setFormState(prev => ({...prev, body: editor.getData()}));
    };

    const handleSave = async () => {
        const recipientError = validateEmailField(formState.recipient);
        const senderError = validateEmailField(formState.sender);
        setErrors({recipient: recipientError, sender: senderError});
        if (recipientError || senderError) {
            return;
        }

        setSaveStatus(null);
        try {
            const result = await saveSettings({
                variables: {
                    recipient: formState.recipient || null,
                    sender: formState.sender || null,
                    subject: formState.subject,
                    body: formState.body
                }
            });
            setSaveStatus(result.data?.jcrAccountCreationNotificationSaveSettings ? 'success' : 'error');
        } catch (err) {
            console.error('Failed to save settings:', err);
            setSaveStatus('error');
        }
    };

    const hasErrors = Boolean(errors.recipient || errors.sender);

    if (loading) {
        return (
            <div className={styles.jacn_loading}>
                <Loader size="big"/>
            </div>
        );
    }

    return (
        <div className={styles.jacn_container}>
            <div className={styles.jacn_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.jacn_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            <div className={styles.jacn_form}>
                <div className={styles.jacn_fieldGroup}>
                    <label className={styles.jacn_label} htmlFor="jacn-recipient">
                        {t('label.recipient')}
                    </label>
                    <input
                        type="text"
                        id="jacn-recipient"
                        className={`${styles.jacn_input}${errors.recipient ? ` ${styles['jacn_input--error']}` : ''}`}
                        value={formState.recipient}
                        placeholder={t('label.recipientPlaceholder')}
                        onChange={handleChange('recipient')}
                        onBlur={handleBlur('recipient')}
                    />
                    {errors.recipient && (
                        <span className={styles.jacn_errorMsg}>{errors.recipient}</span>
                    )}
                </div>

                <div className={styles.jacn_fieldGroup}>
                    <label className={styles.jacn_label} htmlFor="jacn-sender">
                        {t('label.sender')}
                    </label>
                    <input
                        type="text"
                        id="jacn-sender"
                        className={`${styles.jacn_input}${errors.sender ? ` ${styles['jacn_input--error']}` : ''}`}
                        value={formState.sender}
                        placeholder={t('label.senderPlaceholder')}
                        onChange={handleChange('sender')}
                        onBlur={handleBlur('sender')}
                    />
                    {errors.sender && (
                        <span className={styles.jacn_errorMsg}>{errors.sender}</span>
                    )}
                </div>

                <div className={styles.jacn_fieldGroup}>
                    <label className={styles.jacn_label} htmlFor="jacn-subject">
                        {t('label.subject')}
                    </label>
                    <input
                        type="text"
                        id="jacn-subject"
                        className={styles.jacn_input}
                        value={formState.subject}
                        onChange={handleChange('subject')}
                    />
                    <span className={styles.jacn_tokenHint}>{t('label.subjectHint')}</span>
                </div>

                <div className={styles.jacn_fieldGroup}>
                    <label className={styles.jacn_label}>
                        {t('label.body')}
                    </label>
                    <div className={`${styles.jacn_editor}${saving ? ` ${styles['jacn_editor--disabled']}` : ''}`}>
                        <CKEditor
                            editor={ClassicEditor}
                            config={editorConfig}
                            disabled={saving}
                            data={formState.body}
                            onChange={handleBodyChange}
                        />
                    </div>
                    <span className={styles.jacn_tokenHint}>{t('label.bodyHint')}</span>
                </div>
            </div>

            <div className={styles.jacn_actions}>
                {saveStatus === 'success' && (
                    <div className={`${styles.jacn_alert} ${styles['jacn_alert--success']}`}>
                        {t('label.saveSuccess')}
                    </div>
                )}
                {saveStatus === 'error' && (
                    <div className={`${styles.jacn_alert} ${styles['jacn_alert--error']}`}>
                        {t('label.saveError')}
                    </div>
                )}
                <Button
                    label={t('label.save')}
                    variant="primary"
                    isDisabled={saving || hasErrors}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default JcrAccountCreationNotificationAdmin;
