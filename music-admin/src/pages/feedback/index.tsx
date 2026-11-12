import React, { useCallback, useEffect, useState } from "react";
import {
  Badge,
  Button,
  Card,
  Descriptions,
  Divider,
  Empty,
  Image,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import {
  BugOutlined,
  MessageOutlined,
  PictureOutlined,
  ReloadOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { FeedbackItem, feedbackApi } from "../../providers/api";

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const TYPE_LABEL: Record<string, string> = {
  playback: "播放问题",
  lyrics: "歌词问题",
  library: "音乐库与同步",
  account: "账号问题",
  ui: "界面问题",
  feature: "功能建议",
  other: "其他问题",
};

const STATUS_META: Record<string, { label: string; color: string }> = {
  pending: { label: "待处理", color: "orange" },
  processing: { label: "处理中", color: "blue" },
  resolved: { label: "已解决", color: "green" },
};

const fmtTime = (val?: number) => (val ? new Date(val).toLocaleString() : "-");

const statusTag = (status: string) => {
  const meta = STATUS_META[status] || { label: status || "未知", color: "default" };
  return <Tag color={meta.color}>{meta.label}</Tag>;
};

export const FeedbackPage: React.FC = () => {
  const [items, setItems] = useState<FeedbackItem[]>([]);
  const [total, setTotal] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [detail, setDetail] = useState<FeedbackItem | null>(null);
  const [replyStatus, setReplyStatus] = useState<string>("pending");
  const [replyText, setReplyText] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(
    async (opts?: { status?: string; page?: number; pageSize?: number }) => {
      const status = opts?.status ?? statusFilter;
      const current = opts?.page ?? page;
      const size = opts?.pageSize ?? pageSize;
      setLoading(true);
      try {
        const [list, pending] = await Promise.all([
          feedbackApi.list({ status, limit: size, offset: (current - 1) * size }),
          status === "pending"
            ? Promise.resolve(null)
            : feedbackApi.list({ status: "pending", limit: 1 }),
        ]);
        setItems(list.items);
        setTotal(list.total);
        if (pending) setPendingCount(pending.total);
        else setPendingCount(list.total);
      } catch (e: any) {
        message.error(e?.message || "加载反馈失败");
      } finally {
        setLoading(false);
      }
    },
    [statusFilter, page, pageSize]
  );

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, page, pageSize]);

  const openDetail = (item: FeedbackItem) => {
    setDetail(item);
    setReplyStatus(item.status || "pending");
    setReplyText(item.reply || "");
  };

  const saveReply = async () => {
    if (!detail) return;
    setSaving(true);
    try {
      const res = await feedbackApi.reply(detail.id, replyStatus, replyText.trim());
      if (res.code !== 200) {
        message.error(res.msg || "保存失败");
        return;
      }
      message.success("已保存");
      setDetail(null);
      await load();
    } catch (e: any) {
      message.error(e?.message || "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: "时间",
      dataIndex: "createdAt",
      width: 170,
      render: (val: number) => <Text type="secondary">{fmtTime(val)}</Text>,
    },
    {
      title: "用户",
      dataIndex: "username",
      width: 150,
      render: (_: string, row: FeedbackItem) => (
        <Space size={4}>
          <UserOutlined style={{ color: "#94a3b8" }} />
          <Text>{row.username || `#${row.userId}`}</Text>
        </Space>
      ),
    },
    {
      title: "类型",
      dataIndex: "type",
      width: 130,
      render: (val: string) => <Tag color="geekblue">{TYPE_LABEL[val] || val}</Tag>,
    },
    {
      title: "内容",
      dataIndex: "content",
      ellipsis: true,
      render: (val: string, row: FeedbackItem) => (
        <a onClick={() => openDetail(row)}>
          <Text ellipsis style={{ maxWidth: 360 }}>
            {val}
          </Text>
        </a>
      ),
    },
    {
      title: "图片",
      dataIndex: "images",
      width: 80,
      render: (images: string[]) =>
        images?.length ? (
          <Badge count={images.length} size="small" color="#4f46e5">
            <PictureOutlined style={{ fontSize: 16, color: "#64748b" }} />
          </Badge>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: "日志",
      dataIndex: "withLogs",
      width: 70,
      render: (val: boolean) =>
        val ? <Tag icon={<BugOutlined />} color="purple">有</Tag> : <Text type="secondary">-</Text>,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      render: (val: string) => statusTag(val),
    },
    {
      title: "操作",
      width: 110,
      render: (_: unknown, row: FeedbackItem) => (
        <Button size="small" icon={<MessageOutlined />} onClick={() => openDetail(row)}>
          查看/回复
        </Button>
      ),
    },
  ];

  return (
    <Card>
      <Space style={{ width: "100%", justifyContent: "space-between" }} align="start">
        <Space direction="vertical" size={2}>
          <Title level={4} style={{ margin: 0 }}>
            问题反馈
          </Title>
          <Text type="secondary">
            共 {total} 条 · 待处理{" "}
            <Badge count={pendingCount} size="small" color="orange" showZero />
          </Text>
        </Space>
        <Space>
          <Select
            value={statusFilter}
            style={{ width: 130 }}
            onChange={(val) => {
              setStatusFilter(val);
              setPage(1);
            }}
            options={[
              { value: "all", label: "全部状态" },
              { value: "pending", label: "待处理" },
              { value: "processing", label: "处理中" },
              { value: "resolved", label: "已解决" },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()} loading={loading}>
            刷新
          </Button>
        </Space>
      </Space>

      <Divider style={{ margin: "16px 0" }} />

      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={items}
        columns={columns as any}
        locale={{ emptyText: <Empty description="暂无反馈" /> }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setPageSize(s);
          },
        }}
      />

      <Modal
        open={!!detail}
        title="反馈详情"
        width={720}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={saveReply}
        onCancel={() => setDetail(null)}
        destroyOnClose
      >
        {detail && (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="用户">
                {detail.username || `#${detail.userId}`}
              </Descriptions.Item>
              <Descriptions.Item label="类型">
                {TYPE_LABEL[detail.type] || detail.type}
              </Descriptions.Item>
              <Descriptions.Item label="提交时间">{fmtTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="状态">{statusTag(detail.status)}</Descriptions.Item>
              <Descriptions.Item label="客户端">
                {detail.appVersion || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="设备">{detail.device || "-"}</Descriptions.Item>
              <Descriptions.Item label="诊断日志" span={2}>
                {detail.withLogs ? (
                  <Tag icon={<BugOutlined />} color="purple">
                    已附上（可在「客户端日志」按用户/时间查）
                  </Tag>
                ) : (
                  <Text type="secondary">未附</Text>
                )}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title="问题描述">
              <Paragraph style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
                {detail.content}
              </Paragraph>
            </Card>

            {detail.images?.length > 0 && (
              <Card size="small" title={`相关图片（${detail.images.length}）`}>
                <Image.PreviewGroup>
                  <Space wrap>
                    {detail.images.map((raw, index) => {
                      const src = raw.startsWith("data:")
                        ? raw
                        : `data:image/jpeg;base64,${raw}`;
                      return (
                        <Tooltip key={index} title="点击查看大图">
                          <Image
                            src={src}
                            width={96}
                            height={96}
                            style={{ objectFit: "cover", borderRadius: 8 }}
                          />
                        </Tooltip>
                      );
                    })}
                  </Space>
                </Image.PreviewGroup>
              </Card>
            )}

            <Card size="small" title="处理">
              <Space direction="vertical" size={10} style={{ width: "100%" }}>
                <Space>
                  <Text>状态</Text>
                  <Select
                    value={replyStatus}
                    style={{ width: 140 }}
                    onChange={setReplyStatus}
                    options={[
                      { value: "pending", label: "待处理" },
                      { value: "processing", label: "处理中" },
                      { value: "resolved", label: "已解决" },
                    ]}
                  />
                </Space>
                <TextArea
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  rows={4}
                  maxLength={1000}
                  showCount
                  placeholder="给用户的回复（会显示在客户端的「我的反馈」里）"
                />
              </Space>
            </Card>
          </Space>
        )}
      </Modal>
    </Card>
  );
};

export default FeedbackPage;
