import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Modal,
  message,
  Avatar,
  Typography,
  Statistic,
  Tabs,
  Descriptions,
  Empty,
  Tooltip,
} from "antd";
import {
  UserOutlined,
  ReloadOutlined,
  HeartOutlined,
  PlayCircleOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  DownloadOutlined,
  AudioOutlined,
  HistoryOutlined,
  DesktopOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { request } from "../../providers/api";

const { Title, Text } = Typography;

const fmtTime = (val?: number) => (val ? new Date(val).toLocaleString() : "-");
const fmtDuration = (ms?: number) => {
  if (!ms || ms < 0) return "0 秒";
  const s = Math.floor(ms / 1000);
  if (s < 60) return `${s} 秒`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} 分 ${s % 60} 秒`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} 小时 ${m % 60} 分`;
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`;
};

// song_json 结构随平台不同，宽松取常见字段
const pick = (song: any, keys: string[]): string => {
  if (!song || typeof song !== "object") return "";
  for (const k of keys) {
    if (typeof song[k] === "string" && song[k]) return song[k];
  }
  return "";
};

const songColumns = (extra: any[] = []) => [
  {
    title: "歌曲",
    key: "song",
    render: (_: any, record: any) => {
      const song = typeof record.song === "string" ? safeJSON(record.song) : record.song;
      const title = pick(song, ["title", "name", "songName"]) || record.songId || "未知";
      const artist = pick(song, ["artist", "artists", "singer"]);
      const album = pick(song, ["album", "albumName"]);
      return (
        <div>
          <Text strong style={{ fontSize: 13 }}>{title}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {[artist, album].filter(Boolean).join(" · ") || "-"}
          </Text>
        </div>
      );
    },
  },
  {
    title: "平台",
    dataIndex: "platform",
    key: "platform",
    width: 100,
    render: (p: string) => <Tag color="geekblue">{p || "-"}</Tag>,
  },
  ...extra,
];

function safeJSON(s: string): any {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

interface UserItem {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  email?: string;
  role: string;
  created_at: number;
  last_active_at?: number;
}

interface Overview {
  favorites: number;
  history: number;
  playlists: number;
  playlistSongs: number;
  downloads: number;
  scripts: number;
  playSessions: number;
  playEvents: number;
  listenedMs: number;
  devices: number;
  firstListenAt: number;
  lastListenAt: number;
}

interface SessionInfo {
  id: string;
  device_name: string;
  created_at: number;
  last_seen_at: number;
  expires_at: number;
}

const PAGE = 10;

export const UserDetailPage: React.FC<{ userId: number; open: boolean; onClose: () => void }> = ({
  userId,
  open,
  onClose,
}) => {
  const [user, setUser] = useState<UserItem | null>(null);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState("favorites");

  // 各 Tab 独立数据与分页
  const [tabData, setTabData] = useState<Record<string, { items: any[]; total: number }>>({});
  const [tabPages, setTabPages] = useState<Record<string, number>>({});
  const [tabLoading, setTabLoading] = useState<Record<string, boolean>>({});
  const [activeDays, setActiveDays] = useState<any[]>([]);

  const fetchOverview = useCallback(async () => {
    setLoading(true);
    try {
      const res = await request<{ user: UserItem; stats: Overview; sessions: SessionInfo[] }>(
        `/admin/users/${userId}/overview`
      );
      if (res.code === 200 && res.data) {
        setUser(res.data.user);
        setOverview(res.data.stats);
        setSessions(res.data.sessions || []);
      } else {
        message.error(res.msg || "加载用户概览失败");
      }
    } catch (e: any) {
      message.error(e.message || "请求失败");
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    if (open) {
      setActiveTab("favorites");
      setTabData({});
      setTabPages({});
      fetchOverview();
    }
  }, [open, userId, fetchOverview]);

  const fetchActiveDays = useCallback(async () => {
    try {
      const res = await request<{ days: any[] }>(`/admin/users/${userId}/active-stats`);
      if (res.code === 200 && res.data) setActiveDays(res.data.days || []);
    } catch {
      /* 忽略：图表失败不影响主流程 */
    }
  }, [userId]);

  const fetchTab = useCallback(
    async (tab: string, page: number) => {
      setTabLoading((m) => ({ ...m, [tab]: true }));
      try {
        let url = "";
        if (tab === "activeStats") {
          await fetchActiveDays();
          setTabData((m) => ({ ...m, [tab]: { items: [], total: 0 } }));
          return;
        }
        if (tab === "playlists") url = `/admin/users/${userId}/playlists`;
        else if (tab === "scripts") url = `/admin/users/${userId}/scripts`;
        else url = `/admin/users/${userId}/${tab}?page=${page}&limit=${PAGE}`;

        const res = await request<{ items: any[]; total: number }>(url);
        if (res.code === 200 && res.data) {
          setTabData((m) => ({
            ...m,
            [tab]: { items: res.data?.items || [], total: res.data?.total || 0 },
          }));
          setTabPages((m) => ({ ...m, [tab]: page }));
        } else {
          message.error(res.msg || "加载失败");
        }
      } catch (e: any) {
        message.error(e.message || "请求失败");
      } finally {
        setTabLoading((m) => ({ ...m, [tab]: false }));
      }
    },
    [userId, fetchActiveDays]
  );

  useEffect(() => {
    if (!open) return;
    if (activeTab === "playlists" || activeTab === "scripts" || activeTab === "activeStats") {
      if (!tabData[activeTab]) fetchTab(activeTab, 1);
    } else if (tabData[activeTab] === undefined || tabPages[activeTab] === undefined) {
      fetchTab(activeTab, 1);
    }
  }, [activeTab, open, fetchTab, tabData, tabPages]);

  const pagProps = (tab: string) => ({
    current: tabPages[tab] || 1,
    pageSize: PAGE,
    total: tabData[tab]?.total || 0,
    showTotal: (t: number) => `共 ${t} 条`,
    onChange: (p: number) => fetchTab(tab, p),
  });

  const playlistSongsModal = (record: any) => (
    <PlaylistSongsModal userId={userId!} playlist={record} />
  );

  const tabDefs = [
    {
      key: "activeStats",
      label: (
        <span>
          <ClockCircleOutlined /> 活跃统计
        </span>
      ),
      children: (
        <div>
          <Card size="small" title="最近 30 天每日听歌时长" style={{ marginBottom: 16 }}>
            <ActiveDaysChart days={activeDays} loading={tabLoading["activeStats"]} />
          </Card>
          <Table
            rowKey="day"
            size="small"
            loading={tabLoading["activeStats"]}
            dataSource={activeDays}
            pagination={false}
            columns={[
              { title: "日期", dataIndex: "day", key: "day" },
              {
                title: (
                  <Tooltip title="同首歌连续心跳（间隔≤10秒）合并为一次播放；客户端逐秒上报的心跳不重复计数">
                    播放次数 (已合并)
                  </Tooltip>
                ),
                dataIndex: "events",
                key: "events",
              },
              {
                title: "听歌时长",
                dataIndex: "listenedMs",
                key: "listenedMs",
                render: (v: number) => fmtDuration(v),
              },
            ]}
          />
        </div>
      ),
    },
    {
      key: "favorites",
      label: (
        <span>
          <HeartOutlined /> 收藏列表
        </span>
      ),
      children: (
        <Table
          rowKey={(r) => `${r.platform}:${r.songId}`}
          size="small"
          loading={tabLoading["favorites"]}
          dataSource={tabData["favorites"]?.items}
          pagination={pagProps("favorites")}
          columns={songColumns([
            { title: "收藏时间", dataIndex: "updatedAtMs", key: "t", width: 180, render: fmtTime },
          ])}
        />
      ),
    },
    {
      key: "playlists",
      label: (
        <span>
          <UnorderedListOutlined /> 歌单列表
        </span>
      ),
      children: (
        <Table
          rowKey="playlistId"
          size="small"
          loading={tabLoading["playlists"]}
          dataSource={tabData["playlists"]?.items}
          pagination={false}
          columns={[
            {
              title: "歌单名",
              dataIndex: "name",
              key: "name",
              render: (v: string, r: any) => (
                <Space>
                  {r.coverUrl ? <Avatar shape="square" size={32} src={r.coverUrl} /> : null}
                  <Text strong>{v || "未命名歌单"}</Text>
                  {r.kind === "fav" && <Tag color="red">收藏夹</Tag>}
                </Space>
              ),
            },
            { title: "歌曲数", dataIndex: "songCount", key: "songCount", width: 90 },
            { title: "更新时间", dataIndex: "updatedAtMs", key: "t", width: 180, render: fmtTime },
            {
              title: "操作",
              key: "action",
              width: 100,
              render: (_: any, record: any) => playlistSongsModal(record),
            },
          ]}
        />
      ),
    },
    {
      key: "history",
      label: (
        <span>
          <HistoryOutlined /> 听歌历史
        </span>
      ),
      children: (
        <Table
          rowKey={(r) => `${r.platform}:${r.songId}`}
          size="small"
          loading={tabLoading["history"]}
          dataSource={tabData["history"]?.items}
          pagination={pagProps("history")}
          columns={songColumns([
            { title: "播放时间", dataIndex: "playedAtMs", key: "t", width: 180, render: fmtTime },
          ])}
        />
      ),
    },
    {
      key: "playEvents",
      label: (
        <span>
          <PlayCircleOutlined /> 播放事件
        </span>
      ),
      children: (
        <Table
          rowKey="eventId"
          size="small"
          loading={tabLoading["playEvents"]}
          dataSource={tabData["playEvents"]?.items}
          pagination={pagProps("playEvents")}
          columns={[
            {
              title: "歌曲",
              key: "song",
              render: (_: any, r: any) => (
                <div>
                  <Text strong style={{ fontSize: 13 }}>{r.title || r.songId}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>{r.artist || "-"}</Text>
                </div>
              ),
            },
            { title: "平台", dataIndex: "platform", key: "p", width: 90, render: (p: string) => <Tag color="geekblue">{p || "-"}</Tag> },
            { title: "设备", dataIndex: "deviceName", key: "d", width: 130, render: (v: string, r: any) => v || r.deviceId || "-" },
            { title: "已听时长", dataIndex: "listenedMs", key: "l", width: 110, render: fmtDuration },
            { title: "歌曲时长", dataIndex: "durationMs", key: "du", width: 110, render: fmtDuration },
            { title: "时间", dataIndex: "listenedAtMs", key: "t", width: 180, render: fmtTime },
          ]}
        />
      ),
    },
    {
      key: "scripts",
      label: (
        <span>
          <FileTextOutlined /> 用户脚本
        </span>
      ),
      children: (
        <Table
          rowKey="scriptId"
          size="small"
          loading={tabLoading["scripts"]}
          dataSource={tabData["scripts"]?.items}
          pagination={false}
          expandable={{
            expandedRowRender: (r: any) => (
              <pre
                style={{
                  maxHeight: 300,
                  overflow: "auto",
                  background: "#0f172a",
                  color: "#e2e8f0",
                  padding: 12,
                  borderRadius: 8,
                  fontSize: 12,
                }}
              >
                {r.code || "(空)"}
              </pre>
            ),
          }}
          columns={[
            { title: "脚本名", dataIndex: "name", key: "name", render: (v: string) => v || "未命名" },
            { title: "描述", dataIndex: "description", key: "d", ellipsis: true },
            {
              title: "启用",
              dataIndex: "enabled",
              key: "e",
              width: 80,
              render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
            },
            { title: "更新时间", dataIndex: "updatedAtMs", key: "t", width: 180, render: fmtTime },
          ]}
        />
      ),
    },
    {
      key: "downloads",
      label: (
        <span>
          <DownloadOutlined /> 下载记录
        </span>
      ),
      children: (
        <Table
          rowKey={(r) => `${r.platform}:${r.songId}:${r.deviceId}`}
          size="small"
          loading={tabLoading["downloads"]}
          dataSource={tabData["downloads"]?.items}
          pagination={pagProps("downloads")}
          columns={[
            { title: "歌曲", dataIndex: "title", key: "t", render: (v: string, r: any) => (
              <div>
                <Text strong style={{ fontSize: 13 }}>{v || r.songId}</Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>{[r.artist, r.album].filter(Boolean).join(" · ") || "-"}</Text>
              </div>
            ) },
            { title: "平台", dataIndex: "platform", key: "p", width: 90, render: (p: string) => <Tag color="geekblue">{p || "-"}</Tag> },
            { title: "设备", dataIndex: "deviceName", key: "d", width: 130, render: (v: string, r: any) => v || r.deviceId || "-" },
            { title: "音质", dataIndex: "quality", key: "q", width: 90, render: (v: string) => v || "-" },
            {
              title: "大小",
              dataIndex: "sizeBytes",
              key: "s",
              width: 100,
              render: (v: number) => (v > 0 ? `${(v / 1024 / 1024).toFixed(2)} MB` : "-"),
            },
            { title: "下载时间", dataIndex: "downloadedAtMs", key: "t", width: 180, render: fmtTime },
          ]}
        />
      ),
    },
    {
      key: "sessions",
      label: (
        <span>
          <DesktopOutlined /> 登录设备
        </span>
      ),
      children: (
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={sessions}
          pagination={false}
          columns={[
            { title: "设备名", dataIndex: "device_name", key: "d", render: (v: string) => v || "-" },
            { title: "登录时间", dataIndex: "created_at", key: "c", width: 180, render: fmtTime },
            { title: "最后在线", dataIndex: "last_seen_at", key: "l", width: 180, render: fmtTime },
            { title: "过期时间", dataIndex: "expires_at", key: "e", width: 180, render: fmtTime },
          ]}
        />
      ),
    },
  ];

  return (
    <Modal
      title={
        user ? (
          <Space>
            <Avatar src={user.avatar} icon={<UserOutlined />} />
            <span>
              用户详情：{user.nickname || user.username}
              <Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>@{user.username} (ID: {user.id})</Text>
            </span>
          </Space>
        ) : (
          "用户详情"
        )
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width={960}
      destroyOnClose
    >
      <Card size="small" style={{ marginBottom: 16 }} loading={loading}>
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="邮箱">{user?.email || <Tag>未绑定</Tag>}</Descriptions.Item>
          <Descriptions.Item label="角色">
            {user?.role === "admin" ? <Tag color="blue">管理员</Tag> : <Tag>普通用户</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="注册时间">{fmtTime(user?.created_at)}</Descriptions.Item>
          <Descriptions.Item label="最后活跃">{fmtTime(user?.last_active_at)}</Descriptions.Item>
          <Descriptions.Item label="累计听歌">{fmtDuration(overview?.listenedMs)}</Descriptions.Item>
          <Descriptions.Item label="使用设备数">{overview?.devices ?? "-"}</Descriptions.Item>
        </Descriptions>
      </Card>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="收藏歌曲" value={overview?.favorites ?? 0} prefix={<HeartOutlined />} />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="歌单" value={overview?.playlists ?? 0} prefix={<UnorderedListOutlined />} />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="歌单歌曲" value={overview?.playlistSongs ?? 0} prefix={<AudioOutlined />} />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="听歌历史" value={overview?.history ?? 0} prefix={<HistoryOutlined />} />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic
            title="播放次数"
            value={overview?.playSessions ?? 0}
            prefix={<PlayCircleOutlined />}
            suffix={overview && overview.playEvents > overview.playSessions ? (
              <Tooltip title={`客户端逐秒心跳共上报 ${overview.playEvents} 条原始事件，已合并同首歌连续心跳（间隔≤10秒）为一次播放`}>
                <Text type="secondary" style={{ fontSize: 11 }}>({overview.playEvents} 事件)</Text>
              </Tooltip>
            ) : undefined}
          />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="下载记录" value={overview?.downloads ?? 0} prefix={<DownloadOutlined />} />
        </Card>
        <Card size="small" style={{ flex: "1 1 100px" }}>
          <Statistic title="脚本" value={overview?.scripts ?? 0} prefix={<FileTextOutlined />} />
        </Card>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabDefs}
        tabBarExtraContent={
          <Tooltip title="刷新当前数据">
            <Button
              icon={<ReloadOutlined />}
              size="small"
              onClick={() => {
                setTabData((m) => {
                  const n = { ...m };
                  delete n[activeTab];
                  return n;
                });
                fetchOverview();
              }}
            />
          </Tooltip>
        }
      />
    </Modal>
  );
};

// 歌单内歌曲子弹窗
const PlaylistSongsModal: React.FC<{ userId: number; playlist: any }> = ({ userId, playlist }) => {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const fetchSongs = useCallback(
    async (p: number) => {
      setLoading(true);
      try {
        const res = await request<{ items: any[]; total: number }>(
          `/admin/users/${userId}/playlists/${encodeURIComponent(playlist.playlistId)}/songs?page=${p}&limit=${PAGE}`
        );
        if (res.code === 200 && res.data) {
          setItems(res.data.items || []);
          setTotal(res.data.total || 0);
        } else {
          message.error(res.msg || "加载歌单歌曲失败");
        }
      } catch (e: any) {
        message.error(e.message || "请求失败");
      } finally {
        setLoading(false);
      }
    },
    [userId, playlist]
  );

  useEffect(() => {
    if (open) fetchSongs(1);
  }, [open, fetchSongs]);

  return (
    <>
      <Button
        type="link"
        size="small"
        onClick={() => setOpen(true)}
      >
        查看歌曲
      </Button>
      <Modal
        title={`歌单「${playlist.name || "未命名"}」歌曲（共 ${playlist.songCount} 首）`}
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={640}
      >
        <Table
          rowKey={(r) => `${r.platform}:${r.songId}`}
          size="small"
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无歌曲" /> }}
          pagination={{
            current: page,
            pageSize: PAGE,
            total,
            showTotal: (t: number) => `共 ${t} 条`,
            onChange: (p: number) => {
              setPage(p);
              fetchSongs(p);
            },
          }}
          columns={songColumns([{ title: "位置", dataIndex: "position", key: "pos", width: 70 }])}
        />
      </Modal>
    </>
  );
};

// 简易柱状图（不引入额外图表库）
const ActiveDaysChart: React.FC<{ days: any[]; loading: boolean }> = ({ days, loading }) => {
  if (loading) return <div style={{ textAlign: "center", padding: 24 }}>加载中…</div>;
  if (!days || days.length === 0) return <Empty description="最近 30 天暂无听歌记录" />;
  const max = Math.max(...days.map((d) => d.listenedMs || 0), 1);
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 3, height: 140, overflowX: "auto", padding: "8px 2px" }}>
      {days.map((d) => {
        const h = Math.max(4, Math.round(((d.listenedMs || 0) / max) * 120));
        return (
          <Tooltip
            key={d.day}
            title={`${d.day}：播放 ${d.events} 次，听歌 ${fmtDuration(d.listenedMs)}`}
          >
            <div style={{ textAlign: "center", flexShrink: 0 }}>
              <div
                style={{
                  width: 18,
                  height: h,
                  background: "#4f46e5",
                  borderRadius: 3,
                  margin: "0 auto",
                }}
              />
              <div style={{ fontSize: 10, color: "#64748b", marginTop: 2 }}>
                {d.day?.slice(5)}
              </div>
            </div>
          </Tooltip>
        );
      })}
    </div>
  );
};
