/* Lightweight stub of @jahia/moonstone for unit tests. */
import React from 'react';

export const Button = ({label, onClick, isDisabled, type = 'button'}) => (
    <button type={type} disabled={isDisabled} onClick={onClick}>{label}</button>
);

export const Loader = () => <div data-testid="loader"/>;

export const Typography = ({children}) => <div>{children}</div>;
