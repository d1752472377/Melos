import { useState, useEffect } from "react";
import {
  Card,
  Col,
  Row,
  Statistic,
  Typography,
  Button,
  Tag,
  Space,
  Descriptions,
  theme,
} from "antd";
import {
  UserOutlined,
  DatabaseOutlined,
  TeamOutlined,
  AndroidOutlined,
  RocketOutlined,
  ArrowRightOutlined,
  CloudSyncOutlined,
  CustomerServiceOutlined,
  HeartOutlined,
  HistoryOutlined,
  UnorderedListOutlined,
  PlayCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { useGetIdentity } from "@refinedev/core";
import { request, versionApi, syncAdminApi, AppVersion, SyncStats } from "../../providers/api";

const { Title, Text } = Typography;

interface HealthData {
  ok: boolean;
  time: number;
  version: string;
}

interface IdentityUser {
  id?: number;
  name?: string;
  username?: string;
  nickname?: string;
  avatar?: string;
  role?: string;
}

export const DashboardPage = () => {
  const { data: identityUser } = useGetIdentity<IdentityUser>();
  const { token } = theme.useToken();
  const navigate = useNavigate();

  const [syncLoading, setSyncLoading] = useState(false);
  const [syncStats, setSyncStats] = useState<SyncStats | null>(null);
  const [totalUsers, setTotalUsers] = useState<number | null>(null);
  const [latestVersion, setLatestVersion] = useState<AppVersion | null>(null);

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

    // 3. 获取全站真实数据同步统计概览
    setSyncLoading(true);
    syncAdminApi
      .getStats()
      .then((res) => {
        if (active && res.code === 200 && res.data?.stats) {
          setSyncStats(res.data.stats);
          if (res.data.stats.totalUsers !== undefined) {
            setTotalUsers(res.data.stats.totalUsers);
          }
        }
      })
      .catch(() => {})
      .finally(() => {
        if (active) setSyncLoading(false);
      });

    // 4. 获取最新 APK 发布版本
    versionApi
      .getLatest()
      .then((res) => {
        if (active && res.code === 200 && res.data) {
          setLatestVersion(res.data);
        }
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, []);

  const localUserStr = localStorage.getItem("user");
  const localUser = localUserStr ? JSON.parse(localUserStr) : null;
  const adminName =
    identityUser?.nickname ||
    identityUser?.username ||
    identityUser?.name ||
    localUser?.nickname ||
    localUser?.username ||
    localUser?.name ||
    "管理员";

  const totalUsersCount = totalUsers !== null ? totalUsers : (syncStats?.totalUsers ?? "-");
  const syncUsersCount = syncStats ? syncStats.syncUsers : "-";
  const favoritesCount = syncStats?.totalFavorites || 0;
  const playlistsCount = syncStats?.totalPlaylists || 0;
  const playlistSongsCount = syncStats?.totalPlaylistSongs || 0;
  const historyCount = syncStats?.totalHistory || 0;
  const playEventsCount = syncStats?.totalPlayEvents || 0;
  const recentEventsCount = syncStats?.recentPlayEvents || 0;
  const downloadsCount = syncStats?.totalDownloads || 0;
  const scriptsCount = syncStats?.totalScripts || 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
      {/* 顶部简明标题与操作 */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 16,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, fontWeight: 700, color: "#0f172a" }}>
            系统仪表盘
          </Title>
          <Text style={{ color: "#64748b", fontSize: 14 }}>
            查看平台核心数据、同步状态与客户端版本概况
          </Text>
        </div>

        <Space size={10}>
          <Button
            type="primary"
            icon={<AndroidOutlined />}
            onClick={() => navigate("/versions")}
            style={{ background: "#4f46e5", borderColor: "#4f46e5" }}
          >
            版本发布管理
          </Button>
          <Button icon={<TeamOutlined />} onClick={() => navigate("/users")}>
            用户管理
          </Button>
        </Space>
      </div>

      {/* 4 个真实指标卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>系统总用户数</span>}
              value={totalUsersCount}
              prefix={<TeamOutlined style={{ color: "#4f46e5", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>多端同步活跃用户</span>}
              value={syncUsersCount}
              prefix={<CloudSyncOutlined style={{ color: "#7c3aed", marginRight: 8 }} />}
              loading={syncLoading}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>全站云端歌曲收藏</span>}
              value={favoritesCount}
              prefix={<HeartOutlined style={{ color: "#059669", marginRight: 8 }} />}
              loading={syncLoading}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <div style={{ color: "#64748b", fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  服务健康状态
                </div>
                <div style={{ fontSize: 20, fontWeight: "bold", display: "flex", alignItems: "center", gap: 8, height: 32 }}>
                  {healthLoading ? (
                    <SyncOutlined spin style={{ fontSize: 18, color: token.colorPrimary }} />
                  ) : healthError ? (
                    <>
                      <CloseCircleOutlined style={{ color: "#ef4444" }} />
                      <span style={{ color: "#ef4444", fontSize: 18 }}>异常 / 离线</span>
                    </>
                  ) : (
                    <>
                      <PlayCircleOutlined style={{ color: "#10b981" }} />
                      <span style={{ color: "#10b981", fontSize: 18 }}>运行正常</span>
                    </>
                  )}
                </div>
              </div>
              {!healthError && healthData && (
                <div style={{ textAlign: "right" }}>
                  <Tag color="blue">v{healthData.version}</Tag>
                  {healthLatency !== null && (
                    <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 4 }}>
                      {healthLatency} ms
                    </div>
                  )}
                </div>
              )}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 同步数据统计与客户端卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            bordered
            title={
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <CloudSyncOutlined style={{ color: "#4f46e5" }} />
                <span>全站云端数据同步概况</span>
              </div>
            }
            extra={
              <Button type="link" size="small" onClick={() => navigate("/sync")}>
                进入同步监控 <ArrowRightOutlined />
              </Button>
            }
            style={{ borderRadius: 10, borderColor: "#e2e8f0" }}
          >
            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small" style={{ background: "#f8fafc", borderRadius: 8 }}>
                  <Statistic
                    title="收藏曲目"
                    value={favoritesCount}
                    prefix={<HeartOutlined style={{ color: "#ef4444" }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={{ background: "#f8fafc", borderRadius: 8 }}>
                  <Statistic
                    title="云端歌单"
                    value={playlistsCount}
                    prefix={<UnorderedListOutlined style={{ color: "#3b82f6" }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={{ background: "#f8fafc", borderRadius: 8 }}>
                  <Statistic
                    title="播放历史"
                    value={historyCount}
                    prefix={<HistoryOutlined style={{ color: "#8b5cf6" }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={{ background: "#f8fafc", borderRadius: 8 }}>
                  <Statistic
                    title="听歌总打点"
                    value={playEventsCount}
                    prefix={<CustomerServiceOutlined style={{ color: "#10b981" }} />}
                  />
                </Card>
              </Col>
            </Row>

            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="24小时内听歌打点">
                <Tag color="cyan">{recentEventsCount} 次</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="歌单内歌曲总数">
                {playlistSongsCount} 首
              </Descriptions.Item>
              <Descriptions.Item label="离线下载记录总数">
                {downloadsCount} 条
              </Descriptions.Item>
              <Descriptions.Item label="自定义音源脚本">
                {scriptsCount} 个
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            bordered
            title={
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <AndroidOutlined style={{ color: "#10b981" }} />
                <span>客户端版本信息</span>
              </div>
            }
            style={{ borderRadius: 10, borderColor: "#e2e8f0" }}
          >
            {latestVersion ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Text type="secondary">当前正式版本</Text>
                  <Tag color="success" style={{ fontWeight: 600, fontSize: 13 }}>
                    {latestVersion.version_name || latestVersion.versionName}
                  </Tag>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Text type="secondary">构建号 (Version Code)</Text>
                  <Text code style={{ fontSize: 13 }}>
                    {latestVersion.version_code || latestVersion.versionCode}
                  </Text>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Text type="secondary">更新策略</Text>
                  <Tag color={latestVersion.force_update || latestVersion.forceUpdate ? "error" : "default"}>
                    {latestVersion.force_update || latestVersion.forceUpdate ? "强制升级" : "可选更新"}
                  </Tag>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <Text type="secondary">发布时间</Text>
                  <Text style={{ fontSize: 12 }}>
                    {latestVersion.created_at || latestVersion.createdAt || "-"}
                  </Text>
                </div>
                <Button
                  type="primary"
                  block
                  icon={<RocketOutlined />}
                  onClick={() => navigate("/versions")}
                  style={{ background: "#4f46e5", borderColor: "#4f46e5", marginTop: 6 }}
                >
                  版本发布与管理
                </Button>
              </div>
            ) : (
              <div style={{ textAlign: "center", padding: "24px 0" }}>
                <p style={{ color: "#94a3b8", marginBottom: 16 }}>暂无已启用的客户端版本</p>
                <Button type="primary" onClick={() => navigate("/versions")} style={{ background: "#4f46e5" }}>
                  去发布第一个版本
                </Button>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};
