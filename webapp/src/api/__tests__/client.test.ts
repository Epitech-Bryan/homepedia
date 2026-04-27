import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

async function fetchJson<T>(path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(`/api${path}`, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value) url.searchParams.set(key, value);
    });
  }
  const response = await fetch(url.toString());
  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

describe("fetchJson", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("constructs the correct URL without params", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ data: "test" }),
    });

    await fetchJson("/regions");

    expect(mockFetch).toHaveBeenCalledWith("http://localhost:3000/api/regions");
  });

  it("appends query params to the URL", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });

    await fetchJson("/departments", { regionCode: "11", size: "10" });

    const calledUrl = new URL(mockFetch.mock.calls[0][0]);
    expect(calledUrl.pathname).toBe("/api/departments");
    expect(calledUrl.searchParams.get("regionCode")).toBe("11");
    expect(calledUrl.searchParams.get("size")).toBe("10");
  });

  it("skips falsy param values", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });

    await fetchJson("/cities", { name: "", code: "75" });

    const calledUrl = new URL(mockFetch.mock.calls[0][0]);
    expect(calledUrl.searchParams.has("name")).toBe(false);
    expect(calledUrl.searchParams.get("code")).toBe("75");
  });

  it("throws on non-OK responses", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 404,
      statusText: "Not Found",
    });

    await expect(fetchJson("/unknown")).rejects.toThrow("API error: 404 Not Found");
  });

  it("returns parsed JSON on success", async () => {
    const payload = { code: "11", name: "Île-de-France" };
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(payload),
    });

    const result = await fetchJson("/regions/11");
    expect(result).toEqual(payload);
  });
});
