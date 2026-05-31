import type { DashLayout, Dashs, DashCreateReq, DashUpdateReq, DashRes } from './types';
import { authHeaders, handleResponse } from '../api';

function getBaseUrl(): string {
  const stored = localStorage.getItem('VITE_DASH_API_URL');
  if (stored) return stored;
  return import.meta.env.VITE_DASH_API_URL || 'http://localhost:8080/api/v1/dash';
}

export async function listDashes(token: string | null): Promise<Dashs> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/`, { headers: authHeaders(token) });
  return handleResponse<Dashs>(res);
}

export async function getDash(token: string | null, id: string): Promise<DashLayout> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/${encodeURIComponent(id)}`, { headers: authHeaders(token) });
  return handleResponse<DashLayout>(res);
}

export async function createDash(token: string | null, req: DashCreateReq): Promise<DashRes> {
  const base = getBaseUrl();
  const res = await fetch(base, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(req),
  });
  return handleResponse<DashRes>(res);
}

export async function updateDash(
  token: string | null,
  id: string,
  req: DashUpdateReq,
): Promise<DashRes> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(req),
  });
  return handleResponse<DashRes>(res);
}

export async function deleteDash(token: string | null, id: string): Promise<DashRes> {
  const base = getBaseUrl();
  const res = await fetch(`${base}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
  return handleResponse<DashRes>(res);
}
