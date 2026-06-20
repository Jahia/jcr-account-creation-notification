/* Stub of @ckeditor/ckeditor5-react. Renders a textarea and exposes onReady
   with a minimal editor shape so the component's accessibility wiring runs. */
import React, {useEffect, useRef} from 'react';

export const CKEditor = ({data, onChange, onReady, disabled}) => {
    const ref = useRef(null);

    useEffect(() => {
        if (onReady) {
            const editor = {
                editing: {view: {getDomRoot: () => ref.current}},
                ui: {view: {toolbar: {element: document.createElement('div')}}},
                getData: () => (ref.current ? ref.current.value : '')
            };
            onReady(editor);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <textarea
            ref={ref}
            aria-label="email body"
            disabled={disabled}
            defaultValue={data}
            onChange={e => onChange && onChange(e, {getData: () => e.target.value})}
        />
    );
};
