package org.jahia.community.jcraccountcreationnotification;

import org.jahia.services.mail.MailService;
import org.jahia.settings.SettingsBean;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.jcr.observation.Event;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F4 — {@code {server}} subject-token substitution and the {@code resolveServerName()}
 * fallback chain (SettingsBean → InetAddress → "unknown").
 *
 * <p>The substitution is exercised end-to-end through {@code handleUserCreation}: the resulting
 * subject passed to {@link MailService#sendMessage} is captured and asserted. The fallback tiers
 * are selected by intercepting the {@link SettingsBean} and {@link InetAddress} statics with
 * Mockito's inline static mock (enabled by {@code mockito-inline}) — no production seam is added,
 * and {@code resolveServerName()} stays private.
 *
 * <p>Per Stage-2 Q7, the resolved hostname is host-dependent, so tier (b) asserts only that the
 * {@code {server}} token was replaced, never an exact hostname.
 *
 * <p>JUnit 4, matching the rest of the suite (the parent pins the surefire-junit4 provider).
 */
public class JcrAccountCreationListenerServerNameTest {

    private MailService mailService;
    private JcrAccountCreationNotificationConfig config;
    private Event event;
    private JcrAccountCreationListener listener;

    @Before
    public void setUp() throws Exception {
        mailService = mock(MailService.class);
        config = mock(JcrAccountCreationNotificationConfig.class);
        event = mock(Event.class);

        listener = new JcrAccountCreationListener();
        listener.setMailService(mailService);
        listener.setConfig(config);

        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.defaultSender()).thenReturn("default-sender@example.com");
        when(mailService.defaultRecipient()).thenReturn("default-recipient@example.com");
        // Subject carries the {server} token; recipient valid so the send proceeds.
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "admin@example.com",
                "noreply@example.com",
                "[{server}] account created",
                "<p>body</p>"));
        when(event.getPath()).thenReturn("/users/jdoe");
        when(event.getUserID()).thenReturn("admin");
        when(event.getDate()).thenReturn(System.currentTimeMillis());
    }

    private String capturedSubject() {
        final ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(
                any(), any(), isNull(), isNull(), subjectCaptor.capture(), isNull(), anyString());
        return subjectCaptor.getValue();
    }

    // --- tier (a): SettingsBean.getServer() supplies the name verbatim ---

    @Test
    public void handleUserCreation_settingsBeanServer_usedForServerToken() throws Exception {
        final SettingsBean settings = mock(SettingsBean.class);
        when(settings.getServer()).thenReturn("cfg-server");

        try (MockedStatic<SettingsBean> sb = mockStatic(SettingsBean.class)) {
            sb.when(SettingsBean::getInstance).thenReturn(settings);

            listener.handleUserCreation(event);

            assertThat(capturedSubject())
                    .doesNotContain("{server}")
                    .contains("cfg-server")
                    .isEqualTo("[cfg-server] account created");
        }
    }

    // --- tier (b): no SettingsBean server → InetAddress hostname (value not asserted) ---

    @Test
    public void handleUserCreation_noSettingsBeanServer_fallsBackToLocalHostname() throws Exception {
        final SettingsBean settings = mock(SettingsBean.class);
        when(settings.getServer()).thenReturn(""); // empty → skip tier (a)

        try (MockedStatic<SettingsBean> sb = mockStatic(SettingsBean.class)) {
            sb.when(SettingsBean::getInstance).thenReturn(settings);

            listener.handleUserCreation(event);

            // Host-dependent value; only assert the token was substituted with something non-empty.
            assertThat(capturedSubject())
                    .doesNotContain("{server}")
                    .startsWith("[")
                    .doesNotContain("[]");
        }
    }

    // --- tier (c): no server + UnknownHostException → literal "unknown" ---

    @Test
    public void handleUserCreation_hostnameLookupFails_usesUnknown() throws Exception {
        try (MockedStatic<SettingsBean> sb = mockStatic(SettingsBean.class);
             MockedStatic<InetAddress> inet = mockStatic(InetAddress.class)) {
            sb.when(SettingsBean::getInstance).thenReturn(null); // no SettingsBean at all
            inet.when(InetAddress::getLocalHost).thenThrow(new UnknownHostException("no host"));

            listener.handleUserCreation(event);

            assertThat(capturedSubject())
                    .doesNotContain("{server}")
                    .isEqualTo("[unknown] account created");
        }
    }

    // --- substitution guard shared with F5: the {server} token never survives ---

    @Test
    public void handleUserCreation_serverToken_isAlwaysReplaced() throws Exception {
        final SettingsBean settings = mock(SettingsBean.class);
        when(settings.getServer()).thenReturn("host1");

        try (MockedStatic<SettingsBean> sb = mockStatic(SettingsBean.class)) {
            sb.when(SettingsBean::getInstance).thenReturn(settings);

            listener.handleUserCreation(event);

            verify(mailService).sendMessage(
                    eq("noreply@example.com"), eq("admin@example.com"),
                    isNull(), isNull(), eq("[host1] account created"), isNull(), anyString());
        }
    }
}
