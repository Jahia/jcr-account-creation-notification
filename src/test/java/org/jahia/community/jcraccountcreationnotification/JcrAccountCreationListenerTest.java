package org.jahia.community.jcraccountcreationnotification;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the security-relevant helpers of {@link JcrAccountCreationListener}:
 * email-header CR/LF sanitization, recipient email validation, HTML escaping and
 * log sanitization. JUnit 4 is used deliberately because the jahia-modules parent
 * pins the surefire-junit4 provider (JUnit 5 tests would silently report "Tests run: 0").
 */
public class JcrAccountCreationListenerTest {

    // --- sanitizeHeader: CR/LF header-injection prevention ---

    @Test
    public void sanitizeHeader_stripsCarriageReturnAndLineFeed() {
        // Arrange
        String injected = "Subject line\r\nBcc: attacker@evil.example";

        // Act
        String result = JcrAccountCreationListener.sanitizeHeader(injected);

        // Assert
        assertThat(result)
                .doesNotContain("\r")
                .doesNotContain("\n")
                .isEqualTo("Subject line  Bcc: attacker@evil.example");
    }

    @Test
    public void sanitizeHeader_stripsLoneNewline() {
        assertThat(JcrAccountCreationListener.sanitizeHeader("a\nb")).isEqualTo("a b");
    }

    @Test
    public void sanitizeHeader_returnsNullForNull() {
        assertThat(JcrAccountCreationListener.sanitizeHeader(null)).isNull();
    }

    @Test
    public void sanitizeHeader_leavesCleanValueUnchanged() {
        assertThat(JcrAccountCreationListener.sanitizeHeader("Clean subject"))
                .isEqualTo("Clean subject");
    }

    // --- isValidEmail: recipient format validation ---

    @Test
    public void isValidEmail_acceptsWellFormedAddress() {
        assertThat(JcrAccountCreationListener.isValidEmail("admin@example.com")).isTrue();
    }

    @Test
    public void isValidEmail_rejectsNull() {
        assertThat(JcrAccountCreationListener.isValidEmail(null)).isFalse();
    }

    @Test
    public void isValidEmail_rejectsEmpty() {
        assertThat(JcrAccountCreationListener.isValidEmail("")).isFalse();
    }

    @Test
    public void isValidEmail_rejectsAddressWithoutDomainDot() {
        assertThat(JcrAccountCreationListener.isValidEmail("admin@localhost")).isFalse();
    }

    @Test
    public void isValidEmail_rejectsAddressWithEmbeddedNewline() {
        assertThat(JcrAccountCreationListener.isValidEmail("admin@example.com\r\nBcc: x@y.z")).isFalse();
    }

    @Test
    public void isValidEmail_rejectsAddressWithWhitespace() {
        assertThat(JcrAccountCreationListener.isValidEmail("ad min@example.com")).isFalse();
    }

    // --- escapeHtml: body context-value sanitization ---

    @Test
    public void escapeHtml_escapesAllReservedCharacters() {
        // Arrange
        String malicious = "<script>alert(\"x\")&'</script>";

        // Act
        String result = JcrAccountCreationListener.escapeHtml(malicious);

        // Assert
        assertThat(result)
                .isEqualTo("&lt;script&gt;alert(&quot;x&quot;)&amp;&#39;&lt;/script&gt;");
    }

    @Test
    public void escapeHtml_returnsEmptyStringForNull() {
        assertThat(JcrAccountCreationListener.escapeHtml(null)).isEmpty();
    }

    @Test
    public void escapeHtml_leavesPlainTextUnchanged() {
        assertThat(JcrAccountCreationListener.escapeHtml("jdoe")).isEqualTo("jdoe");
    }

    // --- sanitizeForLog: log-injection prevention ---

    @Test
    public void sanitizeForLog_replacesControlCharacters() {
        assertThat(JcrAccountCreationListener.sanitizeForLog("user\r\n\tfake-log-line"))
                .isEqualTo("user___fake-log-line");
    }

    @Test
    public void sanitizeForLog_returnsNullForNull() {
        assertThat(JcrAccountCreationListener.sanitizeForLog(null)).isNull();
    }
}
