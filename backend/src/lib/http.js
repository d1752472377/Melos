/**
 * 统一 HTTP 工具：为所有上游 fetch 加超时。
 *
 * 背景：此前各处直接 fetch 网易云 / 妖狐 / ChKSz 均无超时，
 * 上游 hang 时请求会无限悬挂（Vercel 靠 maxDuration 截断，自托管则永久占用连接）。
 */
export function fetchWithTimeout(url, init = {}, timeoutMs = 10_000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer));
}