import React, { Suspense } from "react";
import { Refine, Authenticated } from "@refinedev/core";
import { DevtoolsPanel, DevtoolsProvider } from "@refinedev/devtools";
import { RefineKbar, RefineKbarProvider } from "@refinedev/kbar";

import {
  ErrorComponent,
  useNotificationProvider,
  ThemedLayout,
} from "@refinedev/antd";
import "@refinedev/antd/dist/reset.css";

import { App as AntdApp, ConfigProvider, Spin } from "antd";
import { BrowserRouter, Route, Routes, Outlet } from "react-router-dom";
import routerProvider, {
  NavigateToResource,
  CatchAllNavigate,
  UnsavedChangesNotifier,
  DocumentTitleHandler,
} from "@refinedev/react-router";
import {
  DashboardOutlined,
  SyncOutlined,
  TeamOutlined,
  AndroidOutlined,
  DownloadOutlined,
  FileTextOutlined,
  MessageOutlined,
} from "@ant-design/icons";

import { dataProvider } from "./providers/data";
import { ColorModeContextProvider } from "./contexts/color-mode";
import { CustomSider } from "./components";
import { Header } from "./components/header";
import { authProvider } from "./providers/auth";

// 路由级代码分割（Lazy Loading），大幅降低首屏资源体积
const DashboardPage = React.lazy(() =>
  import("./pages/dashboard").then((m) => ({ default: m.DashboardPage }))
);
const UsersPage = React.lazy(() =>
  import("./pages/users").then((m) => ({ default: m.UsersPage }))
);
const VersionsPage = React.lazy(() =>
  import("./pages/versions").then((m) => ({ default: m.VersionsPage }))
);
const SyncPage = React.lazy(() =>
  import("./pages/sync").then((m) => ({ default: m.SyncPage }))
);
const ProfilePage = React.lazy(() =>
  import("./pages/profile").then((m) => ({ default: m.ProfilePage }))
);
const DownloadPage = React.lazy(() =>
  import("./pages/download").then((m) => ({ default: m.DownloadPage }))
);
const LogsPage = React.lazy(() =>
  import("./pages/logs").then((m) => ({ default: m.LogsPage }))
);
const FeedbackPage = React.lazy(() =>
  import("./pages/feedback").then((m) => ({ default: m.FeedbackPage }))
);
const Login = React.lazy(() =>
  import("./pages/login").then((m) => ({ default: m.Login }))
);

const isDev = import.meta.env.DEV;

const PageLoading = () => (
  <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 300 }}>
    <Spin size="large" />
  </div>
);

function AppContent() {
  return (
    <Refine
      dataProvider={dataProvider}
      notificationProvider={useNotificationProvider}
      routerProvider={routerProvider}
      authProvider={authProvider}
      resources={[
        {
          name: "dashboard",
          list: "/",
          meta: { label: "仪表盘", icon: <DashboardOutlined /> },
        },
        {
          name: "users",
          list: "/users",
          meta: { label: "用户管理", icon: <TeamOutlined /> },
        },
        {
          name: "versions",
          list: "/versions",
          meta: { label: "版本发布", icon: <AndroidOutlined /> },
        },
        {
          name: "sync",
          list: "/sync",
          meta: { label: "同步监控", icon: <SyncOutlined /> },
        },
        {
          name: "logs",
          list: "/logs",
          meta: { label: "客户端日志", icon: <FileTextOutlined /> },
        },
        {
          name: "feedback",
          list: "/feedback",
          meta: { label: "问题反馈", icon: <MessageOutlined /> },
        },
        {
          // 公开下载落地页（无需登录，手机/PC 都能开）。
          // 之前它只存在于路由里、侧栏没有任何入口，等于「有页面但找不到」——
          // 这里挂成菜单项，点开即离开控制台布局，回到可分享的下载页。
          name: "download",
          list: "/download",
          meta: { label: "下载页", icon: <DownloadOutlined /> },
        },
      ]}
      options={{
        syncWithLocation: true,
        warnWhenUnsavedChanges: true,
        projectId: "IU5L8R-BIV7QJ-UXD0ax",
      }}
    >
      <Suspense fallback={<PageLoading />}>
        <Routes>
          <Route
            element={
              <Authenticated key="authenticated-routes" fallback={<CatchAllNavigate to="/login" />}>
                <ThemedLayout Header={Header} Sider={CustomSider}>
                  <Outlet />
                </ThemedLayout>
              </Authenticated>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="/users" element={<UsersPage />} />
            <Route path="/versions" element={<VersionsPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/sync" element={<SyncPage />} />
            <Route path="/logs" element={<LogsPage />} />
            <Route path="/feedback" element={<FeedbackPage />} />
          </Route>

          {/* 公开下载落地页（适配手机与PC） */}
          <Route path="/download" element={<DownloadPage />} />

          <Route
            element={
              <Authenticated key="authenticated-auth" fallback={<Outlet />}>
                <NavigateToResource resource="dashboard" />
              </Authenticated>
            }
          >
            <Route path="/login" element={<Login />} />
          </Route>

          <Route
            element={
              <Authenticated key="authenticated-error">
                <ThemedLayout Header={Header} Sider={CustomSider}>
                  <Outlet />
                </ThemedLayout>
              </Authenticated>
            }
          >
            <Route path="*" element={<ErrorComponent />} />
          </Route>
        </Routes>
      </Suspense>

      <RefineKbar />
      <UnsavedChangesNotifier />
      <DocumentTitleHandler />
    </Refine>
  );
}

function App() {
  return (
    <BrowserRouter>
      <RefineKbarProvider>
        <ColorModeContextProvider>
          <ConfigProvider
            theme={{
              token: {
                colorPrimary: "#4f46e5",
                borderRadius: 8,
                fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
              },
            }}
          >
            <AntdApp>
              {isDev ? (
                <DevtoolsProvider>
                  <AppContent />
                  <DevtoolsPanel />
                </DevtoolsProvider>
              ) : (
                <AppContent />
              )}
            </AntdApp>
          </ConfigProvider>
        </ColorModeContextProvider>
      </RefineKbarProvider>
    </BrowserRouter>
  );
}

export default App;
