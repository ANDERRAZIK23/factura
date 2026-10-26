import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, configureSession, setAccessToken } from "./client";
import { server } from "../test/setup";

const base = "http://127.0.0.1:8080/api/v1";
const refreshed = {
  accessToken: "new-token",
  tokenType: "Bearer" as const,
  expiresIn: 900,
  user: {
    id: "1",
    fullName: "Admin",
    email: "admin@example.com",
    role: "ADMIN" as const,
  },
};

afterEach(() => setAccessToken(null));

describe("API refresh interceptor", () => {
  it("refreshes once for concurrent 401 responses and retries both", async () => {
    let refreshCount = 0;
    configureSession(async () => {
      refreshCount += 1;
      await new Promise((resolve) => setTimeout(resolve, 10));
      setAccessToken(refreshed.accessToken);
      return refreshed;
    }, vi.fn());
    server.use(
      http.get(`${base}/protected/:id`, ({ request }) =>
        request.headers.get("authorization") === "Bearer new-token"
          ? HttpResponse.json({ ok: true })
          : new HttpResponse(null, { status: 401 }),
      ),
    );
    const [first, second] = await Promise.all([
      api.get("/protected/1"),
      api.get("/protected/2"),
    ]);
    expect(first.data.ok).toBe(true);
    expect(second.data.ok).toBe(true);
    expect(refreshCount).toBe(1);
  });

  it("clears the session when refresh fails", async () => {
    const failed = vi.fn();
    configureSession(async () => {
      throw new Error("refresh failed");
    }, failed);
    server.use(
      http.get(
        `${base}/protected`,
        () => new HttpResponse(null, { status: 401 }),
      ),
    );
    await expect(api.get("/protected")).rejects.toThrow("refresh failed");
    expect(failed).toHaveBeenCalledOnce();
  });

  it("does not refresh repeatedly when the retried request is still unauthorized", async () => {
    let refreshCount = 0;
    configureSession(async () => {
      refreshCount += 1;
      return refreshed;
    }, vi.fn());
    server.use(
      http.get(
        `${base}/protected`,
        () => new HttpResponse(null, { status: 401 }),
      ),
    );
    await expect(api.get("/protected")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refreshCount).toBe(1);
  });
});
