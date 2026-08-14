import React, { createContext, useContext, useState } from 'react';

// Owner (oid) defaults used when creating / starting a WorkflowConfig. Persisted in localStorage
// (same pattern as PageSize / TimestampFormat / WorkflowGrid settings).
//   oid         - the owner id value used for create/start (manual, or filled from oid_extract). Default '0'.
//   oidExtract  - an extraction expression that resolves oid from context, e.g. "{JWT}.tenantId".
//                 Resolved on demand (Enter in the profile field) -> writes into `oid`. More placeholders
//                 (beyond {JWT}) can be added later; unresolved / no JWT -> '0'.
export const DEFAULT_OID = '0';
const KEY_OID = 'ownerOid';
const KEY_OID_EXTRACT = 'ownerOidExtract';

interface OwnerSettings {
  oid: string;
  setOid: (v: string) => void;
  oidExtract: string;
  setOidExtract: (v: string) => void;
}

const OwnerContext = createContext<OwnerSettings>({
  oid: DEFAULT_OID, setOid: () => {}, oidExtract: '', setOidExtract: () => {},
});

export function OwnerProvider({ children }: { children: React.ReactNode }) {
  const [oid, setOidState] = useState<string>(() => localStorage.getItem(KEY_OID) ?? DEFAULT_OID);
  const [oidExtract, setOidExtractState] = useState<string>(() => localStorage.getItem(KEY_OID_EXTRACT) ?? '');

  const setOid = (v: string) => { localStorage.setItem(KEY_OID, v); setOidState(v); };
  const setOidExtract = (v: string) => { localStorage.setItem(KEY_OID_EXTRACT, v); setOidExtractState(v); };

  return (
    <OwnerContext.Provider value={{ oid, setOid, oidExtract, setOidExtract }}>
      {children}
    </OwnerContext.Provider>
  );
}

export function useOwnerSettings() {
  return useContext(OwnerContext);
}

/**
 * Resolve an oid_extract expression to an oid string. Currently supports the `{JWT}.<dot.path>`
 * placeholder (read from the parsed JWT claims). Unresolved / missing / no JWT -> '0'.
 * Extend here as new placeholders are introduced.
 */
export function resolveOidExtract(expr: string, tokenParsed: Record<string, unknown> | null | undefined): string {
  const e = (expr ?? '').trim();
  if (!e) return DEFAULT_OID;
  const m = e.match(/^\{JWT\}\.?(.*)$/); // "{JWT}.tenantId" | "{JWT}"
  if (m && tokenParsed) {
    const path = m[1].trim();
    let cur: unknown = tokenParsed;
    if (path) {
      for (const key of path.split('.')) {
        if (cur && typeof cur === 'object' && key in (cur as Record<string, unknown>)) {
          cur = (cur as Record<string, unknown>)[key];
        } else { cur = undefined; break; }
      }
    }
    if (cur != null && typeof cur !== 'object' && String(cur) !== '') return String(cur);
  }
  return DEFAULT_OID;
}

/** The oid to pre-fill when creating/starting a WorkflowConfig: the stored `oid` (default '0'). */
export function useDefaultOid(): string {
  const { oid } = useOwnerSettings();
  return oid || DEFAULT_OID;
}
