package org.jahia.community.jcraccountcreationnotification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.jahia.services.mail.MailService;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Behaviour tests for {@link JcrAccountCreationListener}.
 *
 * These tests exercise {@link JcrAccountCreationListener#handleUserCreation} and
 * {@link JcrAccountCreationListener#onEvent} using Mockito mocks for
 * {@link MailService}, {@link JcrAccountCreationNotificationConfig}, and {@link Event}.
 *
 * Existing static-helper tests (sanitizeHeader, escapeHtml, isValidEmail, sanitizeForLog)
 * are in {@link JcrAccountCreationListenerTest} and are kept as-is.
 *
 * JUnit 4 + MockitoJUnitRunner are used: the jahia-modules parent pins the surefire-junit4
 * provider (JUnit 5 tests would silently report "Tests run: 0").
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class JcrAccountCreationListenerBehaviourTest {

    @Mock
    private MailService mailService;

    @Mock
    private JcrAccountCreationNotificationConfig config;

    @Mock
    private Event event;

    private JcrAccountCreationListener listener;

    @Before
    public void setUp() throws Exception {
        listener = new JcrAccountCreationListener();
        listener.setMailService(mailService);
        listener.setConfig(config);
        // Inject a session so onEvent() proceeds past the not-registered guard and exercises the loop.
        listener.setObservationSession(mock(Session.class));

        // Default stub: mail is enabled
        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.defaultSender()).thenReturn("default-sender@example.com");
        when(mailService.defaultRecipient()).thenReturn("default-recipient@example.com");

        // Default config stubs — getSnapshot() is called by handleUserCreation for a single volatile read.
        // Individual getters are kept for the per-test overrides that re-stub getSnapshot() inline.
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "admin@example.com",
                "noreply@example.com",
                "[myserver] JCR account creation notification",
                "<p>Username: {username}, Creator: {creator}, Time: {time}</p>"));

        // Default event stubs
        when(event.getPath()).thenReturn("/users/jdoe");
        when(event.getUserID()).thenReturn("admin");
        when(event.getDate()).thenReturn(System.currentTimeMillis());
    }

    // -----------------------------------------------------------------------
    // Happy path — mail sent with correctly substituted and escaped variables
    // -----------------------------------------------------------------------

    @Test
    public void handleUserCreation_happyPath_sendsMailWithEscapedVariables() throws Exception {
        // Arrange — username and creator contain HTML-special chars to verify escaping
        when(event.getPath()).thenReturn("/users/<jdoe>");
        when(event.getUserID()).thenReturn("admin&operator");
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "admin@example.com",
                "noreply@example.com",
                "New user",
                "<p>{username} created by {creator} at {time}</p>"));

        // Act
        listener.handleUserCreation(event);

        // Assert
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMessage(
                eq("noreply@example.com"),
                eq("admin@example.com"),
                isNull(), isNull(),
                eq("New user"),
                isNull(),
                bodyCaptor.capture());

        final String sentBody = bodyCaptor.getValue();
        assertThat(sentBody)
                // username must be HTML-escaped
                .contains("&lt;jdoe&gt;")
                // creator must be HTML-escaped
                .contains("admin&amp;operator")
                // {time} token must be replaced (not literally present)
                .doesNotContain("{time}")
                .doesNotContain("{username}")
                .doesNotContain("{creator}");
    }

    @Test
    public void handleUserCreation_happyPath_usesConfiguredRecipientAndSender() throws Exception {
        // Act
        listener.handleUserCreation(event);

        // Assert
        verify(mailService).sendMessage(
                eq("noreply@example.com"),
                eq("admin@example.com"),
                isNull(), isNull(),
                anyString(), isNull(), anyString());
    }

    @Test
    public void handleUserCreation_noConfiguredSender_fallsBackToMailServiceDefault() throws Exception {
        // Arrange — sender not configured: rebuild snapshot with null sender
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "admin@example.com",
                null,
                "[myserver] JCR account creation notification",
                "<p>Username: {username}, Creator: {creator}, Time: {time}</p>"));

        // Act
        listener.handleUserCreation(event);

        // Assert — must fall back to MailService default sender
        verify(mailService).sendMessage(
                eq("default-sender@example.com"),
                anyString(), isNull(), isNull(), anyString(), isNull(), anyString());
    }

    // -----------------------------------------------------------------------
    // Invalid recipient — notification skipped
    // -----------------------------------------------------------------------

    @Test
    public void handleUserCreation_invalidRecipient_skipsNotification() throws Exception {
        // Arrange — invalid recipient in snapshot
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "not-an-email",
                "noreply@example.com",
                "[myserver] JCR account creation notification",
                "<p>Username: {username}, Creator: {creator}, Time: {time}</p>"));

        // Act
        listener.handleUserCreation(event);

        // Assert — sendMessage must NOT be called
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void handleUserCreation_nullRecipient_noConfiguredDefault_skipsNotification() throws Exception {
        // Arrange — both config and MailService return null/empty
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                null,
                "noreply@example.com",
                "[myserver] JCR account creation notification",
                "<p>Username: {username}, Creator: {creator}, Time: {time}</p>"));
        when(mailService.defaultRecipient()).thenReturn(null);

        // Act
        listener.handleUserCreation(event);

        // Assert
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Mail disabled — onEvent returns early without processing events
    // -----------------------------------------------------------------------

    @Test
    public void onEvent_mailDisabled_doesNotSendAnyMail() {
        // Arrange
        when(mailService.isEnabled()).thenReturn(false);
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true);

        // Act
        listener.onEvent(events);

        // Assert — no mail sent and event iterator not consumed
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
        verify(events, never()).nextEvent();
    }

    // -----------------------------------------------------------------------
    // Broad exception catch: an unexpected failure during handling must not escape the listener.
    // -----------------------------------------------------------------------

    @Test
    public void onEvent_runtimeExceptionInHandleUserCreation_doesNotPropagateToObservationManager() {
        // Arrange — config.getSnapshot() throws to simulate an unexpected RuntimeException
        when(config.getSnapshot()).thenThrow(new RuntimeException("simulated failure"));
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true, false);
        when(events.nextEvent()).thenReturn(event);

        // Act — must NOT throw; the observation manager must not receive the exception
        listener.onEvent(events);

        // Assert — no mail sent (exception was swallowed with log-and-continue)
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Non-activated listener (observationSession == null) — must return early
    // -----------------------------------------------------------------------

    @Test
    public void onEvent_nullObservationSession_returnsEarlyWithoutConsumingEvents() {
        // Arrange — listener constructed but never activated, so observationSession is null
        final JcrAccountCreationListener uninitialised = new JcrAccountCreationListener();
        uninitialised.setMailService(mailService);
        uninitialised.setConfig(config);
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true);

        // Act
        uninitialised.onEvent(events);

        // Assert — no event consumed and no mail sent
        verify(events, never()).nextEvent();
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // RepositoryException (dead session) — stops the whole batch, does not flood
    // -----------------------------------------------------------------------

    @Test
    public void onEvent_repositoryException_breaksBatchWithoutConsumingRemainingEvents() throws Exception {
        // Arrange — reading the first event fails with a RepositoryException (simulated dead session).
        // The iterator would otherwise yield two events; we assert the second is never consumed.
        when(event.getPath()).thenThrow(new RepositoryException("simulated dead session"));
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true, true, false);
        when(events.nextEvent()).thenReturn(event);

        // Act — must NOT throw; the exception is swallowed at WARN
        listener.onEvent(events);

        // Assert — only the first event was pulled before the loop broke; no mail sent
        verify(events, times(1)).nextEvent();
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }
}
