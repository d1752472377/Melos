import { useState, useEffect } from "react";
import { Card, Col, Row, Statistic, Typography, Button, Tag, Space, Descriptions } from "antd";
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
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { request, versionApi, AppVersion } from "../../providers/api";

const { Title, Text } = Typography;

export const DashboardPage = () => {
  const [syncLoading, setSyncLoading] = useState(false);
  const [syncData, setSyncData] = useState<any>(null);
  const [totalUsers, setTotalUsers] = useState<number | null>(null);
  const [latestVersion, setLatestVersion] = useState<AppVersion | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    setSyncLoading(true);

    // 获取系统总用户数
    request<{ items: any[]; total: number }>("/admin/users?limit=1")
      .then((res) => {
        if (active && res.code === 200 && res.data) {
          setTotalUsers(res.data.total ?? 0);
        }
      })
      .catch(() => {});

    // 获取当前管理员自身同步数据
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

    // 获取最新 APK 发布版本
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

  const userStr = localStorage.getItem("user");
  const user = userStr ? JSON.parse(userStr) : null;

  const favoritesCount = syncData?.favorites?.length || 0;
  const historyCount = syncData?.history?.length || 0;
  const playlistsCount = syncData?.playlists?.length || 0;
  const eventsCount = syncData?.playEvents?.length || 0;
  const totalSyncRecords = favoritesCount + historyCount + playlistsCount + (syncData?.playlistSongs?.length || 0);

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
              value={totalUsers !== null ? totalUsers : "-"}
              prefix={<TeamOutlined style={{ color: "#4f46e5", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>当前管理员</span>}
              value={user?.nickname || user?.username || "管理员"}
              prefix={<UserOutlined style={{ color: "#7c3aed", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>当前账号同步记录数</span>}
              value={totalSyncRecords}
              prefix={<DatabaseOutlined style={{ color: "#059669", marginRight: 8 }} />}
              loading={syncLoading}
              valueStyle={{ fontWeight: 700, color: "#0f172a" }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ borderRadius: 10, borderColor: "#e2e8f0" }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: "#64748b" }}>最新发布版本</span>}
              value={latestVersion ? latestVersion.version_name || latestVersion.versionName : "暂无发布"}
              prefix={<RocketOutlined style={{ color: "#e11d48", marginRight: 8 }} />}
              valueStyle={{
                fontWeight: 700,
                color: "#0f172a",
                fontSize: latestVersion ? 22 : 18,
              }}
            />
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
                <span>管理员账号数据同步概况</span>
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
                    title="收藏歌曲"
                    value={favoritesCount}
                    prefix={<HeartOutlined style={{ color: "#ef4444" }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small" style={{ background: "#f8fafc", borderRadius: 8 }}>
                  <Statistic
                    title="播放列表"
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
                    title="播放事件"
                    value={eventsCount}
                    prefix={<CustomerServiceOutlined style={{ color: "#10b981" }} />}
                  />
                </Card>
              </Col>
            </Row>

            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="同步版本号">
                {syncData?.version ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label="歌单内歌曲总数">
                {syncData?.playlistSongs?.length || 0}
              </Descriptions.Item>
              <Descriptions.Item label="最近收藏示例" span={2}>
                {syncData?.favorites && syncData.favorites.length > 0 ? (
                  <span>
                    {syncData.favorites.slice(0, 3).map((f: any) => f.title || f.name || f.songId || f.id).join("、")}
                    {syncData.favorites.length > 3 ? " 等..." : ""}
                  </span>
                ) : (
                  <Text type="secondary">暂无收藏记录</Text>
                )}
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
