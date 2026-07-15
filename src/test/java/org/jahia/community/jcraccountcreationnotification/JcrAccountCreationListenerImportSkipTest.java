package org.jahia.community.jcraccountcreationnotification;

import org.jahia.services.content.JCREventIterator;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.mail.MailService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.Session;
import javax.jcr.observation.Event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D5 — bulk-import boundary: an event batch whose Jahia operation type is {@code IMPORT}
 * must be skipped entirely (no email, iterator not consumed), while a non-IMPORT batch
 * (e.g. {@code API}-driven creation) is processed and produces an email.
 *
 * <p>The skip is driven by {@link JCREventIterator#getOperationType()} — a thread-local
 * operation type set by Jahia's own import machinery, not derived from user-controllable
 * content, so it cannot be spoofed via a crafted username/path.
 *
 * <p>JUnit 4 + Mockito, matching the rest of the suite (the parent pins the surefire-junit4
 * provider — JUnit 5 tests would silently report "Tests run: 0").
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class JcrAccountCreationListenerImportSkipTest {

    @Mock
    private MailService mailService;

    @Mock
    private JcrAccountCreationNotificationConfig config;

    private JcrAccountCreationListener listener;

    @Before
    public void setUp() {
        listener = new JcrAccountCreationListener();
        listener.setMailService(mailService);
        listener.setConfig(config);
        // Past the not-registered guard so onEvent() reaches the IMPORT / mail-enabled checks.
        listener.setObservationSession(mock(Session.class));

        when(mailService.isEnabled()).thenReturn(true);
        when(mailService.defaultSender()).thenReturn("default-sender@example.com");
        when(mailService.defaultRecipient()).thenReturn("default-recipient@example.com");
        when(config.getSnapshot()).thenReturn(new JcrAccountCreationNotificationConfig.Snapshot(
                "admin@example.com",
                "noreply@example.com",
                "New user",
                "<p>Username: {username}</p>"));
    }

    @Test
    public void onEvent_importOperationType_skipsWithoutConsumingEventsOrSending() throws Exception {
        // Arrange — a JCREventIterator reporting IMPORT
        final JCREventIterator events = mock(JCREventIterator.class);
        when(events.getOperationType()).thenReturn(JCRObservationManager.IMPORT);

        // Act
        listener.onEvent(events);

        // Assert — the IMPORT batch is skipped before any event is pulled and no mail is sent
        verify(events, never()).nextEvent();
        verify(mailService, never()).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void onEvent_nonImportOperationType_processesEventAndSends() throws Exception {
        // Arrange — an API-driven (non-IMPORT) batch with a single jnt:user creation event
        final Event event = mock(Event.class);
        when(event.getPath()).thenReturn("/users/jdoe");
        when(event.getUserID()).thenReturn("admin");
        when(event.getDate()).thenReturn(System.currentTimeMillis());

        final JCREventIterator events = mock(JCREventIterator.class);
        when(events.getOperationType()).thenReturn(JCRObservationManager.API);
        when(events.hasNext()).thenReturn(true, false);
        when(events.nextEvent()).thenReturn(event);

        // Act
        listener.onEvent(events);

        // Assert — the interactive (non-IMPORT) creation triggers exactly one notification
        verify(events, times(1)).nextEvent();
        verify(mailService, times(1)).sendMessage(any(), any(), any(), any(), any(), any(), any());
    }
}
