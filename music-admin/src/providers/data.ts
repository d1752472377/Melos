import { createSimpleRestDataProvider } from "@refinedev/rest/simple-rest";
import type { DataProvider } from "@refinedev/core";
import { API_URL, TOKEN_KEY } from "./constants";

const { dataProvider: rawProvider, kyInstance } = createSimpleRestDataProvider({
  apiURL: API_URL,
  kyOptions: {
    hooks: {
      beforeRequest: [
        (request) => {
          const token = localStorage.getItem(TOKEN_KEY);
          if (token && !request.headers.has("Authorization")) {
            request.headers.set("Authorization", `Bearer ${token}`);
          }
        },
      ],
    },
  },
});

// 适配 Melos 后端的业务包裹响应 { code: 200, data: { items: [...], total: ... } }
const adaptData = (raw: any) => {
  if (raw && typeof raw === "object" && !Array.isArray(raw)) {
    if ("data" in raw) {
      const inner = raw.data;
      if (Array.isArray(inner)) return inner;
      if (inner && typeof inner === "object" && Array.isArray(inner.items)) {
        return inner.items;
      }
      return inner;
    }
  }
  return raw;
};

export const dataProvider: DataProvider = {
  ...rawProvider,
  getList: async (params) => {
    const res = await rawProvider.getList(params);
    let items = res.data;
    let total = res.total;
    if (items && typeof items === "object" && !Array.isArray(items)) {
      const d = (items as any).data || items;
      if (Array.isArray(d)) {
        items = d;
      } else if (d && Array.isArray(d.items)) {
        items = d.items;
        if (typeof d.total === "number") total = d.total;
      }
    }
    return { data: items as any, total };
  },
  getOne: async (params) => {
    const res = await rawProvider.getOne(params);
    return { data: adaptData(res.data) };
  },
  create: async (params) => {
    const res = await rawProvider.create(params);
    return { data: adaptData(res.data) };
  },
  update: async (params) => {
    const res = await rawProvider.update(params);
    return { data: adaptData(res.data) };
  },
  deleteOne: async (params) => {
    const res = await rawProvider.deleteOne(params);
    return { data: adaptData(res.data) };
  },
};

export { kyInstance };
