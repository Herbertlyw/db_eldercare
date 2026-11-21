import { afterEach, describe, expect, it, vi } from "vitest";
import { api, session } from "../src/api/client";

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe.each([
  { status: 401, code: "请重新登录", event: "session-expired" },
  { status: 403, code: "PASSWORD_CHANGE_REQUIRED", event: "password-required" },
])("会话响应 $status", ({ status, code, event }) => {
  it.each([true, false])("收到响应前是否已重新登录：%s", async (switched) => {
    let current = "first-session";
    vi.spyOn(session, "get").mockImplementation(() => current);
    const clear = vi.spyOn(session, "clear").mockImplementation(() => {
      current = "";
    });
    const windowEvents = new EventTarget();
    const notices = vi.fn();
    windowEvents.addEventListener(event, notices);
    vi.stubGlobal("window", windowEvents);
    let respond!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve;
        }),
    );

    const result = api("/me");
    if (switched) current = "next-session";
    respond(Response.json({ error: code }, { status }));
    await expect(result).rejects.toMatchObject({ status, code });
    expect(notices).toHaveBeenCalledTimes(switched ? 0 : 1);
    expect(clear).toHaveBeenCalledTimes(!switched && status === 401 ? 1 : 0);
    if (switched) expect(current).toBe("next-session");
  });
});
