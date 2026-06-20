/* Jest setup: jest-dom matchers + global mocks for react-i18next and Apollo.
   Apollo's useQuery/useMutation are jest.fn() so individual tests can control
   loading / data / error and assert mutation calls. */
import '@testing-library/jest-dom';

// react-i18next: t() returns the key, i18n.language defaults to 'en'
jest.mock('react-i18next', () => ({
    useTranslation: () => ({
        t: key => key,
        i18n: {language: 'en'}
    })
}));

// @apollo/client: tests override useQuery/useMutation via mockReturnValue
jest.mock('@apollo/client', () => ({
    gql: (strings, ...values) => strings.reduce((acc, s, i) => acc + s + (values[i] ?? ''), ''),
    useQuery: jest.fn(() => ({loading: false, error: undefined, data: undefined})),
    useMutation: jest.fn(() => [jest.fn(), {loading: false}])
}));
