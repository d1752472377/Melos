import React, { useState, useEffect } from "react";
import {
  Card,
  Button,
  Tag,
  Typography,
  Space,
  Spin,
  QRCode,
  Tooltip,
  message,
} from "antd";
import {
  AndroidOutlined,
  DownloadOutlined,
  SafetyCertificateOutlined,
  CopyOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  MobileOutlined,
} from "@ant-design/icons";
import { versionApi, AppVersion } from "../../providers/api";

const { Title, Text, Paragraph } = Typography;

export const DownloadPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState<AppVersion | null>(null);

  useEffect(() => {
    versionApi
      .getLatest()
      .then((res) => {
        if (res.code === 200 && res.data) {
          setVersion(res.data);
        } else {
          setVersion(null);
        }
      })
      .catch(() => {
        setVersion(null);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const formatSize = (bytes: number) => {
    if (!bytes || bytes <= 0) return "0 MB";
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  };

  const copyText = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success("已复制校验码");
  };

  const downloadUrl = version
    ? versionApi.getDownloadUrl(version.apk_url || version.apkUrl)
    : "";

  // 当前网页 URL（用于在 PC 端扫码时手机打开本页面下载）
  const currentPageUrl = typeof window !== "undefined" ? window.location.href : "";

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "24px 16px",
        fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: 680,
          display: "flex",
          flexDirection: "column",
          gap: 20,
        }}
      >
        {/* 顶部品牌区 */}
        <div style={{ textAlign: "center", marginBottom: 8 }}>
          <div
            style={{
              width: 72,
              height: 72,
              borderRadius: 18,
              background: "linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)",
              color: "#ffffff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              margin: "0 auto 16px",
              boxShadow: "0 10px 25px -5px rgba(79, 70, 229, 0.4)",
              fontSize: 36,
              fontWeight: 800,
            }}
          >
            M
          </div>
          <Title level={2} style={{ margin: "0 0 6px", color: "#0f172a", fontWeight: 800 }}>
            Melos 音乐
          </Title>
          <Text style={{ color: "#64748b", fontSize: 15 }}>
            无损音质 · 跨端云同步 · 纯净音频体验
          </Text>
        </div>

        {/* 内容主体卡片 */}
        <Card
          bordered={false}
          style={{
            borderRadius: 16,
            boxShadow: "0 10px 30px -5px rgba(15, 23, 42, 0.08)",
            padding: "8px 12px",
            background: "#ffffff",
          }}
        >
          {loading ? (
            <div style={{ textAlign: "center", padding: "60px 0" }}>
              <Spin size="large" />
              <div style={{ marginTop: 16, color: "#64748b", fontSize: 14 }}>
                正在获取最新版本信息...
              </div>
            </div>
          ) : version ? (
            <div>
              {/* 版本概览条 */}
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  flexWrap: "wrap",
                  gap: 12,
                  paddingBottom: 20,
                  borderBottom: "1px solid #f1f5f9",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: 12,
                      background: "#eef2ff",
                      color: "#4f46e5",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 22,
                    }}
                  >
                    <AndroidOutlined />
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <span style={{ fontSize: 18, fontWeight: 700, color: "#0f172a" }}>
                        {version.version_name || version.versionName}
                      </span>
                      <Tag color="success" style={{ margin: 0 }}>
                        正式发布版
                      </Tag>
                    </div>
                    <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>
                      构建编号: {version.version_code || version.versionCode} · 文件大小:{" "}
                      {formatSize(version.file_size || version.fileSize)}
                    </div>
                  </div>
                </div>

                <div style={{ fontSize: 12, color: "#94748b", display: "flex", alignItems: "center", gap: 4 }}>
                  <ClockCircleOutlined />
                  <span>发布时间: {version.created_at || version.createdAt || "-"}</span>
                </div>
              </div>

              {/* 下载主操作区（适配手机与PC） */}
              <div
                style={{
                  display: "flex",
                  flexDirection: "row",
                  alignItems: "center",
                  justifyContent: "space-between",
                  flexWrap: "wrap",
                  gap: 24,
                  padding: "24px 0",
                }}
              >
                {/* 左侧：下载按钮与安全提示 */}
                <div style={{ flex: "1 1 280px", display: "flex", flexDirection: "column", gap: 16 }}>
                  <Button
                    type="primary"
                    size="large"
                    icon={<DownloadOutlined style={{ fontSize: 20 }} />}
                    href={downloadUrl}
                    download
                    style={{
                      height: 52,
                      fontSize: 16,
                      fontWeight: 700,
                      borderRadius: 12,
                      background: "#4f46e5",
                      borderColor: "#4f46e5",
                      boxShadow: "0 4px 14px 0 rgba(79, 70, 229, 0.35)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: 8,
                    }}
                  >
                    立即下载 Android 安装包
                  </Button>

                  <div style={{ display: "flex", flexDirection: "column", gap: 6, fontSize: 13, color: "#64748b" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      <CheckCircleOutlined style={{ color: "#10b981" }} />
                      <span>官方原生安装包 · 纯净无广告</span>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      <SafetyCertificateOutlined style={{ color: "#3b82f6" }} />
                      <span>适用 Android 8.0 及以上版本</span>
                    </div>
                  </div>
                </div>

                {/* 右侧：PC 端扫码下载二维码（移动端仍可查看） */}
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    padding: "16px 20px",
                    background: "#f8fafc",
                    borderRadius: 12,
                    border: "1px solid #e2e8f0",
                    flexShrink: 0,
                  }}
                >
                  <QRCode
                    value={currentPageUrl || downloadUrl}
                    size={120}
                    bordered={false}
                    color="#1e293b"
                  />
                  <div style={{ display: "flex", alignItems: "center", gap: 4, marginTop: 8, fontSize: 12, color: "#64748b" }}>
                    <MobileOutlined />
                    <span>手机扫码直接下载</span>
                  </div>
                </div>
              </div>

              {/* 更新日志 */}
              {(version.changelog || "").trim() && (
                <div
                  style={{
                    background: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    borderRadius: 10,
                    padding: 16,
                    marginTop: 8,
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8, color: "#334155", fontWeight: 600 }}>
                    <FileTextOutlined style={{ color: "#4f46e5" }} />
                    <span>更新日志</span>
                  </div>
                  <div
                    style={{
                      whiteSpace: "pre-wrap",
                      fontSize: 13,
                      lineHeight: "1.7",
                      color: "#475569",
                      fontFamily: "monospace",
                    }}
                  >
                    {version.changelog}
                  </div>
                </div>
              )}

              {/* SHA-256 安全校验 */}
              {(version.hash_sha256 || version.hashSha256) && (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    marginTop: 16,
                    padding: "10px 14px",
                    background: "#f1f5f9",
                    borderRadius: 8,
                    fontSize: 12,
                    color: "#64748b",
                    flexWrap: "wrap",
                    gap: 8,
                  }}
                >
                  <span style={{ fontFamily: "monospace" }}>
                    SHA-256: {(version.hash_sha256 || version.hashSha256).slice(0, 24)}...
                  </span>
                  <Tooltip title="复制完整 SHA-256 校验码">
                    <Button
                      type="link"
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copyText(version.hash_sha256 || version.hashSha256)}
                      style={{ padding: 0, fontSize: 12, color: "#4f46e5" }}
                    >
                      复制校验码
                    </Button>
                  </Tooltip>
                </div>
              )}
            </div>
          ) : (
            <div style={{ textAlign: "center", padding: "48px 0" }}>
              <AndroidOutlined style={{ fontSize: 48, color: "#cbd5e1", marginBottom: 16 }} />
              <Title level={4} style={{ color: "#475569", marginBottom: 8 }}>
                暂无已发布的版本
              </Title>
              <Paragraph style={{ color: "#94a3b8", fontSize: 14 }}>
                当前服务器尚未启用任何客户端安装包，请稍后刷新或联系管理员发布。
              </Paragraph>
              <Button type="default" onClick={() => window.location.reload()}>
                刷新页面
              </Button>
            </div>
          )}
        </Card>

        {/* 底部版权与说明 */}
        <div style={{ textAlign: "center", fontSize: 12, color: "#94a3b8" }}>
          © {new Date().getFullYear()} Melos Music. 保留所有权利。
        </div>
      </div>
    </div>
  );
};
