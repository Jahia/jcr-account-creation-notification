package org.jahia.community.jcraccountcreationnotification.graphql;

import org.jahia.community.jcraccountcreationnotification.JcrAccountCreationNotificationConfig;
import org.jahia.osgi.BundleUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F9 / D4 (case 1) — unit coverage for the GraphQL {@code saveSettings} mutation.
 *
 * <p>graphql-dxm instantiates the extension class with a no-arg constructor and it looks
 * {@link ConfigurationAdmin} up via the static {@link BundleUtils#getOsgiService}. There is no
 * DI seam, so the OSGi lookup is intercepted with Mockito's inline static mock (enabled by the
 * {@code mockito-inline} test dependency).
 *
 * <p>JUnit 4, matching the rest of the suite (the parent pins the surefire-junit4 provider).
 */
public class JcrAccountCreationNotificationMutationTest {

    private static ConfigurationAdmin stubConfigAdmin(Configuration config) throws IOException {
        final ConfigurationAdmin configAdmin = mock(ConfigurationAdmin.class);
        when(configAdmin.getConfiguration(JcrAccountCreationNotificationConfig.PID, null)).thenReturn(config);
        return configAdmin;
    }

    // --- F9(a): valid values are persisted at the correct PID and TRUE is returned ---

    @Test
    public void saveSettings_validValues_updatesConfigurationAtPidAndReturnsTrue() throws Exception {
        final Configuration config = mock(Configuration.class);
        when(config.getProperties()).thenReturn(null); // no properties yet → fresh Hashtable
        final ConfigurationAdmin configAdmin = stubConfigAdmin(config);

        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(configAdmin);

            final Boolean result = new JcrAccountCreationNotificationMutation()
                    .saveSettings("security@example.com", "alerts@example.com", "Custom subject", "<p>Body</p>");

            assertThat(result).isTrue();
            // The configuration is fetched for the consumer PID (single source of truth).
            verify(configAdmin).getConfiguration(eq(JcrAccountCreationNotificationConfig.PID), isNull());

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Dictionary<String, Object>> props =
                    ArgumentCaptor.forClass(Dictionary.class);
            verify(config).update(props.capture());
            final Dictionary<String, Object> saved = props.getValue();
            assertThat(saved.get("recipient")).isEqualTo("security@example.com");
            assertThat(saved.get("sender")).isEqualTo("alerts@example.com");
            assertThat(saved.get("subject")).isEqualTo("Custom subject");
            assertThat(saved.get("body")).isEqualTo("<p>Body</p>");
        }
    }

    // --- F9(b): empty values remove the key (putOrRemove) so defaults are restored ---

    @Test
    public void saveSettings_emptyValues_removeExistingKeys() throws Exception {
        // Pre-existing properties that must be cleared by empty inputs.
        final Hashtable<String, Object> existing = new Hashtable<>();
        existing.put("recipient", "old@example.com");
        existing.put("sender", "old-sender@example.com");
        existing.put("subject", "Old subject");
        existing.put("body", "<p>Old</p>");

        final Configuration config = mock(Configuration.class);
        when(config.getProperties()).thenReturn(existing);
        final ConfigurationAdmin configAdmin = stubConfigAdmin(config);

        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(configAdmin);

            final Boolean result = new JcrAccountCreationNotificationMutation()
                    .saveSettings("", "", "", "");

            assertThat(result).isTrue();
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Dictionary<String, Object>> props =
                    ArgumentCaptor.forClass(Dictionary.class);
            verify(config).update(props.capture());
            final Dictionary<String, Object> saved = props.getValue();
            assertThat(saved.get("recipient")).isNull();
            assertThat(saved.get("sender")).isNull();
            assertThat(saved.get("subject")).isNull();
            assertThat(saved.get("body")).isNull();
        }
    }

    // --- F9(c): no ConfigurationAdmin available → FALSE, no throw ---

    @Test
    public void saveSettings_noConfigurationAdmin_returnsFalse() {
        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(null);

            final Boolean result = new JcrAccountCreationNotificationMutation()
                    .saveSettings("r@example.com", "s@example.com", "subj", "body");

            assertThat(result).isFalse();
        }
    }

    // --- F9(d): IOException while persisting → FALSE, no throw ---

    @Test
    public void saveSettings_ioExceptionOnPersist_returnsFalse() throws Exception {
        final ConfigurationAdmin configAdmin = mock(ConfigurationAdmin.class);
        when(configAdmin.getConfiguration(anyString(), isNull()))
                .thenThrow(new IOException("cm store unavailable"));

        try (MockedStatic<BundleUtils> bundleUtils = mockStatic(BundleUtils.class)) {
            bundleUtils.when(() -> BundleUtils.getOsgiService(ConfigurationAdmin.class, null))
                    .thenReturn(configAdmin);

            final Boolean result = new JcrAccountCreationNotificationMutation()
                    .saveSettings("r@example.com", "s@example.com", "subj", "body");

            assertThat(result).isFalse();
        }
    }

    // --- D4(case 1): GraphQL-layer validation of recipient and sender ---

    @Test
    public void saveSettings_invalidRecipient_throwsIllegalArgument() {
        assertThatThrownBy(() -> new JcrAccountCreationNotificationMutation()
                .saveSettings("nope", null, "subj", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    public void saveSettings_invalidSender_throwsIllegalArgument() {
        assertThatThrownBy(() -> new JcrAccountCreationNotificationMutation()
                .saveSettings(null, "bad-sender", "subj", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sender");
    }
}
