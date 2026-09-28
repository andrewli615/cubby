import { accessToken } from "./auth";

export type ReceiptStatus = "UPLOADED" | "PROCESSING" | "READY" | "REVIEW_NEEDED" | "OCR_FAILED";

export interface Receipt {
  receiptId: string;
  userId: string;
  merchant: string;
  purchaseDate: string;
  total: number;
  currency: string;
  category: string | null;
  imageKey: string;
  status: ReceiptStatus;
  createdAt: string;
  updatedAt: string;
  ocr?: {
    merchant: string | null;
    purchaseDate: string | null;
    total: number | null;
    currency: string | null;
    reviewRequired: boolean;
  } | null;
}

export interface ReceiptFields {
  merchant: string;
  purchaseDate: string;
  total: number;
  currency: string;
  category: string | null;
}

interface UploadAuthorization {
  uploadUrl: string;
  imageKey: string;
  expiresAt: string;
  method: "PUT";
  headers: Record<string, string[]>;
}

export class ReceiptApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = "ReceiptApiError";
  }
}

function apiBase(): string {
  const value = import.meta.env.VITE_API_BASE_URL?.trim();
  if (!value) throw new ReceiptApiError("API URL is not configured.");
  const url = new URL(value);
  if (url.protocol !== "https:" && !(url.protocol === "http:" && url.hostname === "localhost")) {
    throw new ReceiptApiError("API URL must use HTTPS.");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new ReceiptApiError("API URL is invalid.");
  }
  return value.replace(/\/$/, "");
}

async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await accessToken();
  if (!token) throw new ReceiptApiError("Your session has expired. Sign in again.", 401);
  const base = apiBase();
  let response: Response;
  try {
    response = await fetch(`${base}${path}`, {
      ...init,
      cache: "no-store",
      headers: { Authorization: `Bearer ${token}`, ...(init.body ? { "Content-Type": "application/json" } : {}) },
    });
  } catch {
    throw new ReceiptApiError("Could not reach the receipt service.");
  }
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new ReceiptApiError("Your session has expired. Sign in again.", response.status);
    }
    if (response.status === 404) throw new ReceiptApiError("Receipt not found.", 404);
    if (response.status === 409) throw new ReceiptApiError("This receipt changed. Reload and try again.", 409);
    if (response.status === 400) throw new ReceiptApiError("The receipt data was rejected. Check your entries.", 400);
    throw new ReceiptApiError("The receipt service could not complete the request.", response.status);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export const receiptApi = {
  list: () => apiRequest<Receipt[]>("/receipts"),
  get: (receiptId: string) => apiRequest<Receipt>(`/receipts/${encodeURIComponent(receiptId)}`),
  create: (fields: ReceiptFields, imageKey: string) =>
    apiRequest<Receipt>("/receipts", { method: "POST", body: JSON.stringify({ ...fields, imageKey }) }),
  update: (receiptId: string, fields: ReceiptFields) =>
    apiRequest<Receipt>(`/receipts/${encodeURIComponent(receiptId)}`,
      { method: "PUT", body: JSON.stringify(fields) }),
  delete: (receiptId: string) =>
    apiRequest<void>(`/receipts/${encodeURIComponent(receiptId)}`, { method: "DELETE" }),
  upload: async (file: File): Promise<string> => {
    validateUpload(file);
    const authorization = await apiRequest<UploadAuthorization>("/receipts/upload-url", {
      method: "POST",
      body: JSON.stringify({ fileName: file.name, contentType: file.type, contentLength: file.size }),
    });
    const expiresAt = Date.parse(authorization.expiresAt);
    if (authorization.method !== "PUT" || !authorization.uploadUrl.startsWith("https://") ||
        !authorization.imageKey || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      throw new ReceiptApiError("Upload authorization is invalid or expired.");
    }
    const signedHeaders = new Headers();
    for (const [name, values] of Object.entries(authorization.headers)) {
      // Browsers manage Host and Content-Length for a raw File body.
      if (name.toLowerCase() === "host" || name.toLowerCase() === "content-length") continue;
      for (const value of values) signedHeaders.append(name, value);
    }
    let uploaded: Response;
    try {
      uploaded = await fetch(authorization.uploadUrl, {
        method: "PUT", headers: signedHeaders, body: file, cache: "no-store",
      });
    } catch {
      throw new ReceiptApiError("Could not upload the original file. Try again.");
    }
    if (!uploaded.ok) throw new ReceiptApiError("Could not upload the original file. Try again.", uploaded.status);
    return authorization.imageKey;
  },
};

export function validateUpload(file: File): void {
  const hasControl = [...file.name].some((character) => {
    const code = character.codePointAt(0) ?? 0;
    return code < 32 || (code >= 127 && code <= 159);
  });
  if (!file.name || file.name.length > 255 || file.name.trim() !== file.name ||
      /[/\\]/.test(file.name) || hasControl || file.name === "." || file.name === ".." ||
      !["image/jpeg", "image/png", "application/pdf"].includes(file.type) ||
      file.size < 1 || file.size > 10 * 1024 * 1024) {
    throw new ReceiptApiError("Choose a JPEG, PNG, or PDF file up to 10 MB.");
  }
}
