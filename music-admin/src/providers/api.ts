import { API_URL, TOKEN_KEY } from "./constants";

export interface ApiResponse<T = any> {
  code: number;
  data?: T;
  msg?: string;
  headers?: Headers;
}

let isRedirectingToLogin = false;

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
  } catch {
    if (!res.ok) {
      throw new Error(`请求失败 (${res.status})`);
    }
    return { code: res.status, headers: res.headers } as ApiResponse<T>;
  }

  json.headers = res.headers;

  if (res.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem("user");
    if (!isRedirectingToLogin && window.location.pathname !== "/login") {
      isRedirectingToLogin = true;
      window.location.href = "/login";
    }
    throw new Error(json.msg || "登录已失效，请重新登录");
  }

  if (!res.ok && json.code === undefined) {
    json.code = res.status;
  }

  return json;
}

export interface AppVersion {
  id: number;
  version_code: number;
  versionCode: number;
  version_name: string;
  versionName: string;
  apk_url: string;
  apkUrl: string;
  file_size: number;
  fileSize: number;
  hash_sha256: string;
  hashSha256: string;
  changelog: string;
  force_update: boolean;
  forceUpdate: boolean;
  enabled: boolean;
  created_at: string;
  createdAt: string;
  has_local_file?: boolean;
  hasLocalFile?: boolean;
}

export const versionApi = {
  list: async (params: { page?: number; limit?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page) query.append("page", String(params.page));
    if (params.limit) query.append("limit", String(params.limit));
    const res = await request<{ items: AppVersion[]; total: number; page: number; limit: number }>(
      `/admin/versions?${query.toString()}`
    );
    const totalCountHeader = res.headers?.get("x-total-count");
    const total = totalCountHeader ? parseInt(totalCountHeader, 10) : res.data?.total || 0;
    return {
      items: res.data?.items || [],
      total,
      page: res.data?.page || params.page || 1,
      limit: res.data?.limit || params.limit || 10,
    };
  },

  create: async (formData: FormData) => {
    return request<AppVersion>("/admin/versions", {
      method: "POST",
      body: formData,
    });
  },

  update: async (
    id: number,
    data: { enabled?: boolean; forceUpdate?: boolean; changelog?: string }
  ) => {
    return request<AppVersion>(`/admin/versions/${id}`, {
      method: "PATCH",
      body: JSON.stringify(data),
    });
  },

  delete: async (id: number) => {
    return request(`/admin/versions/${id}`, {
      method: "DELETE",
    });
  },

  getLatest: async () => {
    return request<AppVersion | null>("/app/version/latest");
  },

  syncGitHub: async () => {
    return request<{ message: string; version: any; prunedOldFiles: number }>(
      "/admin/versions/sync-github",
      { method: "POST" }
    );
  },

  getDownloadUrl: (apkUrl?: string) => {
    if (!apkUrl) return "";
    if (apkUrl.startsWith("http")) return apkUrl;
    // apkUrl is typically "/api/app/download/:file"
    const prefix = API_URL.endsWith("/api") ? API_URL.slice(0, -4) : API_URL;
    return `${prefix}${apkUrl.startsWith("/") ? "" : "/"}${apkUrl}`;
  },
};

export interface SyncStats {
  totalUsers: number;
  syncUsers: number;
  totalFavorites: number;
  totalPlaylists: number;
  totalPlaylistSongs: number;
  totalHistory: number;
  totalDownloads: number;
  totalScripts: number;
  totalPlayEvents: number;
  recentPlayEvents: number;
}

export interface SyncGlobalEvent {
  id: number;
  userId: number;
  username: string;
  eventId: string;
  deviceId: string;
  platform: string;
  songId: string;
  title: string;
  artist: string;
  listenedMs: number;
  listenedAtMs: number;
  updatedAtMs: number;
}

export const syncAdminApi = {
  getStats: async () => {
    return request<{ stats: SyncStats; events: SyncGlobalEvent[]; time: number }>("/admin/sync/stats");
  },
};

