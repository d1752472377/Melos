import { useState, useEffect } from "react";
import { Card, Table, Tag, Button, Space, message, Statistic, Row, Col } from "antd";
import { SyncOutlined, ReloadOutlined, CheckCircleOutlined, ClockCircleOutlined } from "@ant-design/icons";
import { useApiUrl } from "@refinedev/core";

interface SyncStatus {
  version: number;
  eventCount: number;
  lastSync: string;
  collections: {
    favorites: number;
    history: number;
    playlists: number;
    playlistSongs: number;
  };
}

export const SyncPage = () => {
  const apiUrl = useApiUrl();
  const [loading, setLoading] = useState(false);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [syncLogs, setSyncLogs] = useState<any[]>([]);

  const fetchSyncData = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem("refine-auth");
      const res = await fetch(`${apiUrl}/sync/pull?since=0&eventSince=0`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const json = await res.json();
      if (json.code === 200 && json.data) {
        const data = json.data.data || [];
        const events = json.data.events || [];
        setSyncStatus({
          version: json.data.version || 0,
          eventCount: events.length,
          lastSync: new Date().toLocaleString(),
          collections: {
            favorites: data.filter((d: any) => d.collection === "favorites").length,
            history: data.filter((d: any) => d.collection === "history").length,
            playlists: data.filter((d: any) => d.collection === "playlists").length,
            playlistSongs: data.filter((d: any) => d.collection === "playlistSongs").length,
          },
        });
        setSyncLogs(events.slice(0, 20));
        message.success("同步数据拉取成功");
      } else {
        message.error("拉取同步数据失败");
      }
    } catch (error) {
      message.error("请求失败，请检查后端服务");
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchSyncData();
  }, []);

  const columns = [
    { title: "ID", dataIndex: "id", key: "id", width: 80 },
    { title: "集合", dataIndex: "collection", key: "collection", 
      render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: "操作类型", dataIndex: "operation", key: "operation",
      render: (v: string) => <Tag color={v === "delete" ? "red" : "green"}>{v}</Tag> },
    { title: "记录 ID", dataIndex: "recordId", key: "recordId" },
    { title: "时间戳", dataIndex: "timestamp", key: "timestamp",
      render: (v: number) => new Date(v).toLocaleString() },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <h1>🔄 数据同步监控</h1>
        <Button type="primary" icon={<ReloadOutlined />} onClick={fetchSyncData} loading={loading}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="当前版本"
              value={syncStatus?.version || 0}
              prefix={<SyncOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="事件数"
              value={syncStatus?.eventCount || 0}
              prefix={<CheckCircleOutlined style={{ color: "#52c41a" }} />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="收藏"
              value={syncStatus?.collections.favorites || 0}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="歌单"
              value={syncStatus?.collections.playlists || 0}
            />
          </Card>
        </Col>
      </Row>

      <Card title="📋 同步日志">
        <Table
          dataSource={syncLogs}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </div>
  );
};
