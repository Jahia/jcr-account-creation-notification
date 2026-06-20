import {isValidEmail} from './emailValidation';

describe('isValidEmail', () => {
    it('accepts a standard email address', () => {
        // Arrange / Act / Assert
        expect(isValidEmail('user@example.com')).toBe(true);
    });

    it('accepts an address with a subdomain', () => {
        expect(isValidEmail('user@mail.example.co.uk')).toBe(true);
    });

    it('accepts plus-addressing in the local part', () => {
        expect(isValidEmail('user+tag@example.com')).toBe(true);
    });

    it('rejects a value with no @', () => {
        expect(isValidEmail('not-an-email')).toBe(false);
    });

    it('rejects a value with no domain', () => {
        expect(isValidEmail('user@')).toBe(false);
    });

    it('rejects a value with no top-level domain', () => {
        expect(isValidEmail('user@example')).toBe(false);
    });

    it('rejects a value with no local part', () => {
        expect(isValidEmail('@example.com')).toBe(false);
    });

    it('rejects a value containing whitespace', () => {
        expect(isValidEmail('user name@example.com')).toBe(false);
    });

    it('rejects an empty string', () => {
        expect(isValidEmail('')).toBe(false);
    });

    it('rejects a value with multiple @', () => {
        expect(isValidEmail('user@@example.com')).toBe(false);
    });
});
