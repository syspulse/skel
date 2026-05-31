import type { ActionRes, Explain, ExplainCreateReq, Explains, ExplainUpdateReq, ExplainRes } from './types';
import { authHeaders, handleResponse } from '../api';

function getBaseUrl(): string {
  const stored = localStorage.getItem('VITE_API_URL');
  if (stored) return stored;
  return import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1/explain';
}

export async function listRules(
  token: string | null,
  oid?: string,
  rid?: string,
): Promise<Explains> {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  if (oid && oid.trim()) params.set('oid', oid.trim());
  if (rid && rid.trim()) params.set('rid', rid.trim());
  const query = params.toString();
  const url = query ? `${base}?${query}` : base;
  const res = await fetch(url, { headers: authHeaders(token) });
  return handleResponse<Explains>(res);
}

export async function getRule(
  token: string | null,
  rid: string,
  oid?: string,
): Promise<Explain> {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  if (oid && oid.trim()) params.set('oid', oid.trim());
  const query = params.toString();
  const url = query ? `${base}/${encodeURIComponent(rid)}?${query}` : `${base}/${encodeURIComponent(rid)}`;
  const res = await fetch(url, { headers: authHeaders(token) });
  return handleResponse<Explain>(res);
}

export async function createRule(
  token: string | null,
  rid: string,
  req: ExplainCreateReq,
): Promise<ActionRes> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/${encodeURIComponent(rid)}`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(req),
  });
  return handleResponse<ActionRes>(res);
}

export async function updateRule(
  token: string | null,
  rid: string,
  req: ExplainUpdateReq,
): Promise<ActionRes> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/${encodeURIComponent(rid)}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(req),
  });
  return handleResponse<ActionRes>(res);
}

export async function deleteRule(
  token: string | null,
  rid: string,
  oid?: string,
): Promise<ActionRes> {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  if (oid && oid.trim()) params.set('oid', oid.trim());
  const query = params.toString();
  const url = query
    ? `${base}/${encodeURIComponent(rid)}?${query}`
    : `${base}/${encodeURIComponent(rid)}`;
  const res = await fetch(url, { method: 'DELETE', headers: authHeaders(token) });
  return handleResponse<ActionRes>(res);
}

export async function deleteRules(
  token: string | null,
  oid?: string,
): Promise<Explains> {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  if (oid && oid.trim()) params.set('oid', oid.trim());
  const query = params.toString();
  const url = query ? `${base}?${query}` : base;
  const res = await fetch(url, { method: 'DELETE', headers: authHeaders(token) });
  return handleResponse<Explains>(res);
}

export async function runExplain(
  token: string | null,
  rid: string,
  data: unknown,
  oid?: string,
  style?: string,
): Promise<ExplainRes> {
  const base = getBaseUrl();
  const params = new URLSearchParams();
  if (style && style.trim()) params.set('style', style.trim());
  if (oid && oid.trim()) params.set('oid', oid.trim());
  const query = params.toString();
  const url = `${base}/${encodeURIComponent(rid)}/explain${query ? `?${query}` : ''}`;
  const body: Record<string, unknown> = { rid, data };
  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  });
  return handleResponse<ExplainRes>(res);
}
