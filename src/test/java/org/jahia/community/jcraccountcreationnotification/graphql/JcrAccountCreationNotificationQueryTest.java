package org.jahia.community.jcraccountcreationnotification.graphql;

import org.jahia.community.jcraccountcreationnotification.JcrAccountCreationNotificationConfig;
import org.jahia.community.jcraccountcreationnotification.graphql.JcrAccountCreationNotificationQuery.GqlSettings;
import org.jahia.osgi.BundleUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * F8 — unit coverage for the GraphQL {@code settings} query.
 *
 * <p>graphql-dxm instantiates the extension class with a no-arg constructor and it looks
 * {@link JcrAccountCreationNotificationConfig} up via the static
 * {@link BundleUtils#getOsgiService}. There is no DI seam, so the OSGi lookup is intercepted
 * with Mockito's inline static mock (enabled by the {@code mockito-inline} test dependency).
 *
 * <p>JUnit 4, matching the rest of the suite (the parent pins the surefire-junit4 provider).
 */
public class JcrAccountCreationNotificationQueryTest {

    @Test
    public void settings_configUnavailable_returnsModuleDefaults() {
        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            // Arrange — no config service published yet
            bundleUtils.when(() -> BundleUtils.getOsgiService(
                    JcrAccountCreationNotificationConfig.class, null)).thenReturn(null);

            // Act
            final GqlSettings settings = new JcrAccountCreationNotificationQuery().settings();

            // Assert — falls back to GqlSettings.defaults()
            assertThat(settings.getRecipient()).isNull();
            assertThat(settings.getSender()).isNull();
            assertThat(settings.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
            assertThat(settings.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
        }
    }

    @Test
    public void settings_configPresent_mirrorsConfigSnapshot() {
        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            // Arrange — a config with all four fields set
            final JcrAccountCreationNotificationConfig config = mock(JcrAccountCreationNotificationConfig.class);
            when(config.getRecipient()).thenReturn("security@example.com");
            when(config.getSender()).thenReturn("alerts@example.com");
            when(config.getSubject()).thenReturn("Custom subject");
            when(config.getBody()).thenReturn("<p>Custom body</p>");
            bundleUtils.when(() -> BundleUtils.getOsgiService(
                    JcrAccountCreationNotificationConfig.class, null)).thenReturn(config);

            // Act
            final GqlSettings settings = new JcrAccountCreationNotificationQuery().settings();

            // Assert — the query mirrors the config getters verbatim
            assertThat(settings.getRecipient()).isEqualTo("security@example.com");
            assertThat(settings.getSender()).isEqualTo("alerts@example.com");
            assertThat(settings.getSubject()).isEqualTo("Custom subject");
            assertThat(settings.getBody()).isEqualTo("<p>Custom body</p>");
        }
    }

    @Test
    public void defaults_factory_returnsNullRecipientAndSenderWithDefaultTemplates() {
        // Sanity guard on the fallback value object used above (no OSGi involved).
        final GqlSettings defaults = GqlSettings.defaults();
        assertThat(defaults.getRecipient()).isNull();
        assertThat(defaults.getSender()).isNull();
        assertThat(defaults.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
        assertThat(defaults.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
    }
}
