import React from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { javascript } from '@codemirror/lang-javascript';
import { json } from '@codemirror/lang-json';
import { EditorView } from '@codemirror/view';

interface ScriptEditorProps {
  typ: string;
  value: string;
  onChange: (value: string) => void;
}

function extensionsForType(typ: string) {
  switch (typ) {
    case 'js':
      return [javascript({ jsx: false }), EditorView.lineWrapping];
    case 'jq':
      return [json(), EditorView.lineWrapping];
    default:
      return [EditorView.lineWrapping];
  }
}

export function ScriptEditor({ typ, value, onChange }: ScriptEditorProps) {
  return (
    <CodeMirror
      value={value}
      onChange={onChange}
      extensions={extensionsForType(typ)}
      minHeight="192px"
      style={{ fontSize: '12px', border: '1px solid #d1d5db', borderRadius: '4px' }}
      basicSetup={{
        lineNumbers: true,
        foldGutter: false,
        dropCursor: false,
        allowMultipleSelections: false,
        indentOnInput: true,
        syntaxHighlighting: true,
        autocompletion: typ === 'js',
        closeBrackets: typ === 'js',
      }}
    />
  );
}
