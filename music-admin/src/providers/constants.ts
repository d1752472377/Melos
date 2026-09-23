const getDefaultApiUrl = () => {
  if (typeof window !== "undefined" && window.location?.origin) {
    return `${window.location.origin}/api`;
  }
  return "/api";
};

export const API_URL = import.meta.env.VITE_API_URL || getDefaultApiUrl();
export const TOKEN_KEY = "refine-auth";