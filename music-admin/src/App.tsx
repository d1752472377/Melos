import React, { Suspense } from "react";
import { Refine, Authenticated } from "@refinedev/core";
import { DevtoolsPanel, DevtoolsProvider } from "@refinedev/devtools";
import { RefineKbar, RefineKbarProvider } from "@refinedev/kbar";

import {
  ErrorComponent,
  useNotificationProvider,
  ThemedLayout,
  ThemedSider,
} from "@refinedev/antd";
import "@refinedev/antd/dist/reset.css";

import { App as AntdApp, Spin } from "antd";
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
  CloudUploadOutlined,
} from "@ant-design/icons";

import { dataProvider } from "./providers/data";
import { ColorModeContextProvider } from "./contexts/color-mode";
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
          meta: { label: "版本发布", icon: <CloudUploadOutlined /> },
        },
        {
          name: "sync",
          list: "/sync",
          meta: { label: "同步监控", icon: <SyncOutlined /> },
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
                <ThemedLayout Header={Header} Sider={ThemedSider}>
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
          </Route>

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
                <ThemedLayout Header={Header} Sider={ThemedSider}>
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
        </ColorModeContextProvider>
      </RefineKbarProvider>
    </BrowserRouter>
  );
}

export default App;
