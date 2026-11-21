import { errors } from "../model/registry";
import type { ListResult, Row, Values } from "../model/types";
const unknownActionResult = "未能读取办理结果，请先刷新并核对记录。";
export class ApiError extends Error {
  constructor(
    public code: string,
    public status: number,
  ) {
    super(errors[code] || code || "请求失败，请稍后重试。");
  }
}
export const session = {
  get: () => sessionStorage.getItem("elder_token"),
  set: (token: string) => sessionStorage.setItem("elder_token", token),
  clear: () => sessionStorage.removeItem("elder_token"),
};
export async function api<T = any>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = session.get();
  const response = await fetch("/api" + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
      ...options.headers,
    },
  });
  const data = await response.json().catch(() => undefined);
  if (!response.ok) {
    const sameSession = token !== null && token === session.get();
    if (sameSession && response.status === 401 && path !== "/login") {
      session.clear();
      window.dispatchEvent(new Event("session-expired"));
    }
    if (sameSession && data?.error === "PASSWORD_CHANGE_REQUIRED")
      window.dispatchEvent(new Event("password-required"));
    throw new ApiError(data?.error, response.status);
  }
  if (data == null) {
    const message = path.startsWith("/actions/")
      ? unknownActionResult
      : "服务器返回的内容无法读取，请重新加载。";
    throw new ApiError(message, response.status);
  }
  return data;
}
export async function command(
  action: string,
  body: Values,
  signal?: AbortSignal,
) {
  const data = await api<{ result: any }>("/actions/" + action, {
    method: "POST",
    body: JSON.stringify(body),
    signal,
  });
  if (typeof data !== "object" || !Object.hasOwn(data, "result"))
    throw new Error(unknownActionResult);
  return data;
}
export function list(
  kind: string,
  query: Record<string, string | number | undefined> = {},
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== "") params.set(key, String(value));
  return api<ListResult>("/data/" + kind + "?" + params, { signal });
}
export async function allRows(
  kind: string,
  query: Record<string, string | number | undefined> = {},
  signal?: AbortSignal,
): Promise<Row[]> {
  const rows: Row[] = [];
  for (let page = 0; ; page++) {
    const data = await list(kind, { ...query, page, size: 100 }, signal);
    rows.push(...data.rows);
    if ((page + 1) * 100 >= data.total || data.rows.length === 0) return rows;
  }
}
export const messageOf = (e: unknown) =>
  e instanceof Error ? e.message : "操作失败，请稍后重试。";
export const isAbort = (e: unknown) =>
  e instanceof DOMException && e.name === "AbortError";
