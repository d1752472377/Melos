import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Upload,
  Popconfirm,
  message,
  Typography,
  Tooltip,
} from "antd";
import {
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EditOutlined,
  DownloadOutlined,
  InboxOutlined,
  ExclamationCircleOutlined,
  GithubOutlined,
} from "@ant-design/icons";
import type { UploadFile } from "antd/es/upload/interface";
import { request } from "../../providers/api";
import { API_URL } from "../../providers/constants";

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export interface AppVersionItem {
  id: number;
  versionCode: number;
  versionName: string;
  apkUrl: string;
  fileSize: number;
  hashSha256: string;
  changelog: string;
  forceUpdate: boolean;
  enabled: boolean;
  createdAt: number;
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`;
}

export const VersionsPage: React.FC = () => {
  const [versions, setVersions] = useState<AppVersionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 弹窗状态与 Loading
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [currentVersion, setCurrentVersion] = useState<AppVersionItem | null>(null);

  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [syncGithubLoading, setSyncGithubLoading] = useState(false);

  const handleSyncGitHub = async () => {
    setSyncGithubLoading(true);
    try {
      const res = await request<{ message: string; version: any; prunedOldFiles: number }>(
        "/admin/versions/sync-github",
        { method: "POST" }
      );
      if (res.code === 200) {
        message.success(res.data?.message || "已成功从 GitHub Release 同步最新版本并清理旧包");
        fetchVersions();
      } else {
        message.error(res.msg || "同步失败，请检查 GitHub Release 是否存在");
      }
    } catch (err: any) {
      message.error(err.message || "请求失败");
    } finally {
      setSyncGithubLoading(false);
    }
  };

  const fetchVersions = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        limit: String(pageSize),
      });
      const res = await request<{ items: AppVersionItem[]; total: number }>(
        `/admin/versions?${params.toString()}`
      );
      if (res.code === 200 && res.data) {
        setVersions(res.data.items || []);
        setTotal(res.data.total || 0);
      } else {
        message.error(res.msg || "加载版本列表失败");
      }
    } catch (err: any) {
      message.error(err.message || "请求失败");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchVersions();
  }, [fetchVersions]);

  // 发布新版本
  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields();
      if (!fileList.length || !fileList[0].originFileObj) {
        message.error("请选择要上传的 APK 文件");
        return;
      }

      setCreateLoading(true);
      const formData = new FormData();
      formData.append("file", fileList[0].originFileObj);
      formData.append("versionCode", String(values.versionCode));
      formData.append("versionName", values.versionName.trim());
      formData.append("changelog", values.changelog ? values.changelog.trim() : "");
      formData.append("forceUpdate", values.forceUpdate ? "true" : "false");
      formData.append("enabled", values.enabled !== false ? "true" : "false");

      const res = await request("/admin/versions", {
        method: "POST",
        body: formData,
      });

      if (res.code === 200) {
        message.success("新版本发布成功");
        setCreateModalVisible(false);
        createForm.resetFields();
        setFileList([]);
        fetchVersions();
      } else {
        message.error(res.msg || "发布失败");
      }
    } catch (err: any) {
      if (err.message && !err.errorFields) message.error(err.message);
    } finally {
      setCreateLoading(false);
    }
  };

  // 修改版本配置
  const handleEdit = async () => {
    if (!currentVersion) return;
    try {
      const values = await editForm.validateFields();
      setEditLoading(true);
      const res = await request(`/admin/versions/${currentVersion.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          changelog: values.changelog,
          forceUpdate: values.forceUpdate,
          enabled: values.enabled,
        }),
      });

      if (res.code === 200) {
        message.success("版本信息已更新");
        setEditModalVisible(false);
        fetchVersions();
      } else {
        message.error(res.msg || "更新失败");
      }
    } catch (err: any) {
      if (err.message && !err.errorFields) message.error(err.message);
    } finally {
      setEditLoading(false);
    }
  };

  // 快速切换启用状态
  const handleToggleEnabled = async (version: AppVersionItem, enabled: boolean) => {
    try {
      const res = await request(`/admin/versions/${version.id}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled }),
      });
      if (res.code === 200) {
        message.success(enabled ? "版本已启用" : "版本已停用");
        setVersions((prev) =>
          prev.map((item) => (item.id === version.id ? { ...item, enabled } : item))
        );
      } else {
        message.error(res.msg || "更新状态失败");
      }
    } catch (err: any) {
      message.error(err.message || "请求失败");
    }
  };

  // 删除版本
  const handleDelete = async (id: number) => {
    try {
      const res = await request(`/admin/versions/${id}`, {
        method: "DELETE",
      });
      if (res.code === 200) {
        message.success("版本及对应 APK 文件已删除");
        fetchVersions();
      } else {
        message.error(res.msg || "删除失败");
      }
    } catch (err: any) {
      message.error(err.message || "删除失败");
    }
  };

  const getDownloadUrl = (apkUrl: string) => {
    if (!apkUrl) return "#";
    if (apkUrl.startsWith("http")) return apkUrl;
    const base = API_URL.replace(/\/api\/?$/, "");
    return `${base}${apkUrl.startsWith("/") ? "" : "/"}${apkUrl}`;
  };

  const columns = [
    {
      title: "构建编号",
      dataIndex: "versionCode",
      key: "versionCode",
      width: 100,
      render: (val: number) => <Tag color="geekblue">#{val}</Tag>,
    },
    {
      title: "版本名称",
      dataIndex: "versionName",
      key: "versionName",
      width: 120,
      render: (val: string) => <Text strong>{val}</Text>,
    },
    {
      title: "包体大小",
      dataIndex: "fileSize",
      key: "fileSize",
      width: 110,
      render: (val: number) => formatBytes(val),
    },
    {
      title: "强制更新",
      dataIndex: "forceUpdate",
      key: "forceUpdate",
      width: 100,
      render: (val: boolean) =>
        val ? <Tag color="red">强制更新</Tag> : <Tag color="default">可选更新</Tag>,
    },
    {
      title: "启用状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 110,
      render: (val: boolean, record: AppVersionItem) => (
        <Switch
          checked={val}
          checkedChildren="启用"
          unCheckedChildren="停用"
          onChange={(checked) => handleToggleEnabled(record, checked)}
        />
      ),
    },
    {
      title: "更新日志",
      dataIndex: "changelog",
      key: "changelog",
      ellipsis: true,
      render: (val: string) => val || <Text type="secondary">无日志</Text>,
    },
    {
      title: "发布时间",
      dataIndex: "createdAt",
      key: "createdAt",
      width: 180,
      render: (val: number) => (val ? new Date(val).toLocaleString() : "-"),
    },
    {
      title: "操作",
      key: "action",
      width: 200,
      render: (_: any, record: AppVersionItem) => (
        <Space size="small">
          <Tooltip title="下载该版本 APK">
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              href={getDownloadUrl(record.apkUrl)}
              target="_blank"
            >
              下载
            </Button>
          </Tooltip>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setCurrentVersion(record);
              editForm.setFieldsValue({
                changelog: record.changelog,
                forceUpdate: record.forceUpdate,
                enabled: record.enabled,
              });
              setEditModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除此版本？"
            description="删除后，服务器上的 APK 文件将被永久清除且不可恢复！"
            icon={<ExclamationCircleOutlined style={{ color: "red" }} />}
            onConfirm={() => handleDelete(record.id)}
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            🚀 App 版本发布管理
          </Title>
          <Paragraph type="secondary" style={{ margin: 0 }}>
            上传并管理 Android 移动端 APK 安装包，支持强制更新与在线版本启用控制
          </Paragraph>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchVersions} loading={loading}>
            刷新
          </Button>
          <Popconfirm
            title="从 GitHub 同步最新 Release？"
            description="服务端将直连 GitHub 拉取最新 Release APK 与 update.json，下载后自动清理本地旧版 APK（仅保留最新版）。"
            icon={<GithubOutlined style={{ color: "#1677ff" }} />}
            onConfirm={handleSyncGitHub}
            okText="开始同步"
            cancelText="取消"
          >
            <Button icon={<GithubOutlined />} loading={syncGithubLoading}>
              从 GitHub 同步最新版
            </Button>
          </Popconfirm>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              createForm.resetFields();
              setFileList([]);
              setCreateModalVisible(true);
            }}
          >
            发布新版本
          </Button>
        </Space>
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={versions}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 个版本`,
            showSizeChanger: true,
            pageSizeOptions: ["10", "20", "50"],
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      {/* 发布新版本弹窗 */}
      <Modal
        title="发布新版本 APK"
        open={createModalVisible}
        onOk={handleCreate}
        confirmLoading={createLoading}
        onCancel={() => setCreateModalVisible(false)}
        destroyOnClose
        width={560}
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ enabled: true, forceUpdate: false }}
        >
          <Form.Item label="APK 安装包文件" required>
            <Upload.Dragger
              fileList={fileList}
              accept=".apk"
              maxCount={1}
              beforeUpload={(file) => {
                setFileList([file]);
                return false;
              }}
              onRemove={() => setFileList([])}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined style={{ fontSize: 36, color: "#1677ff" }} />
              </p>
              <p className="ant-upload-text">点击或将 APK 安装包拖拽到此处</p>
              <p className="ant-upload-hint">支持 .apk 格式文件，服务端将自动计算 SHA256 与文件大小</p>
            </Upload.Dragger>
          </Form.Item>

          <Form.Item
            name="versionCode"
            label="构建版本编号 (versionCode)"
            rules={[
              { required: true, message: "请输入构建版本编号" },
              { type: "number", min: 1, message: "versionCode 必须为大于 0 的正整数" },
            ]}
            extra="每次发布的编号需严格大于上一版本（如 101, 102）"
          >
            <InputNumber placeholder="如 101" style={{ width: "100%" }} />
          </Form.Item>

          <Form.Item
            name="versionName"
            label="版本名称 (versionName)"
            rules={[
              { required: true, message: "请输入版本名称" },
              { max: 32, message: "最长 32 个字符" },
            ]}
          >
            <Input placeholder="如 1.0.1 或 2.0.0-beta" />
          </Form.Item>

          <Form.Item name="changelog" label="版本更新日志">
            <TextArea
              rows={4}
              placeholder="列出本次版本的主要更新内容，将会在客户端提示更新时向用户展示..."
            />
          </Form.Item>

          <Space size="large">
            <Form.Item name="forceUpdate" valuePropName="checked" label="强制更新">
              <Switch checkedChildren="是" unCheckedChildren="否" />
            </Form.Item>

            <Form.Item name="enabled" valuePropName="checked" label="立即启用">
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 编辑版本弹窗 */}
      <Modal
        title={`编辑版本信息: ${currentVersion?.versionName} (#${currentVersion?.versionCode})`}
        open={editModalVisible}
        onOk={handleEdit}
        confirmLoading={editLoading}
        onCancel={() => setEditModalVisible(false)}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="changelog" label="更新日志">
            <TextArea rows={4} placeholder="版本更新日志" />
          </Form.Item>

          <Space size="large">
            <Form.Item name="forceUpdate" valuePropName="checked" label="强制更新">
              <Switch checkedChildren="是" unCheckedChildren="否" />
            </Form.Item>

            <Form.Item name="enabled" valuePropName="checked" label="启用状态">
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
};
