export interface BusinessCard {
  id: string;
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

export const API_BASE_URL =
  (import.meta.env["VITE_API_BASE_URL"] as string | undefined) ?? "http://localhost:8080/api";

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
    const text = await res.text().catch(() => "");
    throw new ApiError(text || `Request failed (${res.status})`, res.status);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function uploadCard(file: File | Blob, filename = "card.jpg") {
  const form = new FormData();
  form.append("file", file, filename);
  const res = await fetch(`${API_BASE_URL}/cards/upload`, { method: "POST", body: form });
  return handle<BusinessCard>(res);
}

export async function listCards() {
  return handle<BusinessCard[]>(await fetch(`${API_BASE_URL}/cards`));
}

export async function getCard(id: string) {
  return handle<BusinessCard>(await fetch(`${API_BASE_URL}/cards/${id}`));
}

export async function updateCard(card: BusinessCard) {
  const res = await fetch(`${API_BASE_URL}/cards/${card.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(card),
  });
  return handle<BusinessCard>(res);
}

export async function deleteCard(id: string) {
  return handle<void>(await fetch(`${API_BASE_URL}/cards/${id}`, { method: "DELETE" }));
}

export const logoSrc = (b64: string) => `data:image/png;base64,${b64}`;

export const splitList = (value: string) =>
  value
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);