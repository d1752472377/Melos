import { Refine, Authenticated } from "@refinedev/core";
import { DevtoolsPanel, DevtoolsProvider } from "@refinedev/devtools";
import { RefineKbar, RefineKbarProvider } from "@refinedev/kbar";

import {
  ErrorComponent,
  useNotificationProvider,
  ThemedLayout,
  ThemedSider,
  AuthPage,
} from "@refinedev/antd";
import "@refinedev/antd/dist/reset.css";

import { App as AntdApp, ConfigProvider } from "antd";
import { BrowserRouter, Route, Routes, Outlet } from "react-router-dom";
import routerProvider, {
  NavigateToResource,
  CatchAllNavigate,
  UnsavedChangesNotifier,
  DocumentTitleHandler,
} from "@refinedev/react-router";
import { dataProvider } from "./providers/data";
import { ColorModeContextProvider } from "./contexts/color-mode";
import { Header } from "./components/header";
import { authProvider } from "./providers/auth";
import { DashboardPage } from "./pages/dashboard";
import { Register } from "./pages/register";
import { Login } from "./pages/login";
import { SyncPage } from "./pages/sync";

import { DashboardOutlined, SyncOutlined } from "@ant-design/icons";

function App() {
  return (
    <BrowserRouter>
      <RefineKbarProvider>
        <ColorModeContextProvider>
          <ConfigProvider theme={{ token: { colorPrimary: "#1677ff" } }}>
            <AntdApp>
              <DevtoolsProvider>
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
                  <Routes>
                    <Route
                      element={
                        <Authenticated fallback={<CatchAllNavigate to="/login" />}>
                          <ThemedLayout Header={Header} Sider={ThemedSider}>
                            <Outlet />
                          </ThemedLayout>
                        </Authenticated>
                      }
                    >
                      <Route index element={<DashboardPage />} />
                      <Route path="/sync" element={<SyncPage />} />
                    </Route>

                    <Route
                      element={
                        <Authenticated fallback={<Outlet />}>
                          <NavigateToResource resource="dashboard" />
                        </Authenticated>
                      }
                    >
                      <Route path="/login" element={<Login />} />
                      <Route path="/register" element={<Register />} />
                      <Route path="/forgot-password" element={<AuthPage type="forgotPassword" />} />
                    </Route>

                    <Route
                      element={
                        <Authenticated>
                          <ThemedLayout Header={Header} Sider={ThemedSider}>
                            <Outlet />
                          </ThemedLayout>
                        </Authenticated>
                      }
                    >
                      <Route path="*" element={<ErrorComponent />} />
                    </Route>
                  </Routes>

                  <RefineKbar />
                  <UnsavedChangesNotifier />
                  <DocumentTitleHandler />
                </Refine>
                <DevtoolsPanel />
              </DevtoolsProvider>
            </AntdApp>
          </ConfigProvider>
        </ColorModeContextProvider>
      </RefineKbarProvider>
    </BrowserRouter>
  );
}

export default App;
