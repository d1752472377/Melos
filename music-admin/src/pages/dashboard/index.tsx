import { useState, useEffect } from "react";
import { Card, Col, Row, Statistic, Tag, theme } from "antd";
import {
  UserOutlined,
  DatabaseOutlined,
  PlayCircleOutlined,
  TeamOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { useGetIdentity } from "@refinedev/core";
import { request } from "../../providers/api";

interface HealthData {
  ok: boolean;
  time: number;
  version: string;
}

interface IdentityUser {
  id: number;
  name: string;
  avatar?: string;
  role?: string;
}

export const DashboardPage = () => {
  const { data: user } = useGetIdentity<IdentityUser>();
  const { token } = theme.useToken();

  const [syncLoading, setSyncLoading] = useState(false);
  const [syncData, setSyncData] = useState<any>(null);
  const [totalUsers, setTotalUsers] = useState<number | null>(null);

  // 后端健康状态探针
  const [healthLoading, setHealthLoading] = useState(false);
  const [healthData, setHealthData] = useState<HealthData | null>(null);
  const [healthLatency, setHealthLatency] = useState<number | null>(null);
  const [healthError, setHealthError] = useState(false);

  useEffect(() => {
    let active = true;

    // 1. 获取系统总用户数
    request<{ items: any[]; total: number }>("/admin/users?limit=1")
      .then((res) => {
        if (active && res.code === 200 && res.data) {
          setTotalUsers(res.data.total ?? 0);
        }
      })
      .catch(() => {});

    // 2. 探针检测后端服务健康度
    const checkHealth = async () => {
      setHealthLoading(true);
      const startTime = performance.now();
      try {
        const res = await request<HealthData>("/health");
        const latency = Math.round(performance.now() - startTime);
        if (active) {
          if (res.code === 200 && res.data?.ok) {
            setHealthData(res.data);
            setHealthLatency(latency);
            setHealthError(false);
          } else {
            setHealthError(true);
          }
        }
      } catch {
        if (active) setHealthError(true);
      } finally {
        if (active) setHealthLoading(false);
      }
    };
    checkHealth();

    // 3. 获取当前管理员账号个人同步数据概览
    setSyncLoading(true);
    request("/sync/pull?since=0&eventSince=0")
      .then((res) => {
        if (active && res.code === 200 && res.data) {
          setSyncData(res.data);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (active) setSyncLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const dataCount =
    (syncData?.favorites?.length || 0) +
    (syncData?.history?.length || 0) +
    (syncData?.playlists?.length || 0) +
    (syncData?.playlistSongs?.length || 0);

  return (
    <div style={{ padding: 24 }}>
      <h1 style={{ marginBottom: 24 }}>📊 仪表盘</h1>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="系统总用户数"
              value={totalUsers !== null ? totalUsers : "-"}
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="当前管理员"
              value={user?.name || "已登录"}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="当前账号同步记录数"
              value={dataCount}
              prefix={<DatabaseOutlined />}
              loading={syncLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <div style={{ color: token.colorTextSecondary, fontSize: 14, marginBottom: 4 }}>服务健康状态</div>
                <div style={{ fontSize: 24, fontWeight: "bold", display: "flex", alignItems: "center", gap: 8 }}>
                  {healthLoading ? (
                    <SyncOutlined spin style={{ fontSize: 20, color: token.colorPrimary }} />
                  ) : healthError ? (
                    <>
                      <CloseCircleOutlined style={{ color: "#ff4d4f" }} />
                      <span style={{ color: "#ff4d4f", fontSize: 20 }}>异常 / 离线</span>
                    </>
                  ) : (
                    <>
                      <PlayCircleOutlined style={{ color: "#52c41a" }} />
                      <span style={{ color: "#52c41a", fontSize: 20 }}>运行正常</span>
                    </>
                  )}
                </div>
              </div>
              {!healthError && healthData && (
                <div style={{ textAlign: "right" }}>
                  <Tag color="blue">v{healthData.version}</Tag>
                  {healthLatency !== null && (
                    <div style={{ fontSize: 12, color: token.colorTextSecondary, marginTop: 4 }}>
                      {healthLatency} ms
                    </div>
                  )}
                </div>
              )}
            </div>
          </Card>
        </Col>
      </Row>

      <Row style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="📋 当前管理员账号同步数据预览（/api/sync/pull）">
            {syncLoading ? (
              <p>加载中...</p>
            ) : syncData ? (
              <pre
                style={{
                  maxHeight: 400,
                  overflow: "auto",
                  backgroundColor: token.colorFillAlter,
                  color: token.colorText,
                  border: `1px solid ${token.colorBorderSecondary}`,
                  padding: 14,
                  borderRadius: token.borderRadiusLG,
                  fontFamily: "Consolas, Menlo, Monaco, monospace",
                  fontSize: 13,
                }}
              >
                {JSON.stringify(
                  {
                    version: syncData.version,
                    favoritesCount: syncData.favorites?.length || 0,
                    playlistsCount: syncData.playlists?.length || 0,
                    historyCount: syncData.history?.length || 0,
                    eventsCount: syncData.playEvents?.length || 0,
                    recentFavorites: syncData.favorites?.slice(0, 3),
                  },
                  null,
                  2
                )}
              </pre>
            ) : (
              <p style={{ color: token.colorTextSecondary }}>暂无数据，请先同步。</p>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};
