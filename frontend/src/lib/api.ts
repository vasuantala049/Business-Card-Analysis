export interface BusinessCard {
  id: string;
  ownerId?: string;
  name: string | null;
  designation: string | null;
  company: string | null;
  phones: string[];
  emails: string[];
  website: string | null;
  address: string | null;
  rawOcrText: string;
  logoImageBase64: string | null;
  confidence: number;
  extractionSource: "gemini" | "spacy_regex" | string;
  createdAt: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  provider: "LOCAL" | "GOOGLE";
  role: "ROLE_USER" | "ROLE_ADMIN";
}

export const API_BASE_URL =
  (import.meta.env["VITE_API_BASE_URL"] as string | undefined) ?? "/api";

export const GOOGLE_AUTH_URL = "/oauth2/authorization/google";

export const LOW_CONFIDENCE = 0.6;

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (data && data.message) msg = data.message;
    } catch {
      const text = await res.text().catch(() => "");
      if (text) msg = text;
    }
    throw new ApiError(msg, res.status);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// Auth API calls
export async function signupUser(payload: { name: string; email: string; password: String }) {
  const res = await fetch(`${API_BASE_URL}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  return handle<User>(res);
}

export async function loginUser(payload: { email: string; password: String }) {
  const res = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  return handle<User>(res);
}

export async function logoutUser() {
  const res = await fetch(`${API_BASE_URL}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
  return handle<{ message: string }>(res);
}

export async function getCurrentUser() {
  const res = await fetch(`${API_BASE_URL}/auth/me`, {
    method: "GET",
    credentials: "include",
  });
  return handle<User>(res);
}

// Business Card API calls
export async function uploadCard(file: File | Blob, filename = "card.jpg") {
  const form = new FormData();
  form.append("file", file, filename);
  const res = await fetch(`${API_BASE_URL}/cards/upload`, {
    method: "POST",
    credentials: "include",
    body: form,
  });
  return handle<BusinessCard>(res);
}

export async function listCards() {
  const res = await fetch(`${API_BASE_URL}/cards`, {
    credentials: "include",
  });
  return handle<BusinessCard[]>(res);
}

export async function getCard(id: string) {
  const res = await fetch(`${API_BASE_URL}/cards/${id}`, {
    credentials: "include",
  });
  return handle<BusinessCard>(res);
}

export async function updateCard(card: BusinessCard) {
  const res = await fetch(`${API_BASE_URL}/cards/${card.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(card),
  });
  return handle<BusinessCard>(res);
}

export async function deleteCard(id: string) {
  const res = await fetch(`${API_BASE_URL}/cards/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  return handle<void>(res);
}

export const logoSrc = (b64: string) => `data:image/png;base64,${b64}`;

export const splitList = (value: string) =>
  value
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);