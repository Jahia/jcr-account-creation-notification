import React, {useEffect, useRef, useState} from 'react';
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
    const recipientInputRef = useRef(null);
    const senderInputRef = useRef(null);
    const saveLiveRef = useRef(null);

    useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

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
        if (recipientError) {
            recipientInputRef.current?.focus();
            return;
        }

        if (senderError) {
            senderInputRef.current?.focus();
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

        setTimeout(() => saveLiveRef.current?.focus(), 50);
    };

    const hasErrors = Boolean(errors.recipient || errors.sender);

    const saveLiveMsg = saveStatus === 'success' ? t('label.saveSuccess') :
        saveStatus === 'error' ? t('label.saveError') : '';

    if (loading) {
        return (
            <div className={styles.jacn_loading} role="status">
                <span className={styles.jacn_sr_only}>{t('label.loading')}</span>
                <Loader size="big"/>
            </div>
        );
    }

    return (
        <div className={styles.jacn_container}>
            {/* Persistent live region — always in DOM so AT registers it before status changes */}
            <div
                ref={saveLiveRef}
                tabIndex={-1}
                role={saveStatus === 'error' ? 'alert' : 'status'}
                aria-live={saveStatus === 'error' ? 'assertive' : 'polite'}
                aria-atomic="true"
                className={styles.jacn_sr_only}
            >
                {saveLiveMsg}
            </div>

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
                    <span id="jacn-recipient-hint" className={styles.jacn_sr_only}>
                        {t('label.recipientPlaceholder')}
                    </span>
                    <input
                        ref={recipientInputRef}
                        type="email"
                        id="jacn-recipient"
                        className={`${styles.jacn_input}${errors.recipient ? ` ${styles['jacn_input--error']}` : ''}`}
                        value={formState.recipient}
                        placeholder={t('label.recipientPlaceholder')}
                        autoComplete="email"
                        aria-invalid={Boolean(errors.recipient)}
                        aria-describedby={['jacn-recipient-hint', errors.recipient ? 'jacn-recipient-error' : ''].filter(Boolean).join(' ')}
                        onChange={handleChange('recipient')}
                        onBlur={handleBlur('recipient')}
                    />
                    {errors.recipient && (
                        <span id="jacn-recipient-error" className={styles.jacn_errorMsg}>{errors.recipient}</span>
                    )}
                </div>

                <div className={styles.jacn_fieldGroup}>
                    <label className={styles.jacn_label} htmlFor="jacn-sender">
                        {t('label.sender')}
                    </label>
                    <span id="jacn-sender-hint" className={styles.jacn_sr_only}>
                        {t('label.senderPlaceholder')}
                    </span>
                    <input
                        ref={senderInputRef}
                        type="email"
                        id="jacn-sender"
                        className={`${styles.jacn_input}${errors.sender ? ` ${styles['jacn_input--error']}` : ''}`}
                        value={formState.sender}
                        placeholder={t('label.senderPlaceholder')}
                        autoComplete="email"
                        aria-invalid={Boolean(errors.sender)}
                        aria-describedby={['jacn-sender-hint', errors.sender ? 'jacn-sender-error' : ''].filter(Boolean).join(' ')}
                        onChange={handleChange('sender')}
                        onBlur={handleBlur('sender')}
                    />
                    {errors.sender && (
                        <span id="jacn-sender-error" className={styles.jacn_errorMsg}>{errors.sender}</span>
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
                        aria-describedby="jacn-subject-hint"
                        onChange={handleChange('subject')}
                    />
                    <span id="jacn-subject-hint" className={styles.jacn_fieldHint}>{t('label.subjectHint')}</span>
                </div>

                <div className={styles.jacn_fieldGroup}>
                    {/* aria-hidden — CKEditor renders a contenteditable, not a native input;
                        the editor wrapper carries aria-labelledby instead */}
                    <span id="jacn-body-label" className={styles.jacn_label} aria-hidden="false">
                        {t('label.body')}
                    </span>
                    <div
                        className={`${styles.jacn_editor}${saving ? ` ${styles['jacn_editor--disabled']}` : ''}`}
                        aria-labelledby="jacn-body-label"
                        aria-describedby="jacn-body-hint"
                    >
                        <CKEditor
                            editor={ClassicEditor}
                            config={editorConfig}
                            disabled={saving}
                            data={formState.body}
                            onChange={handleBodyChange}
                        />
                    </div>
                    <span id="jacn-body-hint" className={styles.jacn_fieldHint}>{t('label.bodyHint')}</span>
                </div>
            </div>

            <div className={styles.jacn_actions}>
                {saveStatus === 'success' && (
                    <div aria-hidden="true" className={`${styles.jacn_alert} ${styles['jacn_alert--success']}`}>
                        <span className={styles.jacn_alertIcon}>✓</span> {t('label.saveSuccess')}
                    </div>
                )}
                {saveStatus === 'error' && (
                    <div aria-hidden="true" className={`${styles.jacn_alert} ${styles['jacn_alert--error']}`}>
                        <span className={styles.jacn_alertIcon}>✕</span> {t('label.saveError')}
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
