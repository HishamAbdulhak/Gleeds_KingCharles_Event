export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const TOKEN_KEY = "adminToken";
const LOGIN_PATH = "/api/admin/login";
const LOGIN_PAGE = "/admin/login";

export const getToken = () => localStorage.getItem(TOKEN_KEY);

/** Drops the admin token and hard-navigates to the login page (no page state is worth keeping). */
export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  window.location.replace(LOGIN_PAGE);
}

type ErrorBody = { message?: string; errors?: { field: string; defaultMessage: string }[] };

/**
 * fetch with the admin JWT attached. A 401 anywhere but login means the token is gone or expired: logs out
 * (login page) and throws. Other non-2xx throw an Error with `status`; message comes from Boot's error body.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (res.status === 401 && path !== LOGIN_PATH) logout();
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
  const { token } = await api<{ token: string }>(LOGIN_PATH, {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  localStorage.setItem(TOKEN_KEY, token);
}
