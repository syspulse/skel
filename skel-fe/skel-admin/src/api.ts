export function authHeaders(token: string | null): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/** fetch + JSON parse; network/HTTP failures include the request URL. */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    throw new Error(`${msg}: ${url}`);
  }
  return handleResponse<T>(res);
}

export async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `HTTP ${res.status} ${res.statusText}`;
    if (res.url) message += ` ${res.url}`;
    try {
      const body = await res.text();
      if (body) message += `: ${body}`;
    } catch {
      // ignore
    }
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}
