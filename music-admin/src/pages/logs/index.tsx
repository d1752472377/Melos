import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Select,
  Modal,
  message,
  Typography,
  Statistic,
  Popconfirm,
  Tooltip,
  Descriptions,
  Switch,
} from "antd";
import {
  ReloadOutlined,
  SearchOutlined,
  BugOutlined,
  WarningOutlined,
  FileSearchOutlined,
  DeleteOutlined,
  UserOutlined,
  CheckCircleOutlined,
} from "@ant-design/icons";
import { request } from "../../providers/api";

const { Title, Text, Paragraph } = Typography;

const fmtTime = (val?: number) => (val ? new Date(val).toLocaleString() : "-");

const levelTag = (level: string) => {
  switch (level) {
    case "ERROR":
      return <Tag color="red" icon={<BugOutlined />}>ERROR</Tag>;
    case "WARN":
      return <Tag color="orange" icon={<WarningOutlined />}>WARN</Tag>;
    case "INFO":
      return <Tag color="blue">INFO</Tag>;
    case "DEBUG":
      return <Tag color="default">DEBUG</Tag>;
    default:
      return <Tag>{level || "-"}</Tag>;
  }
};

interface LogItem {
  id: number;
  userId: number;
  username: string;
  sessionId: string;
  level: string;
  tag: string;
  message: string;
  stack: string;
  extra: Record<string, string> | null;
  count: number;
  appVersion: string;
  versionCode: number;
  os: string;
  device: string;
  ip: string;
  thread: string;
  context: Record<string, string> | null;
  breadcrumbs: { timeMs: number; level: string; tag: string; message: string }[] | null;
  resolved: boolean;
  resolvedAt: number;
  createdAt: number;
}

/**
 * 环境快照分组：把几十个 key 按"排查时的思考顺序"分块，
 * 而不是平铺成一长串 —— 平铺时人眼根本找不到"内存还剩多少"。
 */
const CONTEXT_GROUPS: { title: string; keys: string[] }[] = [
  {
    title: "播放状态",
    keys: ["song", "songId", "isPlaying", "positionMs", "durationMs", "playMode", "speed", "lastPlayError", "source"],
  },
  {
    title: "页面与账号",
    keys: ["page", "loggedIn", "uid", "username", "server"],
  },
  {
    title: "网络",
    keys: ["network", "metered", "internet", "carrier", "cellType"],
  },
  {
    title: "内存与存储",
    keys: ["appHeap", "sysMemFree", "sysMemTotal", "lowMemory", "lowRamDevice", "storageFree", "storageTotal", "storageLow", "extStorage"],
  },
  {
    title: "电量",
    keys: ["batteryLevel", "charging", "batteryTemp", "powerSave"],
  },
  {
    title: "进程",
    keys: ["uptime", "threads", "foreground", "pid", "process"],
  },
  {
    title: "设备与系统",
    keys: ["manufacturer", "brand", "model", "device", "product", "hardware", "abi", "android", "securityPatch", "buildTags", "emulator", "rooted"],
  },
  {
    title: "屏幕与语言",
    keys: ["screen", "density", "fontScale", "darkMode", "locale", "timezone"],
  },
  {
    title: "应用",
    keys: ["appVersion", "versionCode", "buildType", "flavor", "debug"],
  },
];

/** 按分组渲染环境快照；未归类的 key 兜底放进"其它" */
const renderContext = (ctx: Record<string, string> | null) => {
  if (!ctx || Object.keys(ctx).length === 0) {
    return <Text type="secondary">该日志没有环境快照（旧版本客户端或崩溃前未采集）</Text>;
  }
  const used = new Set<string>();
  const groups = CONTEXT_GROUPS.map((g) => {
    const entries = g.keys.filter((k) => ctx[k] !== undefined && ctx[k] !== "").map((k) => [k, ctx[k]] as const);
    entries.forEach(([k]) => used.add(k));
    return { title: g.title, entries };
  }).filter((g) => g.entries.length > 0);
  const rest = Object.entries(ctx).filter(([k]) => !used.has(k));

  return (
    <Space direction="vertical" size={10} style={{ width: "100%" }}>
      {groups.map((g) => (
        <div key={g.title}>
          <Text strong style={{ fontSize: 12, color: "#4f46e5" }}>{g.title}</Text>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "4px 16px", marginTop: 4 }}>
            {g.entries.map(([k, v]) => (
              <span key={k} style={{ fontSize: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>{k}</Text>
                <Text style={{ fontSize: 12, marginLeft: 6 }}>{v}</Text>
              </span>
            ))}
          </div>
        </div>
      ))}
      {rest.length > 0 && (
        <div>
          <Text strong style={{ fontSize: 12, color: "#64748b" }}>其它</Text>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "4px 16px", marginTop: 4 }}>
            {rest.map(([k, v]) => (
              <span key={k} style={{ fontSize: 12 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>{k}</Text>
                <Text style={{ fontSize: 12, marginLeft: 6 }}>{v}</Text>
              </span>
            ))}
          </div>
        </div>
      )}
    </Space>
  );
};

interface LogStats {
  errors24h: number;
  warns24h: number;
  unresolved24h: number;
  total24h: number;
  devices24h: number;
  total: number;
}

const PAGE = 20;

export const LogsPage: React.FC = () => {
  const [items, setItems] = useState<LogItem[]>([]);
  const [stats, setStats] = useState<LogStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE);

  const [searchInput, setSearchInput] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [level, setLevel] = useState("ALL");
  const [resolvedFilter, setResolvedFilter] = useState("ALL");
  const [detail, setDetail] = useState<LogItem | null>(null);

  const reqIdRef = useRef(0);

  const fetchLogs = useCallback(async () => {
    const rid = ++reqIdRef.current;
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        limit: String(pageSize),
      });
      if (searchQuery.trim()) params.append("search", searchQuery.trim());
      if (level && level !== "ALL") params.append("level", level);
      if (resolvedFilter === "UNRESOLVED") params.append("resolved", "false");
      if (resolvedFilter === "RESOLVED") params.append("resolved", "true");
      const res = await request<{ items: LogItem[]; total: number }>(
        `/admin/logs?${params.toString()}`
      );
      if (rid === reqIdRef.current) {
        if (res.code === 200 && res.data) {
          setItems(res.data.items || []);
          setTotal(res.data.total || 0);
        } else {
          message.error(res.msg || "加载日志失败");
        }
      }
    } catch (e: any) {
      if (rid === reqIdRef.current) message.error(e.message || "请求失败");
    } finally {
      if (rid === reqIdRef.current) setLoading(false);
    }
  }, [page, pageSize, searchQuery, level, resolvedFilter]);

  const fetchStats = useCallback(async () => {
    try {
      const res = await request<LogStats>("/admin/logs/stats");
      if (res.code === 200 && res.data) setStats(res.data);
    } catch {
      /* 概览失败不阻塞列表 */
    }
  }, []);

  useEffect(() => {
    fetchLogs();
    fetchStats();
  }, [fetchLogs, fetchStats]);

  const handlePrune = async () => {
    try {
      const res = await request<{ deleted: number }>("/admin/logs?days=30", {
        method: "DELETE",
      });
      if (res.code === 200) {
        message.success(`已清理 ${res.data?.deleted ?? 0} 条旧日志`);
        fetchLogs();
        fetchStats();
      } else {
        message.error(res.msg || "清理失败");
      }
    } catch (e: any) {
      message.error(e.message || "清理失败");
    }
  };

  const handleToggleResolved = async (row: LogItem, resolved: boolean) => {
    try {
      const res = await request(`/admin/logs/${row.id}`, {
        method: "PATCH",
        body: JSON.stringify({ resolved }),
      });
      if (res.code === 200) {
        setItems((prev) => prev.map((it) => (it.id === row.id ? { ...it, resolved } : it)));
        if (detail?.id === row.id) setDetail({ ...detail, resolved });
        fetchStats();
      } else {
        message.error(res.msg || "更新失败");
      }
    } catch (e: any) {
      message.error(e.message || "更新失败");
    }
  };

  const columns = [
    {
      title: "级别",
      dataIndex: "level",
      key: "level",
      width: 110,
      render: (v: string) => levelTag(v),
      filters: [
        { text: "ERROR", value: "ERROR" },
        { text: "WARN", value: "WARN" },
        { text: "INFO", value: "INFO" },
        { text: "DEBUG", value: "DEBUG" },
      ],
      filterMultiple: false,
    },
    {
      title: "内容",
      key: "content",
      render: (_: any, r: LogItem) => (
        <div style={{ minWidth: 0 }}>
          <Space size={6}>
            <Text strong style={{ fontSize: 13 }}>{r.tag || "-"}</Text>
            {r.count > 1 && (
              <Tag color="purple" style={{ fontSize: 11 }}>×{r.count}</Tag>
            )}
          </Space>
          <Paragraph
            style={{ marginBottom: 0, fontSize: 12, color: "#64748b" }}
            ellipsis={{ rows: 1, expandable: false }}
          >
            {r.message}
          </Paragraph>
        </div>
      ),
    },
    {
      title: "来源",
      key: "source",
      width: 210,
      render: (_: any, r: LogItem) => (
        <div style={{ fontSize: 12 }}>
          <Text style={{ fontSize: 12 }}>{r.device || "未知设备"}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 11 }}>
            v{r.appVersion || "?"} · {r.os || "?"}
          </Text>
        </div>
      ),
    },
    {
      title: "用户",
      key: "user",
      width: 120,
      render: (_: any, r: LogItem) =>
        r.userId > 0 ? (
          <Text style={{ fontSize: 12 }}>{r.username || `#${r.userId}`}</Text>
        ) : (
          <Tag style={{ fontSize: 11 }}>匿名</Tag>
        ),
    },
    {
      title: "时间",
      dataIndex: "createdAt",
      key: "time",
      width: 170,
      render: fmtTime,
    },
    {
      title: "已处理",
      key: "resolved",
      width: 90,
      render: (_: any, r: LogItem) => (
        <Tooltip title={r.resolved ? "点击标记为未处理" : "点击标记为已处理"}>
          <Switch
            size="small"
            checked={!!r.resolved}
            onChange={(checked) => handleToggleResolved(r, checked)}
          />
        </Tooltip>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_: any, r: LogItem) => (
        <Button
          type="link"
          size="small"
          icon={<FileSearchOutlined />}
          onClick={() => setDetail(r)}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 20,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0 }}>
            📋 客户端日志
          </Title>
          <Text type="secondary">
            App 上报的错误 / 警告 / 崩溃记录，用于无真机时定位线上问题
          </Text>
        </div>
        <Space>
          <Popconfirm
            title="清理 30 天前的日志？"
            onConfirm={handlePrune}
            okText="清理"
            cancelText="取消"
          >
            <Button icon={<DeleteOutlined />}>清理旧日志</Button>
          </Popconfirm>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => {
              fetchLogs();
              fetchStats();
            }}
          >
            刷新
          </Button>
        </Space>
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
        {[
          { title: "24h 错误", value: stats?.errors24h, color: "#cf1322", icon: <BugOutlined /> },
          { title: "24h 警告", value: stats?.warns24h, color: "#d46b08", icon: <WarningOutlined /> },
          { title: "24h 未处理", value: stats?.unresolved24h, color: "#dc2626", icon: <CheckCircleOutlined /> },
          { title: "24h 总量", value: stats?.total24h, color: "#4f46e5", icon: <FileSearchOutlined /> },
          { title: "24h 活跃设备", value: stats?.devices24h, color: "#389e0d", icon: <UserOutlined /> },
        ].map((s) => (
          <Card key={s.title} size="small" style={{ flex: "1 1 160px" }}>
            <Statistic
              title={s.title}
              value={s.value ?? 0}
              prefix={s.icon}
              valueStyle={{ color: s.color, fontSize: 22 }}
            />
          </Card>
        ))}
      </div>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap size="middle">
          <Input
            placeholder="搜索 tag / 消息 / 堆栈 / 设备 / 版本"
            prefix={<SearchOutlined />}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onPressEnter={() => {
              setPage(1);
              setSearchQuery(searchInput);
            }}
            allowClear
            onClear={() => {
              setSearchInput("");
              setSearchQuery("");
              setPage(1);
            }}
            style={{ width: 300 }}
          />
          <Select
            value={level}
            onChange={(v) => {
              setLevel(v);
              setPage(1);
            }}
            style={{ width: 130 }}
            options={[
              { label: "全部级别", value: "ALL" },
              { label: "ERROR", value: "ERROR" },
              { label: "WARN", value: "WARN" },
              { label: "INFO", value: "INFO" },
              { label: "DEBUG", value: "DEBUG" },
            ]}
          />
          <Select
            value={resolvedFilter}
            onChange={(v) => {
              setResolvedFilter(v);
              setPage(1);
            }}
            style={{ width: 140 }}
            options={[
              { label: "全部状态", value: "ALL" },
              { label: "未处理", value: "UNRESOLVED" },
              { label: "已处理", value: "RESOLVED" },
            ]}
          />
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              setSearchQuery(searchInput);
            }}
          >
            筛选
          </Button>
        </Space>
      </Card>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={items}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
            showSizeChanger: true,
            pageSizeOptions: ["20", "50", "100"],
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      {/* 日志详情弹窗 */}
      <Modal
        title={
          detail ? (
            <Space>
              {levelTag(detail.level)}
              {detail.resolved && <Tag color="green">已处理</Tag>}
              <span>{detail.tag || "日志详情"}</span>
            </Space>
          ) : (
            "日志详情"
          )
        }
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail && (
          <div>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 12 }}>
              <Descriptions.Item label="时间">{fmtTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="出现次数">×{detail.count}</Descriptions.Item>
              <Descriptions.Item label="设备">{detail.device || "-"}</Descriptions.Item>
              <Descriptions.Item label="系统">{detail.os || "-"}</Descriptions.Item>
              <Descriptions.Item label="App 版本">
                v{detail.appVersion || "?"} (code {detail.versionCode || "?"})
              </Descriptions.Item>
              <Descriptions.Item label="线程">
                <Text code style={{ fontSize: 12 }}>{detail.thread || "-"}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="会话">{detail.sessionId || "-"}</Descriptions.Item>
              <Descriptions.Item label="IP">{detail.ip || "-"}</Descriptions.Item>
              <Descriptions.Item label="用户" span={2}>
                {detail.userId > 0
                  ? `${detail.username || `#${detail.userId}`} (#${detail.userId})`
                  : "匿名"}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title="消息" style={{ marginBottom: 12 }}>
              <Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0, fontSize: 13 }}>
                {detail.message}
              </Paragraph>
            </Card>

            {detail.context && Object.keys(detail.context).length > 0 && (
              <Card size="small" title="环境快照（当时机器状态）" style={{ marginBottom: 12 }}>
                {renderContext(detail.context)}
              </Card>
            )}

            {detail.breadcrumbs && detail.breadcrumbs.length > 0 && (
              <Card
                size="small"
                title={`操作轨迹（崩溃/报错前最近 ${detail.breadcrumbs.length} 步）`}
                style={{ marginBottom: 12 }}
              >
                <div style={{ maxHeight: 260, overflow: "auto" }}>
                  {detail.breadcrumbs.map((c, i) => (
                    <div
                      key={`${c.timeMs}-${i}`}
                      style={{ display: "flex", gap: 8, fontSize: 12, padding: "2px 0", alignItems: "baseline" }}
                    >
                      <Text type="secondary" style={{ fontSize: 11, flexShrink: 0, width: 92 }}>
                        {new Date(c.timeMs).toLocaleTimeString()}
                      </Text>
                      <Tag
                        style={{ fontSize: 10, marginRight: 0, flexShrink: 0 }}
                        color={
                          c.level === "ERROR" ? "red" : c.level === "WARN" ? "orange" : c.level === "BREADCRUMB" ? "purple" : "default"
                        }
                      >
                        {c.tag || c.level}
                      </Tag>
                      <span style={{ color: "#334155" }}>{c.message}</span>
                    </div>
                  ))}
                </div>
              </Card>
            )}

            {detail.extra && Object.keys(detail.extra).length > 0 && (
              <Card size="small" title="上下文 extra" style={{ marginBottom: 12 }}>
                <Descriptions size="small" column={1}>
                  {Object.entries(detail.extra).map(([k, v]) => (
                    <Descriptions.Item key={k} label={<Text code style={{ fontSize: 12 }}>{k}</Text>}>
                      <Text style={{ fontSize: 12 }}>{v}</Text>
                    </Descriptions.Item>
                  ))}
                </Descriptions>
              </Card>
            )}

            {detail.stack && (
              <Card size="small" title="堆栈">
                <pre
                  style={{
                    maxHeight: 320,
                    overflow: "auto",
                    background: "#0f172a",
                    color: "#e2e8f0",
                    padding: 12,
                    borderRadius: 8,
                    fontSize: 12,
                    margin: 0,
                  }}
                >
                  {detail.stack}
                </pre>
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};
