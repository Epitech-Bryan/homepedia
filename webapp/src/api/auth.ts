const BASE_URL = "/api/auth";

export type Me = { username: string };

export async function login(username: string, password: string): Promise<Me> {
  const res = await fetch(`${BASE_URL}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ username, password }),
  });
  if (res.status === 401) {
    throw new InvalidCredentialsError();
  }
  if (!res.ok) {
    throw new Error(`Login failed: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function logout(): Promise<void> {
  await fetch(`${BASE_URL}/logout`, {
    method: "POST",
    credentials: "include",
  });
}

export async function fetchMe(): Promise<Me | null> {
  const res = await fetch(`${BASE_URL}/me`, {
    credentials: "include",
  });
  if (res.status === 401) return null;
  if (!res.ok) {
    throw new Error(`Failed to fetch /auth/me: ${res.status}`);
  }
  return res.json();
}

export class InvalidCredentialsError extends Error {
  constructor() {
    super("Identifiant ou mot de passe invalide");
    this.name = "InvalidCredentialsError";
  }
}
