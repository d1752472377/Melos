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
  Popconfirm,
  Form,
  Switch,
  Upload,
  Alert,
  Divider,
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
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  LinkOutlined,
  UploadOutlined,
  CodeOutlined,
  ClearOutlined,
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
  /** 播放记录是否显示"原始心跳"（默认按会话合并显示） */
  const [rawEvents, setRawEvents] = useState(false);
  /** 脚本编辑弹窗 */
  const [scriptModal, setScriptModal] = useState<{ open: boolean; item: any | null }>({
    open: false,
    item: null,
  });
  /** 歌单编辑弹窗 */
  const [playlistModal, setPlaylistModal] = useState<{ open: boolean; item: any | null }>({
    open: false,
    item: null,
  });

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
        else if (tab === "playEvents")
          url = `/admin/users/${userId}/play-events?page=${page}&limit=${PAGE}${rawEvents ? "&raw=1" : ""}`;
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
    [userId, fetchActiveDays, rawEvents]
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

  /** 让某个 Tab 失效（下次渲染自动重新拉取） */
  const reloadTab = useCallback((tab: string) => {
    setTabData((m) => {
      const n = { ...m };
      delete n[tab];
      return n;
    });
  }, []);

  /** 管理员写入后的统一收尾：刷新概览 + 指定 Tab */
  const afterWrite = useCallback(
    (tab: string, tip?: string) => {
      if (tip) message.success(tip);
      fetchOverview();
      reloadTab(tab);
    },
    [fetchOverview, reloadTab]
  );

  /** 管理员删除类操作（统一封装，避免每个 Tab 重复写 try/catch） */
  const adminDelete = useCallback(
    async (url: string, tab: string, tip: string) => {
      try {
        const res = await request(url, { method: "DELETE" });
        if (res.code === 200) afterWrite(tab, tip);
        else message.error(res.msg || "操作失败");
      } catch (e: any) {
        message.error(e.message || "请求失败");
      }
    },
    [afterWrite]
  );

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
                  <Tooltip title="同一首歌、同一台设备上，间隔≤15 秒的连续上报合并为一次播放；客户端逐秒上报的心跳不重复计数">
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
            {
              title: "操作",
              key: "action",
              width: 100,
              render: (_: any, r: any) => (
                <Popconfirm
                  title="从该用户的收藏中移除？"
                  description="会同步到用户的所有设备"
                  okText="移除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() =>
                    adminDelete(
                      `/admin/users/${userId}/favorites/${encodeURIComponent(r.platform)}/${encodeURIComponent(r.songId)}`,
                      "favorites",
                      "已取消收藏"
                    )
                  }
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                    移除
                  </Button>
                </Popconfirm>
              ),
            },
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
        <div>
          <Space style={{ marginBottom: 12 }}>
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setPlaylistModal({ open: true, item: null })}
            >
              新建歌单
            </Button>
            <Text type="secondary" style={{ fontSize: 12 }}>
              改名/换封面会同步到用户的所有设备
            </Text>
          </Space>
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
              { title: "更新时间", dataIndex: "updatedAtMs", key: "t", width: 170, render: fmtTime },
              {
                title: "操作",
                key: "action",
                width: 200,
                render: (_: any, record: any) => (
                  <Space size={0}>
                    {playlistSongsModal(record)}
                    <Button
                      type="link"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => setPlaylistModal({ open: true, item: record })}
                    >
                      编辑
                    </Button>
                    <Popconfirm
                      title="删除该歌单？"
                      description="歌单及其中的歌曲都会从用户所有设备移除"
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() =>
                        adminDelete(
                          `/admin/users/${userId}/playlists/${encodeURIComponent(record.playlistId)}`,
                          "playlists",
                          "歌单已删除"
                        )
                      }
                    >
                      <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </div>
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
        <div>
          <Space style={{ marginBottom: 12 }}>
            <Popconfirm
              title="清空该用户的全部听歌历史？"
              description="会同步删除用户所有设备上的历史记录，不可恢复"
              okText="清空"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => adminDelete(`/admin/users/${userId}/history`, "history", "已清空历史")}
            >
              <Button size="small" danger icon={<ClearOutlined />}>
                清空全部历史
              </Button>
            </Popconfirm>
          </Space>
          <Table
            rowKey={(r) => `${r.platform}:${r.songId}`}
            size="small"
            loading={tabLoading["history"]}
            dataSource={tabData["history"]?.items}
            pagination={pagProps("history")}
            columns={songColumns([
              { title: "播放时间", dataIndex: "playedAtMs", key: "t", width: 170, render: fmtTime },
              {
                title: "操作",
                key: "action",
                width: 100,
                render: (_: any, r: any) => (
                  <Popconfirm
                    title="删除这条历史？"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() =>
                      adminDelete(
                        `/admin/users/${userId}/history/${encodeURIComponent(r.platform)}/${encodeURIComponent(r.songId)}`,
                        "history",
                        "已删除"
                      )
                    }
                  >
                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                ),
              },
            ])}
          />
        </div>
      ),
    },
    {
      key: "playEvents",
      label: (
        <span>
          <PlayCircleOutlined /> 播放记录
        </span>
      ),
      children: (
        <div>
          <Space style={{ marginBottom: 12 }} size="middle" wrap>
            <Text type="secondary" style={{ fontSize: 12 }}>
              按「连续播放」合并：同一首歌同一设备，中断不超过 15 秒算同一次播放
            </Text>
            <Button
              size="small"
              onClick={() => {
                setRawEvents(!rawEvents);
                setTabData((m) => {
                  const n = { ...m };
                  delete n["playEvents"];
                  return n;
                });
              }}
            >
              {rawEvents ? "查看合并后的播放" : "查看原始心跳"}
            </Button>
          </Space>
          <Table
            rowKey={(r: any) =>
              rawEvents ? r.eventId : `${r.platform}:${r.songId}:${r.deviceId}:${r.startedAt}`
            }
            size="small"
            loading={tabLoading["playEvents"]}
            dataSource={tabData["playEvents"]?.items}
            pagination={pagProps("playEvents")}
            columns={
              rawEvents
                ? [
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
                    { title: "心跳时长", dataIndex: "listenedMs", key: "l", width: 100, render: fmtDuration },
                    { title: "歌曲时长", dataIndex: "durationMs", key: "du", width: 100, render: fmtDuration },
                    { title: "时间", dataIndex: "listenedAtMs", key: "t", width: 170, render: fmtTime },
                  ]
                : [
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
                    {
                      title: "本次播放时长",
                      dataIndex: "listenedMs",
                      key: "l",
                      width: 130,
                      render: (v: number) => <Text strong>{fmtDuration(v)}</Text>,
                    },
                    { title: "歌曲时长", dataIndex: "durationMs", key: "du", width: 100, render: fmtDuration },
                    {
                      title: "开始时间",
                      dataIndex: "startedAt",
                      key: "t",
                      width: 170,
                      render: fmtTime,
                    },
                    {
                      title: "分段",
                      dataIndex: "segments",
                      key: "seg",
                      width: 70,
                      render: (v: number) => (
                        <Tooltip title="本次播放由多少条上报记录合并而成（旧版客户端逐秒心跳会很多）">
                          <Tag>{v}</Tag>
                        </Tooltip>
                      ),
                    },
                  ]
            }
          />
        </div>
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
        <div>
          <Space style={{ marginBottom: 12 }} wrap>
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setScriptModal({ open: true, item: null })}
            >
              新增脚本
            </Button>
            <Text type="secondary" style={{ fontSize: 12 }}>
              支持手写 / 从链接导入（服务端拉取，避开跨域）/ 上传本地文件
            </Text>
          </Space>
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
              {
                title: "脚本 id",
                dataIndex: "scriptId",
                key: "id",
                width: 180,
                ellipsis: true,
                render: (v: string) => (
                  <Text type="secondary" style={{ fontSize: 12 }} copyable={{ text: v || "" }}>
                    {v || "-"}
                  </Text>
                ),
              },
              { title: "描述", dataIndex: "description", key: "d", ellipsis: true },
              {
                title: "启用",
                dataIndex: "enabled",
                key: "e",
                width: 80,
                render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
              },
              { title: "更新时间", dataIndex: "updatedAtMs", key: "t", width: 170, render: fmtTime },
              {
                title: "操作",
                key: "action",
                width: 160,
                render: (_: any, r: any) => (
                  <Space size={0}>
                    <Button
                      type="link"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => setScriptModal({ open: true, item: r })}
                    >
                      编辑
                    </Button>
                    <Popconfirm
                      title="删除该脚本？"
                      description="会同步删除用户所有设备上的这个音源脚本"
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() =>
                        adminDelete(
                          `/admin/users/${userId}/scripts/${encodeURIComponent(r.scriptId)}`,
                          "scripts",
                          "脚本已删除"
                        )
                      }
                    >
                      <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </div>
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
            { title: "下载时间", dataIndex: "downloadedAtMs", key: "t", width: 170, render: fmtTime },
            {
              title: "操作",
              key: "action",
              width: 100,
              render: (_: any, r: any) => (
                <Popconfirm
                  title="删除这条下载记录？"
                  description="仅删除记录，不影响设备上的文件"
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() =>
                    adminDelete(
                      `/admin/users/${userId}/downloads/${encodeURIComponent(r.platform)}/${encodeURIComponent(r.songId)}/${encodeURIComponent(r.deviceId)}`,
                      "downloads",
                      "已删除"
                    )
                  }
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              ),
            },
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
              <Tooltip title={`客户端共上报 ${overview.playEvents} 条原始记录；同一首歌、同一台设备上间隔≤15 秒的连续记录已合并为一次播放`}>
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

      {/* 脚本编辑：手写 / 链接导入 / 上传文件 */}
      <ScriptEditorModal
        userId={userId}
        open={scriptModal.open}
        item={scriptModal.item}
        onClose={() => setScriptModal({ open: false, item: null })}
        onSaved={() => {
          setScriptModal({ open: false, item: null });
          afterWrite("scripts", "脚本已保存");
        }}
      />

      {/* 歌单新建 / 改名 / 换封面 */}
      <PlaylistEditorModal
        userId={userId}
        open={playlistModal.open}
        item={playlistModal.item}
        onClose={() => setPlaylistModal({ open: false, item: null })}
        onSaved={() => {
          setPlaylistModal({ open: false, item: null });
          afterWrite("playlists", "歌单已保存");
        }}
      />
    </Modal>
  );
};

/**
 * 新增脚本时用的 id。
 *
 * 管理端没有客户端那种"本地脚本 id"，以前 POST 直接把 `id: undefined` 发出去，
 * JSON.stringify 会把这整个字段丢掉，后端就回 400「脚本 id 不能为空」——
 * 「新增脚本」因此完全不可用。后端现在也会兜底生成，但前端带上 id 更稳：
 * 保存后刷新列表能立刻定位到这条记录。
 */
const newScriptId = () =>
  `admin_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

/**
 * 脚本编辑器：三种录入方式共用一份 code。
 *
 * - 手写：直接编辑源码；
 * - 链接导入：交给服务端拉取（浏览器直连会撞 CORS，且脚本常托管在
 *   GitHub raw / gist，前端 fetch 拿不到正文）；
 * - 上传文件：读本地 .js 文本填进编辑器，保存仍走同一接口。
 *
 * 导入/上传都只是"把内容填进 textarea"，用户能先看一眼再保存——
 * 避免管理员误把 HTML 错误页当成脚本写进用户设备。
 */
const ScriptEditorModal: React.FC<{
  userId: number;
  open: boolean;
  item: any | null;
  onClose: () => void;
  onSaved: () => void;
}> = ({ userId, open, item, onClose, onSaved }) => {
  const [form] = Form.useForm();
  const [code, setCode] = useState("");
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importUrl, setImportUrl] = useState("");
  const isEdit = !!item;

  useEffect(() => {
    if (!open) return;
    if (item) {
      form.setFieldsValue({
        name: item.name,
        description: item.description,
        enabled: item.enabled,
      });
      setCode(item.code || "");
    } else {
      form.setFieldsValue({ name: "", description: "", enabled: true });
      setCode("");
    }
    setImportUrl("");
  }, [open, item, form]);

  const handleImportUrl = async () => {
    const url = importUrl.trim();
    if (!url) {
      message.warning("请填写脚本链接");
      return;
    }
    setImporting(true);
    try {
      // 只拉取内容回填编辑器，不直接入库：管理员应先过目
      const res = await request<any>(`/admin/users/${userId}/scripts/import-url`, {
        method: "POST",
        body: JSON.stringify({ url, name: form.getFieldValue("name") || undefined, dryRun: true }),
      });
      if (res.code === 200) {
        // 后端导入接口会直接保存；这里为了"先看后存"，改成用返回的源码回填
        setCode(res.data?.code || "");
        if (res.data?.name && !form.getFieldValue("name")) {
          form.setFieldsValue({ name: res.data.name });
        }
        message.success("已拉取脚本内容，确认无误后点保存");
      } else {
        message.error(res.msg || "导入失败");
      }
    } catch (e: any) {
      message.error(e.message || "导入失败");
    } finally {
      setImporting(false);
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      if (!code.trim()) {
        message.warning("脚本内容不能为空");
        return;
      }
      setSaving(true);
      const id = item?.scriptId || newScriptId();
      const url = isEdit
        ? `/admin/users/${userId}/scripts/${encodeURIComponent(item.scriptId)}`
        : `/admin/users/${userId}/scripts`;
      const res = await request(url, {
        method: isEdit ? "PUT" : "POST",
        body: JSON.stringify({
          id,
          name: values.name,
          description: values.description || "",
          enabled: values.enabled !== false,
          code,
        }),
      });
      if (res.code === 200) {
        onSaved();
      } else {
        message.error(res.msg || "保存失败");
      }
    } catch (e: any) {
      if (e?.errorFields) return; // 表单校验失败
      message.error(e.message || "保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={isEdit ? `编辑脚本：${item?.name || ""}` : "新增脚本"}
      open={open}
      onCancel={onClose}
      onOk={handleSave}
      confirmLoading={saving}
      okText="保存"
      width={860}
      destroyOnClose
    >
      <Form form={form} layout="vertical" size="small">
        <Space size="middle" style={{ display: "flex" }} align="start">
          <Form.Item name="name" label="脚本名" rules={[{ required: true, message: "请输入脚本名" }]} style={{ flex: 1 }}>
            <Input placeholder="例如：墨澜聚合音源" />
          </Form.Item>
          <Form.Item name="description" label="描述" style={{ flex: 2 }}>
            <Input placeholder="选填，会显示在用户端的音源列表里" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>
        </Space>

        <Divider style={{ margin: "4px 0 12px" }} orientation="left" plain>
          <Text type="secondary" style={{ fontSize: 12 }}>
            脚本内容
          </Text>
        </Divider>

        <Space style={{ marginBottom: 8 }} wrap>
          <Input
            prefix={<LinkOutlined />}
            placeholder="粘贴脚本直链（GitHub raw / gist / 自建）"
            value={importUrl}
            onChange={(e) => setImportUrl(e.target.value)}
            style={{ width: 420 }}
            allowClear
          />
          <Button onClick={handleImportUrl} loading={importing} icon={<LinkOutlined />}>
            从链接导入
          </Button>
          <Upload
            accept=".js,.mjs,.txt,text/javascript"
            showUploadList={false}
            beforeUpload={(file) => {
              const reader = new FileReader();
              reader.onload = () => {
                setCode(String(reader.result || ""));
                if (!form.getFieldValue("name")) {
                  form.setFieldsValue({ name: file.name.replace(/\.[^.]+$/, "") });
                }
                message.success(`已读取 ${file.name}，确认后保存`);
              };
              reader.onerror = () => message.error("读取文件失败");
              reader.readAsText(file);
              return false; // 不上传到服务器，只在本地读取
            }}
          >
            <Button icon={<UploadOutlined />}>上传本地文件</Button>
          </Upload>
        </Space>

        <Input.TextArea
          value={code}
          onChange={(e) => setCode(e.target.value)}
          rows={16}
          placeholder="// 在此粘贴或编辑脚本源码"
          style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 12 }}
        />
        <Alert
          style={{ marginTop: 8 }}
          type="info"
          showIcon
          message="保存后会同步到该用户的所有设备，用户端下次同步即可看到这个音源脚本。"
        />
      </Form>
    </Modal>
  );
};

/** 歌单编辑：新建 / 改名 / 换封面 */
const PlaylistEditorModal: React.FC<{
  userId: number;
  open: boolean;
  item: any | null;
  onClose: () => void;
  onSaved: () => void;
}> = ({ userId, open, item, onClose, onSaved }) => {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const isEdit = !!item;

  useEffect(() => {
    if (!open) return;
    if (item) {
      form.setFieldsValue({ name: item.name, coverUrl: item.coverUrl });
    } else {
      form.setFieldsValue({ name: "", coverUrl: "" });
    }
  }, [open, item, form]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const url = isEdit
        ? `/admin/users/${userId}/playlists/${encodeURIComponent(item.playlistId)}`
        : `/admin/users/${userId}/playlists`;
      const res = await request(url, {
        method: isEdit ? "PUT" : "POST",
        body: JSON.stringify({
          id: item?.playlistId,
          name: values.name,
          coverUrl: values.coverUrl || "",
          kind: item?.kind || "mine",
        }),
      });
      if (res.code === 200) onSaved();
      else message.error(res.msg || "保存失败");
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.message || "保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={isEdit ? `编辑歌单：${item?.name || ""}` : "新建歌单"}
      open={open}
      onCancel={onClose}
      onOk={handleSave}
      confirmLoading={saving}
      okText="保存"
      destroyOnClose
    >
      <Form form={form} layout="vertical" size="small">
        <Form.Item name="name" label="歌单名" rules={[{ required: true, message: "请输入歌单名" }]}>
          <Input placeholder="例如：通勤歌单" />
        </Form.Item>
        <Form.Item name="coverUrl" label="封面图 URL">
          <Input placeholder="选填，图片直链" />
        </Form.Item>
        <Alert type="info" showIcon message="保存后会同步到该用户的所有设备。" />
      </Form>
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
          columns={songColumns([
            { title: "位置", dataIndex: "position", key: "pos", width: 70 },
            {
              title: "操作",
              key: "action",
              width: 90,
              render: (_: any, r: any) => (
                <Popconfirm
                  title="从歌单中移除这首歌？"
                  okText="移除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={async () => {
                    try {
                      const res = await request(
                        `/admin/users/${userId}/playlists/${encodeURIComponent(playlist.playlistId)}/songs/${encodeURIComponent(r.platform)}/${encodeURIComponent(r.songId)}`,
                        { method: "DELETE" }
                      );
                      if (res.code === 200) {
                        message.success("已移除");
                        fetchSongs(page);
                      } else {
                        message.error(res.msg || "移除失败");
                      }
                    } catch (e: any) {
                      message.error(e.message || "移除失败");
                    }
                  }}
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                    移除
                  </Button>
                </Popconfirm>
              ),
            },
          ])}
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
