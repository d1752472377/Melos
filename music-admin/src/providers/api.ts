import { API_URL, TOKEN_KEY } from "./constants";

export interface ApiResponse<T = any> {
  code: number;
  data?: T;
  msg?: string;
}

export async function request<T = any>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = new Headers(options.headers || {});

  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const url = endpoint.startsWith("http")
    ? endpoint
    : `${API_URL}${endpoint.startsWith("/") ? "" : "/"}${endpoint}`;

  const res = await fetch(url, {
    ...options,
    headers,
  });

  let json: ApiResponse<T>;
  try {
    json = await res.json();
  } catch (err) {
    if (!res.ok) {
      throw new Error(`请求失败 (${res.status})`);
    }
    return { code: res.status } as ApiResponse<T>;
  }

  if (res.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem("user");
    window.location.href = "/login";
    throw new Error(json.msg || "登录已失效，请重新登录");
  }

  if (!res.ok && json.code === undefined) {
    json.code = res.status;
  }

  return json;
}
