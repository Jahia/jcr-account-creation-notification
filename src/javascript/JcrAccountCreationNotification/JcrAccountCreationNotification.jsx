import React, {useEffect, useMemo, useRef, useState} from 'react';
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
import {isValidEmail} from './emailValidation';

/**
 * Builds the CKEditor 5 configuration for the email-body editor.
 *
 * IMPORTANT: this MUST NOT be recreated on every keystroke — passing a new
 * config object to <CKEditor> forces a full editor teardown/re-init and loses
 * the user's cursor. It is memoised in the component, keyed only on the UI
 * locale, so it is rebuilt only when the language actually changes.
 *
 * @param {string} language - UI locale (e.g. 'en', 'fr') driving editor language.
 * @param {string} toolbarLabel - Accessible label for the editor toolbar (SC 4.1.2).
 * @returns {object} A CKEditor 5 ClassicEditor configuration object.
 */
const buildEditorConfig = (language, toolbarLabel) => ({
    licenseKey: 'GPL',
    language,
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
        label: toolbarLabel,
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
});

/**
 * Administration panel for the JCR Account Creation Notification module.
 *
 * Renders a form (recipient, sender, subject, and a CKEditor 5 rich-text body)
 * that reads from / writes to the module's OSGi configuration through the
 * `jcrAccountCreationNotificationSettings` GraphQL query and
 * `jcrAccountCreationNotificationSaveSettings` mutation.
 *
 * Hydration is performed once via a `useEffect` watching the query `data`
 * (guarded by an `initialised` ref) so background cache refetches never clobber
 * in-flight user edits. Email fields are validated client-side; the backend
 * remains authoritative.
 *
 * @returns {JSX.Element} The rendered administration panel.
 */
export const JcrAccountCreationNotificationAdmin = () => {
    const {t, i18n} = useTranslation('jcr-account-creation-notification');
    const [saveStatus, setSaveStatus] = useState(null);
    const [errors, setErrors] = useState({recipient: '', sender: ''});
    const recipientInputRef = useRef(null);
    const senderInputRef = useRef(null);
    const formRef = useRef(null);
    const prevLoadingRef = useRef(true);
    // Guards one-time form hydration so background cache refetches don't clobber edits
    const initialisedRef = useRef(false);
    // Guards against double-submit (rapid clicks before `saving` re-renders)
    const submittingRef = useRef(false);

    useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

    const [formState, setFormState] = useState({
        recipient: '',
        sender: '',
        subject: '',
        body: ''
    });

    // Memoised so a new config object is NOT created per keystroke (which would
    // tear down and re-init the editor); rebuilt only when the UI locale changes.
    // `toolbarLabel` is hoisted so the dep array stays stable — `t` is referentially
    // unstable in react-i18next and would force an unnecessary editor remount.
    const toolbarLabel = t('label.bodyToolbarLabel');
    const editorConfig = useMemo(() => buildEditorConfig(i18n.language, toolbarLabel), [i18n.language, toolbarLabel]);

    const {loading, error, data} = useQuery(GET_SETTINGS, {
        fetchPolicy: 'network-only'
    });

    // Hydrate the form exactly once from the first successful response. Watching
    // `data` (rather than onCompleted) plus the `initialised` ref means later
    // background cache refetches never overwrite in-flight user edits.
    //
    // CONTRACT: the query uses fetchPolicy:'network-only' so a fresh fetch runs on
    // remount; that is sufficient. Do NOT add refetchQueries to the save mutation
    // without also resetting initialisedRef.current = false, or the form will silently
    // go stale after a save.
    useEffect(() => {
        if (initialisedRef.current) {
            return;
        }

        const s = data?.jcrAccountCreationNotificationSettings;
        if (s) {
            initialisedRef.current = true;
            setFormState({
                recipient: s.recipient ?? '',
                sender: s.sender ?? '',
                subject: s.subject ?? '',
                body: s.body ?? ''
            });
        }
    }, [data]);

    const [saveSettings, {loading: saving}] = useMutation(SAVE_SETTINGS);

    useEffect(() => {
        if (prevLoadingRef.current && !loading && formRef.current) {
            formRef.current.focus();
        }

        prevLoadingRef.current = loading;
    }, [loading]);

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
        // Double-submit guard: ignore re-entrant clicks while a save is in flight
        if (submittingRef.current) {
            return;
        }

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

        submittingRef.current = true;
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
        } catch {
            setSaveStatus('error');
        } finally {
            submittingRef.current = false;
        }
    };

    const hasErrors = Boolean(errors.recipient || errors.sender);

    if (loading) {
        return (
            <div className={styles.jacn_loading} role="status">
                <span className={styles.jacn_sr_only}>{t('label.loading')}</span>
                <Loader size="big"/>
            </div>
        );
    }

    if (error) {
        return (
            <div className={styles.jacn_container}>
                <div className={styles.jacn_header}>
                    <h2>{t('label.title')}</h2>
                </div>
                <div role="alert" className={`${styles.jacn_alert} ${styles['jacn_alert--error']}`}>
                    <span className={styles.jacn_alertIcon} aria-hidden="true">✕</span> {t('label.loadError')}
                </div>
            </div>
        );
    }

    return (
        <div className={styles.jacn_container}>
            {/* SC 4.1.3: two fixed-role live regions always in DOM — AT registers roles at mount */}
            <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className={styles.jacn_sr_only}
            >
                {saveStatus === 'success' ? t('label.saveSuccess') : ''}
            </div>
            <div
                role="alert"
                aria-live="assertive"
                aria-atomic="true"
                className={styles.jacn_sr_only}
            >
                {saveStatus === 'error' ? t('label.saveError') : ''}
            </div>

            <div className={styles.jacn_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.jacn_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            <div ref={formRef} tabIndex={-1} aria-label={t('label.title')} className={styles.jacn_form}>
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
                        aria-invalid={errors.recipient ? 'true' : undefined}
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
                        aria-invalid={errors.sender ? 'true' : undefined}
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
                    {/* Label and hint MUST precede the editor so the aria-labelledby/
                        aria-describedby targets exist in the DOM when onReady wires them. */}
                    <span id="jacn-body-label" className={styles.jacn_label}>
                        {t('label.body')}
                    </span>
                    <span id="jacn-body-hint" className={styles.jacn_fieldHint}>{t('label.bodyHint')}</span>
                    <div
                        className={`${styles.jacn_editor}${saving ? ` ${styles['jacn_editor--disabled']}` : ''}`}
                    >
                        <CKEditor
                            editor={ClassicEditor}
                            config={editorConfig}
                            disabled={saving}
                            data={formState.body}
                            onChange={handleBodyChange}
                            onReady={editor => {
                                const root = editor.editing.view.getDomRoot();
                                root.setAttribute('aria-labelledby', 'jacn-body-label');
                                root.setAttribute('aria-describedby', 'jacn-body-hint');
                                // SC 4.1.2: distinguish this toolbar from any other on the page
                                const toolbarEl = editor.ui.view.toolbar?.element;
                                if (toolbarEl) {
                                    toolbarEl.setAttribute('aria-label', t('label.bodyToolbarLabel'));
                                }
                            }}
                        />
                    </div>
                </div>
            </div>

            <div className={styles.jacn_actions}>
                {saveStatus === 'success' && (
                    <div aria-hidden="true" className={`${styles.jacn_alert} ${styles['jacn_alert--success']}`}>
                        <span className={styles.jacn_alertIcon} aria-hidden="true">✓</span> {t('label.saveSuccess')}
                    </div>
                )}
                {saveStatus === 'error' && (
                    <div aria-hidden="true" className={`${styles.jacn_alert} ${styles['jacn_alert--error']}`}>
                        <span className={styles.jacn_alertIcon} aria-hidden="true">✕</span> {t('label.saveError')}
                    </div>
                )}
                <Button
                    label={t('label.save')}
                    type="button"
                    variant="primary"
                    isDisabled={saving || hasErrors}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default JcrAccountCreationNotificationAdmin;
