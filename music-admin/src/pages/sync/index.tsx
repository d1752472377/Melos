import { useState, useEffect } from "react";
import { Card, Table, Tag, Button, Statistic, Row, Col, Typography, message } from "antd";
import { SyncOutlined, ReloadOutlined, CheckCircleOutlined } from "@ant-design/icons";
import { request } from "../../providers/api";

const { Title, Paragraph } = Typography;

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

interface PlayEventItem {
  id?: number | string;
  eventId?: number | string;
  songId?: string;
  platform?: string;
  title?: string;
  updatedAtMs?: number;
  listenedAtMs?: number;
}

export const SyncPage = () => {
  const [loading, setLoading] = useState(false);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [syncLogs, setSyncLogs] = useState<any[]>([]);

  const fetchSyncData = async () => {
    setLoading(true);
    try {
      const res = await request<any>("/sync/pull?since=0&eventSince=0");
      if (res.code === 200 && res.data) {
        const d = res.data;
        const events: PlayEventItem[] = d.playEvents || [];
        setSyncStatus({
          version: d.version || 0,
          eventCount: events.length,
          lastSync: new Date().toLocaleString(),
          collections: {
            favorites: d.favorites?.length || 0,
            history: d.history?.length || 0,
            playlists: d.playlists?.length || 0,
            playlistSongs: d.playlistSongs?.length || 0,
          },
        });
        setSyncLogs(
          events.slice(0, 50).map((e: PlayEventItem, index: number) => ({
            id: e.id || e.eventId || `event-${index}`,
            collection: "play_events",
            operation: "append",
            recordId: `${e.platform || "event"}:${e.songId || e.id || ""} (${e.title || "-"})`,
            timestamp: e.updatedAtMs || e.listenedAtMs || Date.now(),
          }))
        );
        message.success("同步数据拉取成功");
      } else {
        message.error(res.msg || "拉取同步数据失败");
      }
    } catch (error: any) {
      message.error(error.message || "请求失败，请检查后端服务");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSyncData();
  }, []);

  const columns = [
    { title: "ID", dataIndex: "id", key: "id", width: 120 },
    {
      title: "集合",
      dataIndex: "collection",
      key: "collection",
      width: 130,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: "操作类型",
      dataIndex: "operation",
      key: "operation",
      width: 100,
      render: (v: string) => <Tag color={v === "delete" ? "red" : "green"}>{v}</Tag>,
    },
    { title: "记录详情", dataIndex: "recordId", key: "recordId" },
    {
      title: "时间戳",
      dataIndex: "timestamp",
      key: "timestamp",
      width: 200,
      render: (v: number) => (v ? new Date(v).toLocaleString() : "-"),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            🔄 数据同步监控与调试
          </Title>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            查看当前登录管理员账号在云端的增量同步快照与最近同步日志
          </Paragraph>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={fetchSyncData} loading={loading}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="同步数据版本"
              value={syncStatus?.version || 0}
              prefix={<SyncOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="同步事件数"
              value={syncStatus?.eventCount || 0}
              prefix={<CheckCircleOutlined style={{ color: "#52c41a" }} />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="收藏歌曲"
              value={syncStatus?.collections.favorites || 0}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="同步歌单"
              value={syncStatus?.collections.playlists || 0}
            />
          </Card>
        </Col>
      </Row>

      <Card title="📋 最近同步事件列表" styles={{ body: { padding: 0 } }}>
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
