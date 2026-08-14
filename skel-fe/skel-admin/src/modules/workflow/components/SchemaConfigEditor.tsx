import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import CodeMirror from '@uiw/react-codemirror';
import { json as jsonLang } from '@codemirror/lang-json';
import { javascript } from '@codemirror/lang-javascript';
import { EditorView } from '@codemirror/view';
import { IconPlus, IconMinus } from '../../../components/Icons';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

export type JsonSchema = Record<string, unknown>;
export type UiSchema = Record<string, unknown>;

type TabId = 'config' | 'schema' | 'raw';

export interface SchemaConfigEditorProps {
  /** JSON Schema driving the config form. */
  schema?: JsonSchema;
  /** RJSF-style UI hints (`ui:order`, per-property `ui:widget` / `ui:options`). */
  uiSchema?: UiSchema;
  /** Config instance being edited. */
  value?: unknown;
  onChange?: (value: unknown) => void;
  /** When set, the Schema tab is editable and reports the parsed schema. */
  onSchemaChange?: (schema: JsonSchema) => void;
  readOnly?: boolean;
  className?: string;
  /** Default editor height (px). The panel is user-resizable. */
  height?: number;
}

function isObj(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

function omitRef(s: JsonSchema): JsonSchema {
  const { $ref: _r, ...rest } = s;
  return rest;
}

/** Resolve `#/$defs/X` or `#/definitions/X` against the root schema. */
function resolveRef(node: JsonSchema, root: JsonSchema): JsonSchema {
  const ref = node.$ref;
  if (typeof ref !== 'string' || !ref.startsWith('#/')) return node;
  const path = ref.slice(2).split('/').filter(Boolean);
  let cur: unknown = root;
  for (const key of path) {
    if (!isObj(cur) || !(key in cur)) return node;
    cur = cur[key];
  }
  if (!isObj(cur)) return node;
  return { ...cur, ...omitRef(node) };
}

function schemaType(node: JsonSchema): string {
  const t = node.type;
  if (typeof t === 'string') return t;
  if (Array.isArray(t)) {
    const n = t.find((x) => x !== 'null');
    return typeof n === 'string' ? n : 'string';
  }
  if (node.properties || node.$defs || node.definitions) return 'object';
  if (node.items) return 'array';
  if (node.enum) return 'string';
  if (node.oneOf) return 'oneOf';
  if (node.$ref) return 'ref';
  return 'string';
}

function defaultFor(node0: JsonSchema, root: JsonSchema): unknown {
  const node = resolveRef(node0, root);
  if (node.default !== undefined) return node.default;
  if (node.const !== undefined) return node.const;
  if (Array.isArray(node.enum) && node.enum.length > 0) return node.enum[0];
  const oneOf = node.oneOf;
  if (Array.isArray(oneOf) && oneOf[0] && isObj(oneOf[0]) && 'const' in oneOf[0]) return (oneOf[0] as JsonSchema).const;
  switch (schemaType(node)) {
    case 'object': {
      const props = isObj(node.properties) ? node.properties : {};
      const out: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(props)) {
        if (isObj(v)) out[k] = defaultFor(v, root);
      }
      return out;
    }
    case 'array': {
      const min = typeof node.minItems === 'number' ? node.minItems : 0;
      const items = isObj(node.items) ? node.items : {};
      if (min <= 0) return [];
      return Array.from({ length: min }, () => defaultFor(items, root));
    }
    case 'integer':
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'null':
      return null;
    default:
      return '';
  }
}

/** `ui:order` keys first (that exist), then remaining schema property order. */
function orderedKeys(properties: Record<string, unknown>, ui?: UiSchema): string[] {
  const keys = Object.keys(properties);
  const order = Array.isArray(ui?.['ui:order']) ? (ui!['ui:order'] as unknown[]).map(String) : [];
  const first = order.filter((k) => keys.includes(k));
  const rest = keys.filter((k) => !first.includes(k));
  return [...first, ...rest];
}

function propUi(ui: UiSchema | undefined, key: string): UiSchema | undefined {
  const v = ui?.[key];
  return isObj(v) ? v : undefined;
}

function widgetOf(ui?: UiSchema): string | undefined {
  const w = ui?.['ui:widget'];
  return typeof w === 'string' ? w : undefined;
}

function uiOptions(ui?: UiSchema): Record<string, unknown> {
  return isObj(ui?.['ui:options']) ? (ui!['ui:options'] as Record<string, unknown>) : {};
}

function fieldHeight(ui?: UiSchema, fallback = 120): number {
  const opts = uiOptions(ui);
  if (typeof opts.height === 'number') return opts.height;
  if (typeof opts.height === 'string') {
    const n = parseInt(opts.height, 10);
    if (!Number.isNaN(n)) return n;
  }
  if (typeof opts.rows === 'number') return opts.rows * 18;
  if (typeof ui?.['ui:height'] === 'number') return ui['ui:height'] as number;
  return fallback;
}

function titleOf(key: string, schema: JsonSchema, ui?: UiSchema): string {
  if (typeof ui?.['ui:title'] === 'string') return ui['ui:title'] as string;
  if (typeof schema.title === 'string' && schema.title) return schema.title;
  return key;
}

function oneOfOptions(node: JsonSchema): { value: unknown; label: string }[] | null {
  const oneOf = node.oneOf;
  if (!Array.isArray(oneOf) || oneOf.length === 0) return null;
  const opts: { value: unknown; label: string }[] = [];
  for (const item of oneOf) {
    if (!isObj(item) || !('const' in item)) return null;
    const label = typeof item.title === 'string' ? item.title : String(item.const);
    opts.push({ value: item.const, label });
  }
  return opts;
}

function pretty(v: unknown): string {
  try { return JSON.stringify(v ?? {}, null, 2); } catch { return '{}'; }
}

function CodeField({ value, onChange, lang, height, readOnly }: {
  value: string; onChange?: (v: string) => void; lang: 'json' | 'javascript' | 'text'; height: number; readOnly?: boolean;
}) {
  const ext = lang === 'json' ? jsonLang() : lang === 'javascript' ? javascript({ jsx: false }) : EditorView.lineWrapping;
  return (
    <div className="flex-1 min-w-0 border border-border rounded overflow-hidden bg-card" style={{ minHeight: height }}>
      <CodeMirror
        value={value}
        onChange={readOnly ? undefined : onChange}
        extensions={[ext, EditorView.lineWrapping, ...(readOnly ? [EditorView.editable.of(false)] : [])]}
        height={`${height}px`}
        style={{ fontSize: '12px' }}
        basicSetup={{
          lineNumbers: true, foldGutter: lang === 'json', dropCursor: false,
          indentOnInput: !readOnly, syntaxHighlighting: true,
          autocompletion: false, closeBrackets: lang !== 'text',
        }}
      />
    </div>
  );
}

/** Bordered, resizable syntax-highlighted JSON editor (Start dialog Input, not SchemaConfigEditor). */
export function JsonCodeEditor({ value, onChange, height = 160, readOnly }: {
  value: string; onChange?: (v: string) => void; height?: number; readOnly?: boolean;
}) {
  return (
    <div className="schema-config-editor" style={{ minHeight: height }}>
      <CodeMirror
        value={value}
        onChange={readOnly ? undefined : onChange}
        extensions={[jsonLang(), EditorView.lineWrapping, ...(readOnly ? [EditorView.editable.of(false)] : [])]}
        height={`${height}px`}
        style={{ fontSize: '12px' }}
        basicSetup={{
          lineNumbers: true, foldGutter: true, dropCursor: false,
          indentOnInput: !readOnly, syntaxHighlighting: true,
          autocompletion: false, closeBrackets: true,
        }}
      />
    </div>
  );
}

function ScalarEditor({ schema, ui, value, onChange, readOnly }: {
  schema: JsonSchema; ui?: UiSchema; value: unknown; onChange: (v: unknown) => void; readOnly?: boolean;
}) {
  const widget = widgetOf(ui);
  const t = schemaType(schema);
  const enums = Array.isArray(schema.enum) ? schema.enum : null;
  const oneOf = oneOfOptions(schema);

  if (widget === 'hidden') return null;

  if (widget === 'json' || widget === 'javascript' || widget === 'code' || widget === 'textarea') {
    const lang = widget === 'javascript' ? 'javascript' : widget === 'json' || widget === 'code' ? 'json' : 'text';
    const text = typeof value === 'string' ? value : value == null ? '' : pretty(value);
    return (
      <CodeField
        value={text}
        lang={lang === 'text' ? 'text' : lang}
        height={fieldHeight(ui, widget === 'textarea' ? 80 : 140)}
        readOnly={readOnly}
        onChange={(s) => {
          if (widget === 'json' || widget === 'code') {
            try { onChange(JSON.parse(s)); } catch { onChange(s); }
          } else onChange(s);
        }}
      />
    );
  }

  if (t === 'boolean' || widget === 'checkbox') {
    return (
      <input type="checkbox" className="mt-1" disabled={readOnly}
        checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
    );
  }

  if (oneOf || enums) {
    const opts = oneOf ?? (enums ?? []).map((v) => ({ value: v, label: String(v) }));
    const cur = value === undefined ? '' : JSON.stringify(value);
    return (
      <select className="field-inline" disabled={readOnly} value={cur}
        onChange={(e) => {
          const raw = e.target.value;
          try { onChange(JSON.parse(raw)); } catch { onChange(raw); }
        }}>
        {opts.map((o, i) => (
          <option key={i} value={JSON.stringify(o.value)}>{o.label}</option>
        ))}
      </select>
    );
  }

  if (t === 'integer' || t === 'number') {
    return (
      <input type="number" className="field-inline" disabled={readOnly} step={t === 'integer' ? 1 : 'any'}
        value={typeof value === 'number' ? value : ''}
        onChange={(e) => {
          const n = e.target.value === '' ? undefined : Number(e.target.value);
          onChange(t === 'integer' && n !== undefined ? Math.trunc(n) : n);
        }} />
    );
  }

  return (
    <input className="field-inline" disabled={readOnly}
      value={value == null ? '' : String(value)}
      onChange={(e) => onChange(e.target.value)} />
  );
}

function ObjectPanel({ schema, ui, value, onChange, root, readOnly, title }: {
  schema: JsonSchema; ui?: UiSchema; value: unknown; onChange: (v: unknown) => void;
  root: JsonSchema; readOnly?: boolean; title?: string;
}) {
  const resolved = resolveRef(schema, root);
  const props = isObj(resolved.properties) ? resolved.properties : {};
  const obj = isObj(value) ? value : {};
  return (
    <div className="schema-config-panel">
      {title && <div className="text-[11px] text-muted-foreground mb-1">{title}</div>}
      <SchemaFields schema={resolved} ui={ui} value={obj} onChange={onChange} root={root} readOnly={readOnly} />
    </div>
  );
}

function ArrayEditor({ schema, ui, value, onChange, root, readOnly, title }: {
  schema: JsonSchema; ui?: UiSchema; value: unknown; onChange: (v: unknown) => void;
  root: JsonSchema; readOnly?: boolean; title?: string;
}) {
  const itemsSchema = isObj(schema.items) ? resolveRef(schema.items, root) : { type: 'string' } as JsonSchema;
  const itemsUi = isObj(ui?.items) ? ui!.items as UiSchema : propUi(ui, 'items');
  const arr = Array.isArray(value) ? value : [];
  const itemType = schemaType(itemsSchema);

  const setAt = (i: number, v: unknown) => {
    const next = arr.slice();
    next[i] = v;
    onChange(next);
  };
  const removeAt = (i: number) => onChange(arr.filter((_, j) => j !== i));
  const add = () => onChange([...arr, defaultFor(itemsSchema, root)]);

  return (
    <div className="schema-config-panel">
      <div className="flex items-center justify-between mb-1">
        {title && <div className="text-[11px] text-muted-foreground">{title}</div>}
        {!readOnly && (
          <button type="button" className="schema-config-item-btn" onClick={add} title="+">
            <IconPlus size={12} />
          </button>
        )}
      </div>
      <div className="space-y-1.5">
        {arr.map((item, i) => (
          <div key={i} className="flex items-start gap-1">
            <div className="flex-1 min-w-0">
              {itemType === 'object' || itemsSchema.$ref ? (
                <ObjectPanel schema={itemsSchema} ui={itemsUi} value={item} root={root} readOnly={readOnly}
                  onChange={(v) => setAt(i, v)} />
              ) : (
                <ScalarEditor schema={itemsSchema} ui={itemsUi} value={item} readOnly={readOnly}
                  onChange={(v) => setAt(i, v)} />
              )}
            </div>
            {!readOnly && (
              <button type="button" className="schema-config-item-btn mt-0.5 shrink-0" onClick={() => removeAt(i)} title="-">
                <IconMinus size={12} />
              </button>
            )}
          </div>
        ))}
        {arr.length === 0 && <div className="text-[11px] text-muted-foreground italic">[]</div>}
      </div>
    </div>
  );
}

function SchemaFields({ schema, ui, value, onChange, root, readOnly }: {
  schema: JsonSchema; ui?: UiSchema; value: unknown; onChange: (v: unknown) => void;
  root: JsonSchema; readOnly?: boolean;
}) {
  const resolved = resolveRef(schema, root);
  const props = isObj(resolved.properties) ? resolved.properties : {};
  const keys = orderedKeys(props, ui);
  const obj = isObj(value) ? value : {};

  const setKey = (k: string, v: unknown) => onChange({ ...obj, [k]: v });

  if (keys.length === 0) {
    return (
      <CodeField
        value={pretty(obj)}
        lang="json"
        height={fieldHeight(ui, 140)}
        readOnly={readOnly}
        onChange={(s) => { try { onChange(JSON.parse(s)); } catch { /* keep typing */ } }}
      />
    );
  }

  return (
    <div className="space-y-1.5">
      {keys.map((key) => {
        const ps = isObj(props[key]) ? resolveRef(props[key] as JsonSchema, root) : { type: 'string' } as JsonSchema;
        if (widgetOf(propUi(ui, key)) === 'hidden') return null;
        const pui = propUi(ui, key);
        const typ = schemaType(ps);
        const title = titleOf(key, ps, pui);
        const cur = obj[key] !== undefined ? obj[key] : defaultFor(ps, root);
        const isObjField = typ === 'object' || Boolean(ps.$ref && schemaType(ps) === 'object');
        const isArr = typ === 'array';
        return (
          <div key={key}>
            {isObjField ? (
              <ObjectPanel schema={ps} ui={pui} value={cur} root={root} readOnly={readOnly} title={title}
                onChange={(v) => setKey(key, v)} />
            ) : isArr ? (
              <ArrayEditor schema={ps} ui={pui} value={Array.isArray(cur) ? cur : defaultFor(ps, root)} root={root}
                readOnly={readOnly} title={title} onChange={(v) => setKey(key, v)} />
            ) : (
              <SliderFieldRow label={title}>
                <ScalarEditor schema={ps} ui={pui} value={cur} readOnly={readOnly} onChange={(v) => setKey(key, v)} />
              </SliderFieldRow>
            )}
          </div>
        );
      })}
    </div>
  );
}

export function defaultConfig(schema?: JsonSchema): unknown {
  if (!schema) return {};
  return defaultFor(schema, schema);
}

export function SchemaConfigEditor(props: SchemaConfigEditorProps) {
  const { schema, uiSchema, value, onChange, onSchemaChange, readOnly, className = '', height = 280 } = props;
  const { t } = useTranslation();
  const [tab, setTab] = useState<TabId>('config');
  const [rawText, setRawText] = useState(() => pretty(value ?? {}));
  const [schemaText, setSchemaText] = useState(() => pretty(schema ?? { type: 'object', properties: {} }));
  const [rawErr, setRawErr] = useState<string | null>(null);
  const [schemaErr, setSchemaErr] = useState<string | null>(null);

  const root = schema ?? { type: 'object', properties: {} };

  useEffect(() => {
    if (tab !== 'raw') setRawText(pretty(value ?? {}));
  }, [value, tab]);

  useEffect(() => {
    if (tab !== 'schema') setSchemaText(pretty(schema ?? { type: 'object', properties: {} }));
  }, [schema, tab]);

  const filled = useMemo(() => {
    const d = defaultFor(root, root);
    if (!isObj(d) || !isObj(value)) return value ?? d;
    return { ...d, ...value };
  }, [root, value]);

  const tabs: { id: TabId; label: string }[] = [
    { id: 'config', label: t('workflow.fields.config') },
    { id: 'schema', label: t('workflow.fields.schema') },
    { id: 'raw', label: t('workflow.fields.raw') },
  ];

  const schemaEditable = Boolean(onSchemaChange) && !readOnly;

  return (
    <div className={`schema-config-editor ${className}`} style={{ minHeight: height }}>
      <div className="flex border-b border-border gap-1 px-2 pt-1 shrink-0">
        {tabs.map(({ id, label }) => (
          <button key={id} type="button" onClick={() => setTab(id)}
            className={`px-2 py-1 text-[11px] border-b-2 -mb-px transition-colors ${
              tab === id ? 'border-blue-500 text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}>
            {label}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-auto p-2 min-h-0">
        {tab === 'config' && (
          <SchemaFields schema={root} ui={uiSchema} value={filled} root={root} readOnly={readOnly}
            onChange={(v) => onChange?.(v)} />
        )}
        {tab === 'schema' && (
          <>
            {schemaErr && <div className="alert-error mb-1">{schemaErr}</div>}
            <CodeField
              value={schemaText}
              lang="json"
              height={Math.max(160, height - 48)}
              readOnly={!schemaEditable}
              onChange={(s) => {
                setSchemaText(s);
                try {
                  const parsed = JSON.parse(s);
                  if (!isObj(parsed)) throw new Error('schema must be an object');
                  setSchemaErr(null);
                  onSchemaChange?.(parsed);
                } catch (e) {
                  setSchemaErr(e instanceof Error ? e.message : String(e));
                }
              }}
            />
          </>
        )}
        {tab === 'raw' && (
          <>
            {rawErr && <div className="alert-error mb-1">{rawErr}</div>}
            <CodeField
              value={rawText}
              lang="json"
              height={Math.max(160, height - 48)}
              readOnly={readOnly}
              onChange={(s) => {
                setRawText(s);
                try {
                  const parsed = JSON.parse(s);
                  setRawErr(null);
                  onChange?.(parsed);
                } catch (e) {
                  setRawErr(e instanceof Error ? e.message : String(e));
                }
              }}
            />
          </>
        )}
      </div>
    </div>
  );
}
