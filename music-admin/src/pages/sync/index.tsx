import React, { useState, useEffect } from "react";
import { Card, Table, Tag, Button, Statistic, Row, Col, Typography, message, Space } from "antd";
import {
  SyncOutlined,
  ReloadOutlined,
  HeartOutlined,
  UnorderedListOutlined,
  CustomerServiceOutlined,
  TeamOutlined,
  MobileOutlined,
} from "@ant-design/icons";
import { syncAdminApi, SyncStats, SyncGlobalEvent } from "../../providers/api";

const { Title, Paragraph, Text } = Typography;

export const SyncPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState<SyncStats | null>(null);
  const [events, setEvents] = useState<SyncGlobalEvent[]>([]);
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string>("-");

  const fetchSyncData = async () => {
    setLoading(true);
    try {
      const res = await syncAdminApi.getStats();
      if (res.code === 200 && res.data) {
        setStats(res.data.stats);
        setEvents(res.data.events || []);
        setLastRefreshedAt(new Date(res.data.time || Date.now()).toLocaleTimeString());
        message.success("已刷新全站同步状态与审计日志");
      } else {
        message.error(res.msg || "获取全站同步监控数据失败");
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

  const formatDuration = (ms: number) => {
    if (!ms || ms <= 0) return "-";
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}分${s < 10 ? "0" : ""}${s}秒`;
  };

  const columns = [
    {
      title: "流水号",
      dataIndex: "id",
      key: "id",
      width: 90,
      render: (id: number) => <Text code>{id}</Text>,
    },
    {
      title: "同步用户",
      dataIndex: "username",
      key: "username",
      width: 150,
      render: (name: string, r: SyncGlobalEvent) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>UID: {r.userId}</Text>
        </Space>
      ),
    },
    {
      title: "设备标识",
      dataIndex: "deviceId",
      key: "deviceId",
      width: 140,
      render: (dev: string) =>
        dev ? (
          <Tag icon={<MobileOutlined />} color="default">
            {dev.length > 12 ? `${dev.slice(0, 10)}...` : dev}
          </Tag>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: "平台",
      dataIndex: "platform",
      key: "platform",
      width: 100,
      render: (p: string) => {
        let color = "blue";
        if (p === "netease") color = "red";
        else if (p === "qq") color = "green";
        else if (p === "bilibili") color = "magenta";
        return <Tag color={color}>{p || "通用"}</Tag>;
      },
    },
    {
      title: "歌曲 / 歌手",
      key: "songInfo",
      render: (_: any, r: SyncGlobalEvent) => (
        <div>
          <div style={{ fontWeight: 600, color: "#0f172a" }}>{r.title || "(未知曲名)"}</div>
          <div style={{ fontSize: 12, color: "#64748b" }}>{r.artist || "(未知歌手)"}</div>
        </div>
      ),
    },
    {
      title: "听歌时长",
      dataIndex: "listenedMs",
      key: "listenedMs",
      width: 120,
      render: (ms: number) => <Tag color="purple">{formatDuration(ms)}</Tag>,
    },
    {
      title: "同步事件时间",
      key: "timestamp",
      width: 180,
      render: (_: any, r: SyncGlobalEvent) => {
        const t = r.listenedAtMs || r.updatedAtMs;
        return t ? <span style={{ fontSize: 12, color: "#64748b" }}>{new Date(t).toLocaleString()}</span> : "-";
      },
    },
  ];

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 24 }}>
      {/* 头部导航与操作 */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 16 }}>
        <div>
          <Title level={3} style={{ margin: 0, fontWeight: 700 }}>
            🔄 全站数据同步监控与审计
          </Title>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            监控全平台用户的多端同步活跃度、曲库存储体量与最近同步活动流水（上次刷新：{lastRefreshedAt}）
          </Paragraph>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={fetchSyncData} loading={loading}>
          刷新数据
        </Button>
      </div>

      {/* 4 块全平台数据体量统计 */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Card bordered style={{ borderRadius: 10 }}>
            <Statistic
              title={<span style={{ color: "#64748b", fontWeight: 600 }}>云同步活跃用户</span>}
              value={stats?.syncUsers ?? "-"}
              suffix={stats?.totalUsers ? <span style={{ fontSize: 13, color: "#94a3b8" }}>/ {stats.totalUsers} 总用户</span> : undefined}
              prefix={<TeamOutlined style={{ color: "#4f46e5", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered style={{ borderRadius: 10 }}>
            <Statistic
              title={<span style={{ color: "#64748b", fontWeight: 600 }}>全站云端歌曲收藏</span>}
              value={stats?.totalFavorites ?? 0}
              prefix={<HeartOutlined style={{ color: "#ef4444", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered style={{ borderRadius: 10 }}>
            <Statistic
              title={<span style={{ color: "#64748b", fontWeight: 600 }}>全站云端歌单 / 歌曲</span>}
              value={stats?.totalPlaylists ?? 0}
              suffix={stats ? <span style={{ fontSize: 13, color: "#94a3b8" }}>({stats.totalPlaylistSongs} 首)</span> : undefined}
              prefix={<UnorderedListOutlined style={{ color: "#3b82f6", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered style={{ borderRadius: 10 }}>
            <Statistic
              title={<span style={{ color: "#64748b", fontWeight: 600 }}>24小时内听歌打点</span>}
              value={stats?.recentPlayEvents ?? 0}
              suffix={stats ? <span style={{ fontSize: 13, color: "#94a3b8" }}>/ 累计 {stats.totalPlayEvents}</span> : undefined}
              prefix={<CustomerServiceOutlined style={{ color: "#10b981", marginRight: 8 }} />}
              valueStyle={{ fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      {/* 全站用户最近听歌与同步流水 */}
      <Card
        title={
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <SyncOutlined style={{ color: "#4f46e5" }} />
            <span>全站最近听歌与同步流水（最新 50 条）</span>
          </div>
        }
        styles={{ body: { padding: 0 } }}
        style={{ borderRadius: 10 }}
      >
        <Table
          dataSource={events}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条同步事件` }}
        />
      </Card>
    </div>
  );
};
