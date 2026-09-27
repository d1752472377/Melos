import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  Row,
  Col,
  Button,
  Table,
  Tag,
  Switch,
  Input,
  InputNumber,
  Form,
  Modal,
  Popconfirm,
  message,
  Upload,
  Typography,
  Tooltip,
  Space,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  CloudUploadOutlined,
  DownloadOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EditOutlined,
  AndroidOutlined,
  RocketOutlined,
  WarningOutlined,
  CopyOutlined,
  FileZipOutlined,
  MobileOutlined,
} from "@ant-design/icons";
import { versionApi, AppVersion } from "../../providers/api";

const { Text, Title } = Typography;

export const VersionsPage: React.FC = () => {
  const [versions, setVersions] = useState<AppVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [latestVersion, setLatestVersion] = useState<AppVersion | null>(null);

  // 上传文件与表单状态
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [form] = Form.useForm();

  // 编辑弹窗状态
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingVersion, setEditingVersion] = useState<AppVersion | null>(null);
  const [editForm] = Form.useForm();
  const [updating, setUpdating] = useState(false);

  // 加载列表与最新版本
  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [listRes, latestRes] = await Promise.all([
        versionApi.list({ page, limit: pageSize }),
        versionApi.getLatest(),
      ]);
      setVersions(listRes.items);
      setTotal(listRes.total);
      if (latestRes.code === 200 && latestRes.data) {
        setLatestVersion(latestRes.data);
      } else {
        setLatestVersion(null);
      }
    } catch (err: any) {
      message.error(err.message || "加载版本信息失败");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // 格式化文件大小
  const formatSize = (bytes: number) => {
    if (!bytes || bytes <= 0) return "0 MB";
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(2)} MB`;
  };

  // 复制文本
  const copyText = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success("校验码已复制到剪贴板");
  };

  // 智能解析 APK 文件名并自动填充
  const handleBeforeUpload = (file: File) => {
    const isApk = file.name.toLowerCase().endsWith(".apk");
    if (!isApk) {
      message.error("请上传 .apk 格式的文件！");
      return Upload.LIST_IGNORE;
    }
    setSelectedFile(file);

    // 智能提取版本名：例如 v2.0.0、2.0.0
    const vMatch = file.name.match(/v?(\d+\.\d+(\.\d+)?)/i);
    if (vMatch) {
      const parsedName = `v${vMatch[1]}`;
      form.setFieldsValue({ versionName: parsedName });

      // 智能生成构建号：例如 2.0.0 -> 200, 2.1.3 -> 213
      const parts = vMatch[1].split(".").map((p) => parseInt(p, 10) || 0);
      if (parts.length >= 2) {
        const major = parts[0];
        const minor = parts[1];
        const patch = parts[2] || 0;
        const calcCode = major * 100 + minor * 10 + patch;
        if (calcCode > 0 && !form.getFieldValue("versionCode")) {
          form.setFieldsValue({ versionCode: calcCode });
        }
      }
    }
    message.info(`已选定安装包: ${file.name} (${formatSize(file.size)})`);
    return false;
  };

  // 提交发布新版本
  const handlePublish = async () => {
    if (!selectedFile) {
      message.warning("请先拖拽或点击上传 APK 安装包！");
      return;
    }

    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    setPublishing(true);
    try {
      const formData = new FormData();
      // 先追加文本元数据，再追加大文件流
      formData.append("versionCode", String(values.versionCode));
      formData.append("versionName", values.versionName.trim());
      formData.append("forceUpdate", values.forceUpdate ? "true" : "false");
      formData.append("enabled", "true");
      formData.append("changelog", (values.changelog || "").trim());
      formData.append("file", selectedFile);

      const res = await versionApi.create(formData);
      if (res.code === 200) {
        message.success(`版本 ${values.versionName} 发布成功！`);
        form.resetFields();
        setSelectedFile(null);
        fetchData();
      } else {
        message.error(res.msg || "发布失败");
      }
    } catch (err: any) {
      console.error("上传错误详情:", err);
      const errMsg = err.message || "";
      if (errMsg.includes("ABORTED") || errMsg.includes("Failed to fetch") || errMsg.includes("NetworkError")) {
        message.error("上传失败：网络连接中断，请确认后端服务正在运行且允许大文件上传！");
      } else {
        message.error(errMsg || "上传发布失败，请检查网络与后端服务");
      }
    } finally {
      setPublishing(false);
    }
  };

  // 快捷切换启停状态
  const handleToggleEnabled = async (record: AppVersion, checked: boolean) => {
    try {
      const res = await versionApi.update(record.id, { enabled: checked });
      if (res.code === 200) {
        message.success(checked ? "已启用该版本" : "已下线该版本");
        setVersions((prev) =>
          prev.map((v) => (v.id === record.id ? { ...v, enabled: checked } : v))
        );
        versionApi.getLatest().then((r) => {
          if (r.code === 200) setLatestVersion(r.data || null);
        });
      } else {
        message.error(res.msg || "操作失败");
      }
    } catch (err: any) {
      message.error(err.message || "更新状态失败");
    }
  };

  // 打开编辑弹窗
  const handleOpenEdit = (record: AppVersion) => {
    setEditingVersion(record);
    editForm.setFieldsValue({
      forceUpdate: record.force_update ?? record.forceUpdate,
      changelog: record.changelog || "",
    });
    setEditModalVisible(true);
  };

  // 提交编辑更新
  const handleSaveEdit = async () => {
    if (!editingVersion) return;
    try {
      const values = await editForm.validateFields();
      setUpdating(true);
      const res = await versionApi.update(editingVersion.id, {
        forceUpdate: values.forceUpdate,
        changelog: values.changelog,
      });
      if (res.code === 200) {
        message.success("更新成功");
        setEditModalVisible(false);
        fetchData();
      } else {
        message.error(res.msg || "更新失败");
      }
    } catch (err: any) {
      message.error(err.message || "保存失败");
    } finally {
      setUpdating(false);
    }
  };

  // 删除版本
  const handleDelete = async (id: number) => {
    try {
      const res = await versionApi.delete(id);
      if (res.code === 200) {
        message.success("该版本及本地安装包已删除");
        fetchData();
      } else {
        message.error(res.msg || "删除失败");
      }
    } catch (err: any) {
      message.error(err.message || "删除请求失败");
    }
  };

  // 表格列定义
  const columns: ColumnsType<AppVersion> = [
    {
      title: "版本名称",
      key: "version_name",
      width: 160,
      render: (_, r) => (
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 6,
              background: "#eef2ff",
              color: "#4f46e5",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 16,
              fontWeight: 700,
            }}
          >
            <AndroidOutlined />
          </div>
          <div>
            <div style={{ fontWeight: 600, fontSize: 14, color: "#0f172a" }}>
              {r.version_name || r.versionName}
            </div>
            {latestVersion?.id === r.id && (
              <Tag color="success" style={{ margin: 0, fontSize: 11, padding: "0 4px", lineHeight: "16px" }}>
                当前正式版
              </Tag>
            )}
          </div>
        </div>
      ),
    },
    {
      title: "构建号",
      key: "version_code",
      width: 110,
      render: (_, r) => (
        <Text code style={{ fontSize: 13 }}>
          {r.version_code || r.versionCode}
        </Text>
      ),
    },
    {
      title: "安装包大小",
      key: "file_size",
      width: 120,
      render: (_, r) => (
        <span style={{ fontWeight: 500, color: "#1e293b" }}>
          {formatSize(r.file_size || r.fileSize || 0)}
        </span>
      ),
    },
    {
      title: "SHA-256 校验码",
      key: "hash_sha256",
      render: (_, r) => {
        const hash = r.hash_sha256 || r.hashSha256 || "";
        if (!hash) return "-";
        return (
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Tooltip title={hash}>
              <Text style={{ fontSize: 12, fontFamily: "monospace", color: "#64748b" }}>
                {hash.slice(0, 16)}...
              </Text>
            </Tooltip>
            <Tooltip title="点击复制完整校验码">
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined style={{ fontSize: 12, color: "#94a3b8" }} />}
                onClick={() => copyText(hash)}
                style={{ padding: "0 4px", height: 20 }}
              />
            </Tooltip>
          </div>
        );
      },
    },
    {
      title: "更新策略",
      key: "policy",
      width: 110,
      render: (_, r) => {
        const isForce = r.force_update ?? r.forceUpdate;
        return isForce ? (
          <Tag color="error">强制更新</Tag>
        ) : (
          <Tag color="default">普通更新</Tag>
        );
      },
    },
    {
      title: "启用状态",
      key: "enabled",
      width: 110,
      render: (_, r) => (
        <Switch
          checked={r.enabled}
          checkedChildren="已启用"
          unCheckedChildren="已下线"
          onChange={(checked) => handleToggleEnabled(r, checked)}
        />
      ),
    },
    {
      title: "发布时间",
      dataIndex: "created_at",
      key: "created_at",
      width: 170,
      render: (v, r) => (
        <span style={{ fontSize: 13, color: "#64748b" }}>
          {v || r.createdAt || "-"}
        </span>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 140,
      align: "right",
      render: (_, r) => {
        const downloadUrl = versionApi.getDownloadUrl(r.apk_url || r.apkUrl);
        return (
          <Space size={4}>
            <Tooltip title="下载 APK 文件">
              <Button
                type="text"
                size="small"
                icon={<DownloadOutlined style={{ color: "#4f46e5" }} />}
                href={downloadUrl}
                target="_blank"
                download
              />
            </Tooltip>
            <Tooltip title="编辑日志与更新策略">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined style={{ color: "#3b82f6" }} />}
                onClick={() => handleOpenEdit(r)}
              />
            </Tooltip>
            <Popconfirm
              title="确定要删除该版本吗？"
              description="删除后服务器上的安装包文件也将被彻底移除！"
              okText="确定删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleDelete(r.id)}
            >
              <Tooltip title="删除版本">
                <Button type="text" danger size="small" icon={<DeleteOutlined />} />
              </Tooltip>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
      {/* 顶部简明 Header */}
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
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Title level={3} style={{ margin: 0, fontWeight: 700, color: "#0f172a" }}>
              客户端版本管理
            </Title>
            {latestVersion ? (
              <Tag color="success" style={{ fontSize: 13, padding: "2px 8px" }}>
                当前最新正式版: {latestVersion.version_name || latestVersion.versionName} (构建号:{" "}
                {latestVersion.version_code || latestVersion.versionCode})
              </Tag>
            ) : (
              <Tag color="warning">暂无已启用的发布版本</Tag>
            )}
          </div>
          <Text style={{ color: "#64748b", fontSize: 14, marginTop: 4, display: "inline-block" }}>
            管理 Android 客户端安装包分发、强制更新策略与发布记录
          </Text>
        </div>

        <Space size={10}>
          <Button
            icon={<MobileOutlined />}
            href="/download"
            target="_blank"
          >
            打开公开下载页
          </Button>
          <Button icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
            刷新列表
          </Button>
          {latestVersion && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              href={versionApi.getDownloadUrl(latestVersion.apk_url || latestVersion.apkUrl)}
              target="_blank"
              download
              style={{ background: "#4f46e5", borderColor: "#4f46e5" }}
            >
              下载最新 APK ({formatSize(latestVersion.file_size || latestVersion.fileSize)})
            </Button>
          )}
        </Space>
      </div>

      {/* 发布新版本卡片 */}
      <Card
        bordered
        title={
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <RocketOutlined style={{ color: "#4f46e5", fontSize: 18 }} />
            <span style={{ fontSize: 16, fontWeight: 700, color: "#0f172a" }}>发布新版本</span>
          </div>
        }
        style={{
          borderRadius: 10,
          borderColor: "#e2e8f0",
        }}
      >
        <Form form={form} layout="vertical">
          <Row gutter={[24, 24]}>
            {/* 左侧上传区 */}
            <Col xs={24} lg={10}>
              <Form.Item
                label={<span style={{ fontWeight: 600 }}>APK 安装包文件</span>}
                required
                help={!selectedFile ? "请选择 .apk 文件上传（上限 200MB）" : undefined}
              >
                <Upload.Dragger
                  name="file"
                  accept=".apk"
                  multiple={false}
                  showUploadList={false}
                  beforeUpload={handleBeforeUpload}
                  style={{
                    background: "#f8fafc",
                    border: "2px dashed #cbd5e1",
                    borderRadius: 10,
                    padding: "24px 16px",
                  }}
                >
                  <p className="ant-upload-drag-icon" style={{ marginBottom: 12 }}>
                    {selectedFile ? (
                      <FileZipOutlined style={{ color: "#10b981", fontSize: 40 }} />
                    ) : (
                      <CloudUploadOutlined style={{ color: "#4f46e5", fontSize: 40 }} />
                    )}
                  </p>
                  {selectedFile ? (
                    <div>
                      <p style={{ fontWeight: 600, color: "#0f172a", margin: "0 0 4px" }}>
                        {selectedFile.name}
                      </p>
                      <Tag color="success">文件大小: {formatSize(selectedFile.size)}</Tag>
                      <p style={{ fontSize: 12, color: "#10b981", marginTop: 8 }}>
                        ✓ 文件已就绪，填写右侧参数后即可发布
                      </p>
                    </div>
                  ) : (
                    <div>
                      <p style={{ fontWeight: 600, color: "#1e293b", margin: "0 0 4px" }}>
                        点击或拖拽 .apk 文件到此处
                      </p>
                      <p style={{ fontSize: 12, color: "#64748b", margin: 0 }}>
                        支持标准 Android APK 文件，上限 200MB
                      </p>
                    </div>
                  )}
                </Upload.Dragger>
              </Form.Item>
            </Col>

            {/* 右侧发布表单 */}
            <Col xs={24} lg={14}>
              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="versionName"
                    label={<span style={{ fontWeight: 600 }}>版本名称</span>}
                    rules={[{ required: true, message: "请输入版本名称，如 v2.0.0" }]}
                  >
                    <Input placeholder="例如: v2.0.0" style={{ height: 38 }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="versionCode"
                    label={<span style={{ fontWeight: 600 }}>版本构建号</span>}
                    rules={[
                      { required: true, message: "请输入正整数版本构建号，如 200" },
                      {
                        validator: (_, value) =>
                          value && Number.isInteger(value) && value > 0
                            ? Promise.resolve()
                            : Promise.reject(new Error("必须为大于 0 的正整数")),
                      },
                    ]}
                  >
                    <InputNumber
                      placeholder="例如: 200"
                      min={1}
                      style={{ width: "100%", height: 38 }}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "12px 16px",
                  background: "#fff1f2",
                  border: "1px solid #fecdd3",
                  borderRadius: 8,
                  marginBottom: 16,
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <WarningOutlined style={{ color: "#e11d48", fontSize: 18 }} />
                  <div>
                    <div style={{ fontWeight: 600, color: "#9f1239", fontSize: 13 }}>
                      强制更新策略
                    </div>
                    <div style={{ fontSize: 12, color: "#be123c" }}>
                      开启后，低于此构建号的旧客户端启动时将强制弹窗提示升级
                    </div>
                  </div>
                </div>
                <Form.Item name="forceUpdate" valuePropName="checked" noStyle initialValue={false}>
                  <Switch checkedChildren="强制" unCheckedChildren="普通" />
                </Form.Item>
              </div>

              <Form.Item
                name="changelog"
                label={<span style={{ fontWeight: 600 }}>更新日志</span>}
              >
                <Input.TextArea
                  rows={3}
                  placeholder="- 新增：无损音频解码支持&#10;- 优化：播放流畅度与网络缓存&#10;- 修复：已知问题"
                  style={{ fontSize: 13 }}
                />
              </Form.Item>

              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <Button
                  type="primary"
                  icon={<RocketOutlined />}
                  loading={publishing}
                  size="large"
                  onClick={handlePublish}
                  style={{
                    background: "#4f46e5",
                    borderColor: "#4f46e5",
                    fontWeight: 600,
                    padding: "0 32px",
                  }}
                >
                  {publishing ? "正在上传并发布..." : "立即发布 APK"}
                </Button>
              </div>
            </Col>
          </Row>
        </Form>
      </Card>

      {/* 版本发布历史表格 */}
      <Card
        bordered
        title={
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <AndroidOutlined style={{ color: "#4f46e5" }} />
            <span style={{ fontWeight: 700 }}>版本发布历史</span>
          </div>
        }
        extra={<Text type="secondary">共 {total} 个发布记录</Text>}
        style={{
          borderRadius: 10,
          borderColor: "#e2e8f0",
        }}
      >
        <Table<AppVersion>
          rowKey="id"
          columns={columns}
          dataSource={versions}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      {/* 编辑弹窗 */}
      <Modal
        title={`修改版本配置 - ${editingVersion?.version_name || editingVersion?.versionName}`}
        open={editModalVisible}
        onOk={handleSaveEdit}
        onCancel={() => setEditModalVisible(false)}
        confirmLoading={updating}
        okText="保存更改"
        cancelText="取消"
        okButtonProps={{ style: { background: "#4f46e5", borderColor: "#4f46e5" } }}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="forceUpdate"
            label={<span style={{ fontWeight: 600 }}>强制更新策略</span>}
            valuePropName="checked"
          >
            <Switch checkedChildren="强制更新" unCheckedChildren="普通更新" />
          </Form.Item>
          <Form.Item
            name="changelog"
            label={<span style={{ fontWeight: 600 }}>更新日志</span>}
          >
            <Input.TextArea rows={6} style={{ fontSize: 13 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
