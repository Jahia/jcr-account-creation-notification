package org.jahia.community.jcraccountcreationnotification;

import org.apache.jackrabbit.core.security.JahiaLoginModule;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.jahia.services.mail.MailService;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.ObservationManager;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F1 / F10 — listener registration parameters and activate/deactivate lifecycle.
 *
 * <p>{@code activate()} logs into the {@code default} workspace through the static
 * {@link JCRSessionFactory#getInstance()} and {@link JahiaLoginModule#getSystemCredentials()}.
 * Both are intercepted with Mockito's inline static mock (enabled by {@code mockito-inline})
 * so the registration can be exercised without a running JCR — no production seam is added.
 *
 * <p>JUnit 4, matching the rest of the suite (the parent pins the surefire-junit4 provider).
 */
public class JcrAccountCreationListenerLifecycleTest {

    private JcrAccountCreationListener newListener() {
        final JcrAccountCreationListener listener = new JcrAccountCreationListener();
        listener.setMailService(mock(MailService.class));
        listener.setConfig(mock(JcrAccountCreationNotificationConfig.class));
        return listener;
    }

    // --- F1: exact registration arguments are a regression guard ---

    @Test
    public void activate_registersListenerWithExactObservationArguments() throws Exception {
        final ObservationManager observationManager = mock(ObservationManager.class);
        final JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        when(workspace.getObservationManager()).thenReturn(observationManager);
        final JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getWorkspace()).thenReturn(workspace);

        final JCRSessionFactory sessionFactory = mock(JCRSessionFactory.class);
        when(sessionFactory.login(any(), eq("default"))).thenReturn(session);

        final JcrAccountCreationListener listener = newListener();

        try (MockedStatic<JCRSessionFactory> sf = mockStatic(JCRSessionFactory.class);
             MockedStatic<JahiaLoginModule> lm = mockStatic(JahiaLoginModule.class)) {
            sf.when(JCRSessionFactory::getInstance).thenReturn(sessionFactory);
            lm.when(JahiaLoginModule::getSystemCredentials).thenReturn(null);

            listener.activate();

            // Locks in: NODE_ADDED only, path "/users", deep, no UUID filter,
            // node-type filter {"jnt:user"}, noLocal=false.
            verify(observationManager).addEventListener(
                    same(listener),
                    eq(Event.NODE_ADDED),
                    eq("/users"),
                    eq(true),
                    isNull(),
                    aryEq(new String[]{"jnt:user"}),
                    eq(false));
        }
    }

    // --- F10(a): activate login failure leaves the listener unregistered and inert ---

    @Test
    public void activate_loginFailure_doesNotRegisterAndOnEventReturnsEarly() throws Exception {
        final JCRSessionFactory sessionFactory = mock(JCRSessionFactory.class);
        when(sessionFactory.login(any(), eq("default")))
                .thenThrow(new RepositoryException("login refused"));

        final JcrAccountCreationListener listener = newListener();

        try (MockedStatic<JCRSessionFactory> sf = mockStatic(JCRSessionFactory.class);
             MockedStatic<JahiaLoginModule> lm = mockStatic(JahiaLoginModule.class)) {
            sf.when(JCRSessionFactory::getInstance).thenReturn(sessionFactory);
            lm.when(JahiaLoginModule::getSystemCredentials).thenReturn(null);

            // activate() must swallow the RepositoryException (logged as an error).
            listener.activate();
        }

        // With no observation session, a subsequent event batch is ignored without consumption.
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true);
        listener.onEvent(events);
        verify(events, never()).nextEvent();
    }

    // --- F10(b): deactivate removes the listener, logs out, and clears the session ---

    @Test
    public void deactivate_removesListenerLogsOutAndClearsSession() throws Exception {
        final ObservationManager observationManager = mock(ObservationManager.class);
        final JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        when(workspace.getObservationManager()).thenReturn(observationManager);
        final JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getWorkspace()).thenReturn(workspace);

        final JcrAccountCreationListener listener = newListener();
        listener.setObservationSession(session); // simulate a successful prior activation

        listener.deactivate();

        verify(observationManager).removeEventListener(listener);
        verify(session).logout();

        // Session cleared: a subsequent event batch is ignored without consumption.
        final EventIterator events = mock(EventIterator.class);
        when(events.hasNext()).thenReturn(true);
        listener.onEvent(events);
        verify(events, never()).nextEvent();
    }
}
