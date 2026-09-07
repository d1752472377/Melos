import type { AuthProvider } from "@refinedev/core";
import { TOKEN_KEY, API_URL } from "./constants";

interface AuthResponse {
  code: number;
  msg?: string;
  data: {
    token: string;
    expiresAt: number;
    user: {
      id: number;
      username: string;
      nickname: string;
      avatar: string | null;
      role: string;
      created_at: number;
    };
  };
}

interface MeResponse {
  code: number;
  data: {
    id: number;
    username: string;
    nickname: string;
    avatar: string | null;
    role: string;
    created_at: number;
  };
}

function saveSession(token: string, user: unknown) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem("user", JSON.stringify(user));
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem("user");
}

export const authProvider: AuthProvider = {
  login: async (params: any) => {
    try {
      const username = params?.username || "";
      const password = params?.password || "";

      if (!username || !password) {
        return {
          success: false,
          error: { name: "LoginError", message: "请输入用户名和密码" },
        };
      }

      const res = await fetch(`${API_URL}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password, deviceName: "Admin" }),
      });

      const json: AuthResponse = await res.json();

      if (json.code === 200 && json.data?.token) {
        // 限制普通用户无法登录管理后台
        if (json.data.user?.role !== "admin") {
          try {
            await fetch(`${API_URL}/auth/logout`, {
              method: "POST",
              headers: { Authorization: `Bearer ${json.data.token}` },
            });
          } catch {}
          return {
            success: false,
            error: { name: "PermissionError", message: "普通用户无权登录管理后台" },
          };
        }

        saveSession(json.data.token, json.data.user);
        return { success: true, redirectTo: "/" };
      }

      const msg = json.code === 401 ? "用户名或密码错误" : json.msg || "登录失败，请重试";
      return {
        success: false,
        error: { name: "LoginError", message: msg },
      };
    } catch (error) {
      console.error("Login error:", error);
      return {
        success: false,
        error: { name: "NetworkError", message: "无法连接到服务器，请检查后端是否运行" },
      };
    }
  },

  register: async (params: any) => {
    try {
      const username = params?.username || "";
      const password = params?.password || "";
      const nickname = params?.nickname || "";

      if (!username || !password) {
        return {
          success: false,
          error: { name: "RegisterError", message: "请输入用户名和密码" },
        };
      }

      const res = await fetch(`${API_URL}/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username,
          password,
          nickname: nickname || undefined,
          deviceName: "Admin",
        }),
      });

      const json: AuthResponse = await res.json();

      if (json.code === 200 && json.data?.token) {
        saveSession(json.data.token, json.data.user);
        return { success: true, redirectTo: "/" };
      }

      const msg =
        json.code === 409 ? "用户名已存在" : json.msg || "注册失败，请重试";
      return {
        success: false,
        error: { name: "RegisterError", message: msg },
      };
    } catch (error) {
      console.error("Register error:", error);
      return {
        success: false,
        error: { name: "NetworkError", message: "无法连接到服务器，请检查后端是否运行" },
      };
    }
  },

  logout: async () => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      try {
        await fetch(`${API_URL}/auth/logout`, {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch {}
    }
    clearSession();
    return { success: true, redirectTo: "/login" };
  },

  check: async () => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      return { authenticated: false, redirectTo: "/login" };
    }
    try {
      const res = await fetch(`${API_URL}/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const json: MeResponse = await res.json();
        if (json.code === 200 && json.data) {
          // 仅允许管理员维持登录态
          if (json.data.role !== "admin") {
            clearSession();
            return {
              authenticated: false,
              redirectTo: "/login",
              error: { name: "PermissionError", message: "普通用户无权访问管理后台" },
            };
          }
          localStorage.setItem("user", JSON.stringify(json.data));
          return { authenticated: true };
        }
      }
    } catch {}
    clearSession();
    return { authenticated: false, redirectTo: "/login" };
  },

  getPermissions: async () => {
    const userStr = localStorage.getItem("user");
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        return user.role;
      } catch {}
    }
    return null;
  },

  getIdentity: async () => {
    const userStr = localStorage.getItem("user");
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        return {
          id: user.id,
          name: user.nickname || user.username,
          avatar: user.avatar || undefined,
          role: user.role,
        };
      } catch {}
    }
    return null;
  },

  onError: async (error) => {
    console.error(error);
    return { error };
  },
};