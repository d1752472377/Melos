import { useState, useEffect } from "react";
import { Card, Col, Row, Statistic } from "antd";
import { UserOutlined, DatabaseOutlined, SyncOutlined, PlayCircleOutlined } from "@ant-design/icons";
import { request } from "../../providers/api";

export const DashboardPage = () => {
  const [syncLoading, setSyncLoading] = useState(false);
  const [syncData, setSyncData] = useState<any>(null);

  useEffect(() => {
    let active = true;
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

  const userStr = localStorage.getItem("user");
  const user = userStr ? JSON.parse(userStr) : null;

  const dataCount = (syncData?.favorites?.length || 0) +
    (syncData?.history?.length || 0) +
    (syncData?.playlists?.length || 0) +
    (syncData?.playlistSongs?.length || 0);
  const eventCount = syncData?.playEvents?.length || 0;

  return (
    <div style={{ padding: 24 }}>
      <h1 style={{ marginBottom: 24 }}>📊 仪表盘</h1>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="当前用户"
              value={user?.nickname || user?.username || "未登录"}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="数据记录数"
              value={dataCount}
              prefix={<DatabaseOutlined />}
              loading={syncLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="同步事件数"
              value={eventCount}
              prefix={<SyncOutlined />}
              loading={syncLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="服务状态"
              value="运行中"
              prefix={<PlayCircleOutlined style={{ color: "#52c41a" }} />}
            />
          </Card>
        </Col>
      </Row>
      <Row style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="📋 最近同步数据预览">
            {syncLoading ? (
              <p>加载中...</p>
            ) : syncData ? (
              <pre style={{ maxHeight: 400, overflow: "auto", background: "#f5f5f5", padding: 12, borderRadius: 8 }}>
                {JSON.stringify({
                  version: syncData.version,
                  favoritesCount: syncData.favorites?.length || 0,
                  playlistsCount: syncData.playlists?.length || 0,
                  historyCount: syncData.history?.length || 0,
                  eventsCount: syncData.playEvents?.length || 0,
                  recentFavorites: syncData.favorites?.slice(0, 3),
                }, null, 2)}
              </pre>
            ) : (
              <p style={{ color: "#999" }}>暂无数据，请先同步。</p>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};
