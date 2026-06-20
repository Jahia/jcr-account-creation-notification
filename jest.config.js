/**
 * Jest configuration for the React admin UI unit tests.
 *
 * Isolated from the webpack/eslint babel setup: a dedicated babel-jest transform
 * (with configFile/babelrc disabled) compiles JSX so we never pick up the module's
 * runtime babel presets. SCSS modules resolve through identity-obj-proxy, and the
 * heavy editor / Jahia / i18n / Apollo dependencies are stubbed via manual mocks.
 */
module.exports = {
    testEnvironment: 'jsdom',
    rootDir: __dirname,
    roots: ['<rootDir>/src/javascript'],
    testMatch: ['**/__tests__/**/*.test.{js,jsx}', '**/*.test.{js,jsx}'],
    setupFilesAfterEnv: ['<rootDir>/src/javascript/__mocks__/setupTests.js'],
    moduleNameMapper: {
        // SCSS modules -> proxy that returns the class name as-is
        '\\.scss$': 'identity-obj-proxy',
        // Redirect bare 'ckeditor5' and the React wrapper to lightweight stubs
        '^ckeditor5$': '<rootDir>/src/javascript/__mocks__/ckeditor5.js',
        '^@ckeditor/ckeditor5-react$': '<rootDir>/src/javascript/__mocks__/ckeditor5-react.js',
        '^@jahia/moonstone$': '<rootDir>/src/javascript/__mocks__/moonstone.js'
    },
    transform: {
        '^.+\\.(js|jsx)$': ['babel-jest', {
            configFile: false,
            babelrc: false,
            presets: [
                ['@babel/preset-env', {targets: {node: 'current'}}],
                ['@babel/preset-react', {runtime: 'classic'}]
            ]
        }]
    },
    clearMocks: true
};
