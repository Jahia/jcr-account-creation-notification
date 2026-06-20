package org.jahia.modules.jcraccountcreationnotification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.jahia.services.mail.MailService;

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
@RunWith(MockitoJUnitRunner.class)
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

        // Default stub: mail is enabled
        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.defaultSender()).thenReturn("default-sender@example.com");
        when(mailService.defaultRecipient()).thenReturn("default-recipient@example.com");

        // Default config stubs
        when(config.getRecipient()).thenReturn("admin@example.com");
        when(config.getSender()).thenReturn("noreply@example.com");
        when(config.getSubject()).thenReturn("[myserver] JCR account creation notification");
        when(config.getBody()).thenReturn("<p>Username: {username}, Creator: {creator}, Time: {time}</p>");

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
        when(config.getBody()).thenReturn("<p>{username} created by {creator} at {time}</p>");
        when(config.getRecipient()).thenReturn("admin@example.com");
        when(config.getSender()).thenReturn("noreply@example.com");
        when(config.getSubject()).thenReturn("New user");

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
        // username must be HTML-escaped
        assertThat(sentBody).contains("&lt;jdoe&gt;");
        // creator must be HTML-escaped
        assertThat(sentBody).contains("admin&amp;operator");
        // {time} token must be replaced (not literally present)
        assertThat(sentBody).doesNotContain("{time}");
        assertThat(sentBody).doesNotContain("{username}");
        assertThat(sentBody).doesNotContain("{creator}");
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
        // Arrange — sender not configured
        when(config.getSender()).thenReturn(null);

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
        // Arrange
        when(config.getRecipient()).thenReturn("not-an-email");

        // Act
        listener.handleUserCreation(event);

        // Assert — sendMessage must NOT be called
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void handleUserCreation_nullRecipient_noConfiguredDefault_skipsNotification() throws Exception {
        // Arrange — both config and MailService return null/empty
        when(config.getRecipient()).thenReturn(null);
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
    public void onEvent_mailDisabled_doesNotSendAnyMail() throws Exception {
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
    // onEvent broad exception catch (H-4) — RuntimeException must not escape
    // -----------------------------------------------------------------------

    @Test
    public void onEvent_runtimeExceptionInHandleUserCreation_doesNotPropagateToObservationManager() throws Exception {
        // Arrange — config.getBody() throws to simulate an unexpected RuntimeException
        when(config.getBody()).thenThrow(new RuntimeException("simulated failure"));
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true, false);
        when(events.nextEvent()).thenReturn(event);

        // Act — must NOT throw; the observation manager must not receive the exception
        listener.onEvent(events);

        // Assert — no mail sent (exception was swallowed with log-and-continue)
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }
}
