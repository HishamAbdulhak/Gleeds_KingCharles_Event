const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const TOKEN_KEY = "adminToken";

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

type ErrorBody = { message?: string; errors?: { field: string; defaultMessage: string }[] };

/** fetch with the admin JWT attached. Throws an Error with `status` on non-2xx; message comes from Boot's error body. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try {
      const body: ErrorBody = JSON.parse(text);
      message = body.errors?.map((e) => `${e.field}: ${e.defaultMessage}`).join(", ") || body.message || text;
    } catch {}
    throw Object.assign(new Error(message), { status: res.status });
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export async function login(email: string, password: string) {
  const { token } = await api<{ token: string }>("/api/admin/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  localStorage.setItem(TOKEN_KEY, token);
}
