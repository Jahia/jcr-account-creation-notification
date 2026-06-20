package org.jahia.modules.jcraccountcreationnotification;

import org.junit.Test;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JcrAccountCreationNotificationConfig}.
 *
 * Covers: initial defaults, binding a full dictionary, binding partial keys,
 * null-dictionary reset (config deleted), and the atomic snapshot guarantee.
 *
 * JUnit 4 is used deliberately: the jahia-modules parent pins the surefire-junit4
 * provider and JUnit 5 tests would silently report "Tests run: 0".
 */
public class JcrAccountCreationNotificationConfigTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static JcrAccountCreationNotificationConfig freshConfig() {
        return new JcrAccountCreationNotificationConfig();
    }

    private static Dictionary<String, Object> dict(String recipient, String sender,
                                                    String subject, String body) {
        final Hashtable<String, Object> d = new Hashtable<>();
        if (recipient != null) d.put("recipient", recipient);
        if (sender    != null) d.put("sender",    sender);
        if (subject   != null) d.put("subject",   subject);
        if (body      != null) d.put("body",       body);
        return d;
    }

    // -----------------------------------------------------------------------
    // Initial defaults (before updated() is ever called)
    // -----------------------------------------------------------------------

    @Test
    public void initial_state_returns_defaults() {
        // Arrange
        final JcrAccountCreationNotificationConfig config = freshConfig();

        // Assert
        assertThat(config.getRecipient()).isNull();
        assertThat(config.getSender()).isNull();
        assertThat(config.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
    }

    // -----------------------------------------------------------------------
    // Full dictionary binding
    // -----------------------------------------------------------------------

    @Test
    public void updated_fullDictionary_storesAllValues() throws Exception {
        // Arrange
        final JcrAccountCreationNotificationConfig config = freshConfig();

        // Act
        config.updated(dict("admin@example.com", "sender@example.com",
                "Custom subject", "Custom body"));

        // Assert
        assertThat(config.getRecipient()).isEqualTo("admin@example.com");
        assertThat(config.getSender()).isEqualTo("sender@example.com");
        assertThat(config.getSubject()).isEqualTo("Custom subject");
        assertThat(config.getBody()).isEqualTo("Custom body");
    }

    // -----------------------------------------------------------------------
    // Partial dictionary — subject/body absent → fall back to defaults
    // -----------------------------------------------------------------------

    @Test
    public void updated_partialDictionary_subjectAndBodyFallToDefaults() throws Exception {
        // Arrange
        final JcrAccountCreationNotificationConfig config = freshConfig();

        // Act — only recipient set, subject/body keys absent
        config.updated(dict("admin@example.com", null, null, null));

        // Assert
        assertThat(config.getRecipient()).isEqualTo("admin@example.com");
        assertThat(config.getSender()).isNull();
        assertThat(config.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
    }

    @Test
    public void updated_emptySubjectAndBody_fallToDefaults() throws Exception {
        // Arrange
        final JcrAccountCreationNotificationConfig config = freshConfig();

        // Act — empty strings for subject and body should also fall back to defaults
        config.updated(dict(null, null, "", ""));

        // Assert
        assertThat(config.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
    }

    // -----------------------------------------------------------------------
    // Null dictionary reset (M-1) — config deleted via ConfigurationAdmin
    // -----------------------------------------------------------------------

    @Test
    public void updated_nullDictionary_resetsToDefaults() throws Exception {
        // Arrange — first bind a custom config
        final JcrAccountCreationNotificationConfig config = freshConfig();
        config.updated(dict("admin@example.com", "sender@example.com",
                "Custom subject", "Custom body"));

        // Act — simulate config deletion
        config.updated(null);

        // Assert — all values must revert to defaults, not retain stale data
        assertThat(config.getRecipient()).isNull();
        assertThat(config.getSender()).isNull();
        assertThat(config.getSubject()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_SUBJECT);
        assertThat(config.getBody()).isEqualTo(JcrAccountCreationNotificationConfig.DEFAULT_BODY);
    }

    // -----------------------------------------------------------------------
    // Snapshot atomicity — getters must all reflect the same update
    // -----------------------------------------------------------------------

    @Test
    public void updated_snapshotIsAtomic_allGettersReflectSameUpdate() throws Exception {
        // Arrange
        final JcrAccountCreationNotificationConfig config = freshConfig();
        config.updated(dict("v1@example.com", "s1@example.com", "S1", "B1"));

        // Act — second update
        config.updated(dict("v2@example.com", "s2@example.com", "S2", "B2"));

        // Assert — snapshot is consistent: either fully old or fully new, never mixed.
        // Since no concurrency here, we expect the new values.
        assertThat(config.getRecipient()).isEqualTo("v2@example.com");
        assertThat(config.getSender()).isEqualTo("s2@example.com");
        assertThat(config.getSubject()).isEqualTo("S2");
        assertThat(config.getBody()).isEqualTo("B2");
    }

    // -----------------------------------------------------------------------
    // PID constant
    // -----------------------------------------------------------------------

    @Test
    public void pid_constant_hasExpectedValue() {
        assertThat(JcrAccountCreationNotificationConfig.PID)
                .isEqualTo("org.jahia.modules.jcraccountcreationnotification");
    }
}
