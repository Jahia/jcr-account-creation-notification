/**
 * Returns true when the given value is a syntactically valid email address.
 *
 * Intentionally a pragmatic regex (local-part@domain.tld) rather than a full
 * RFC 5322 implementation — it matches the client-side guard used by the
 * admin form, where the backend remains the authoritative validator.
 *
 * @param {string} val - The candidate email address.
 * @returns {boolean} True when the value looks like a valid email address.
 */
export const isValidEmail = val => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);
