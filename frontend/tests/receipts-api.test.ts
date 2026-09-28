import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";

const auth = vi.hoisted(() => ({ accessToken: vi.fn() }));
vi.mock("../src/auth", () => auth);
import { receiptApi, validateUpload } from "../src/receipts-api";

const receipt = {
  receiptId: "920a995d-693c-4634-9fcf-985b1ddc0199", userId: "verified-subject", merchant: "Store",
  purchaseDate: "2026-09-28", total: 19.99, currency: "CAD", category: null,
  imageKey: "verified-subject/originals/image", status: "UPLOADED",
  createdAt: "2026-09-28T12:00:00Z", updatedAt: "2026-09-28T12:00:00Z",
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubEnv("VITE_API_BASE_URL", "http://localhost:3000");
  auth.accessToken.mockResolvedValue("verified-access-token");
});
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe("typed receipt API client", () => {
  it("attaches the access token and never sends owner identity in create/update bodies", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify(receipt), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const fields = { merchant: "Store", purchaseDate: "2026-09-28", total: 19.99,
      currency: "CAD", category: null };
    await receiptApi.create(fields, "verified-subject/originals/image");
    await receiptApi.update(receipt.receiptId, fields);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init.headers.Authorization).toBe("Bearer verified-access-token");
      expect(JSON.parse(init.body)).not.toHaveProperty("userId");
      expect(JSON.parse(init.body)).not.toHaveProperty("receiptId");
    }
  });

  it("fails closed without an access token or a configured API URL", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    auth.accessToken.mockResolvedValueOnce(null);
    await expect(receiptApi.list()).rejects.toThrow("session has expired");
    vi.stubEnv("VITE_API_BASE_URL", "");
    await expect(receiptApi.list()).rejects.toThrow("API URL is not configured");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("encodes combined list filters and sorting with the authenticated request", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response("[]", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await receiptApi.list({ merchant: " Corner Shop ", category: "Office Supplies",
      dateFrom: "2026-09-01", dateTo: "2026-09-28", sort: "total_desc" });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:3000/receipts?merchant=Corner+Shop&category=Office+Supplies" +
      "&dateFrom=2026-09-01&dateTo=2026-09-28&sort=total_desc");
    expect(init.headers.Authorization).toBe("Bearer verified-access-token");
    expect(url).not.toContain("userId");
    await receiptApi.list({ merchant: " ", category: "" });
    expect(fetchMock.mock.calls[1][0]).toBe("http://localhost:3000/receipts");
  });

  it("rejects invalid date ranges and sort values before fetch", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    await expect(receiptApi.list({ dateFrom: "2026-02-30" })).rejects.toThrow("Invalid dateFrom");
    await expect(receiptApi.list({ dateFrom: "2026-09-29", dateTo: "2026-09-28" }))
      .rejects.toThrow("start date");
    await expect(receiptApi.list({ sort: "unknown" as "date_desc" })).rejects.toThrow("Invalid receipt sort");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("uses all browser-settable signed headers and sends no Cognito token to the presigned URL", async () => {
    const file = new File(["receipt bytes"], "receipt.png", { type: "image/png" });
    const authorization = { uploadUrl: "https://private-bucket.example/opaque-signature",
      imageKey: "verified-subject/originals/uuid", method: "PUT",
      expiresAt: new Date(Date.now() + 300_000).toISOString(),
      headers: { "content-type": ["image/png"], "if-none-match": ["*"],
        "content-length": [String(file.size)], host: ["private-bucket.example"] } };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authorization), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    expect(await receiptApi.upload(file)).toBe(authorization.imageKey);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [apiUrl, apiInit] = fetchMock.mock.calls[0];
    expect(apiUrl).toBe("http://localhost:3000/receipts/upload-url");
    expect(apiInit.headers.Authorization).toBe("Bearer verified-access-token");
    expect(JSON.parse(apiInit.body)).toEqual({ fileName: file.name, contentType: file.type, contentLength: file.size });
    const [uploadUrl, uploadInit] = fetchMock.mock.calls[1];
    expect(uploadUrl).toBe(authorization.uploadUrl);
    expect(uploadInit.method).toBe("PUT");
    expect(uploadInit.body).toBe(file);
    expect(uploadInit.headers.get("content-type")).toBe("image/png");
    expect(uploadInit.headers.get("if-none-match")).toBe("*");
    expect(uploadInit.headers.has("authorization")).toBe(false);
    expect(uploadInit.headers.has("content-length")).toBe(false);
  });

  it("rejects invalid files and expired upload authorizations before uploading", async () => {
    expect(() => validateUpload(new File(["x"], "receipt.txt", { type: "text/plain" }))).toThrow();
    expect(() => validateUpload(new File([], "receipt.png", { type: "image/png" }))).toThrow();
    const file = new File(["x"], "receipt.png", { type: "image/png" });
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({
      uploadUrl: "https://private-bucket.example/expired", imageKey: "some-key", method: "PUT",
      expiresAt: new Date(Date.now() - 1000).toISOString(), headers: {},
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(receiptApi.upload(file)).rejects.toThrow("invalid or expired");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
