import React, { useState, useEffect } from "react";
import {
  Card,
  Row,
  Col,
  Form,
  Input,
  Button,
  Avatar,
  Tag,
  Typography,
  Divider,
  message,
  Space,
} from "antd";
import {
  UserOutlined,
  LockOutlined,
  SaveOutlined,
  CrownOutlined,
} from "@ant-design/icons";
import { request } from "../../providers/api";

const { Title, Text, Paragraph } = Typography;

interface UserProfile {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  role: string;
}

export const ProfilePage: React.FC = () => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);

  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  // 监听表单中的 avatar 变动以实现实时头像预览
  const avatarWatch = Form.useWatch("avatar", profileForm);

  const fetchProfile = async () => {
    try {
      const res = await request<UserProfile>("/me");
      if (res.code === 200 && res.data) {
        setUser(res.data);
        profileForm.setFieldsValue({
          username: res.data.username,
          nickname: res.data.nickname,
          avatar: res.data.avatar,
        });
        localStorage.setItem("user", JSON.stringify(res.data));
      }
    } catch (err: any) {
      message.error(err.message || "获取账号信息失败");
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  // 更新个人资料
  const handleUpdateProfile = async () => {
    setProfileLoading(true);
    try {
      const values = await profileForm.validateFields();
      const res = await request<UserProfile>("/me", {
        method: "PUT",
        body: JSON.stringify({
          nickname: values.nickname,
          avatar: values.avatar,
        }),
      });
      if (res.code === 200 && res.data) {
        setUser(res.data);
        localStorage.setItem("user", JSON.stringify(res.data));
        message.success("个人资料已更新");
      } else {
        message.error(res.msg || "资料更新失败");
      }
    } catch (err: any) {
      if (err.message) message.error(err.message);
    } finally {
      setProfileLoading(false);
    }
  };

  // 修改密码
  const handleChangePassword = async () => {
    setPasswordLoading(true);
    try {
      const values = await passwordForm.validateFields();
      const res = await request("/me/password", {
        method: "PUT",
        body: JSON.stringify({
          oldPassword: values.oldPassword,
          newPassword: values.newPassword,
        }),
      });
      if (res.code === 200) {
        message.success("密码修改成功，请牢记您的新密码！");
        passwordForm.resetFields();
      } else {
        message.error(res.msg || "修改密码失败");
      }
    } catch (err: any) {
      if (err.message) message.error(err.message);
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: "0 auto" }}>
      <Title level={3} style={{ marginBottom: 8 }}>
        ⚙️ 账号设置
      </Title>
      <Paragraph type="secondary" style={{ marginBottom: 24 }}>
        查看与修改当前管理员账号的个人资料及安全密码
      </Paragraph>

      <Row gutter={[24, 24]}>
        {/* 左侧：账号资料 */}
        <Col xs={24} md={12}>
          <Card title="基本资料" style={{ height: "100%" }}>
            <div style={{ textAlign: "center", marginBottom: 24 }}>
              <Avatar
                size={80}
                src={avatarWatch || user?.avatar}
                icon={<UserOutlined />}
                style={{ backgroundColor: "#1677ff", marginBottom: 12 }}
              >
                {user?.nickname?.[0] || user?.username?.[0]}
              </Avatar>
              <div>
                <Text strong style={{ fontSize: 18 }}>
                  {user?.nickname || user?.username}
                </Text>
              </div>
              <div style={{ marginTop: 6 }}>
                {user?.role === "admin" ? (
                  <Tag color="blue" icon={<CrownOutlined />}>
                    系统管理员
                  </Tag>
                ) : (
                  <Tag color="default">普通用户</Tag>
                )}
              </div>
            </div>

            <Divider />

            <Form form={profileForm} layout="vertical">
              <Form.Item label="用户名" name="username">
                <Input disabled prefix={<UserOutlined />} />
              </Form.Item>

              <Form.Item
                label="昵称"
                name="nickname"
                rules={[{ max: 24, message: "昵称最长 24 字" }]}
              >
                <Input placeholder="输入您的个性昵称" />
              </Form.Item>

              <Form.Item label="头像 URL" name="avatar">
                <Input placeholder="输入头像直链 URL（选填）" />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  onClick={handleUpdateProfile}
                  loading={profileLoading}
                  block
                >
                  保存个人资料
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        {/* 右侧：修改密码 */}
        <Col xs={24} md={12}>
          <Card title="安全设置 - 修改密码" style={{ height: "100%" }}>
            <Paragraph type="secondary" style={{ marginBottom: 20 }}>
              为了您的账号安全，请定期更换密码。修改成功后其他设备的会话将自动失效。
            </Paragraph>

            <Form form={passwordForm} layout="vertical">
              <Form.Item
                label="原密码"
                name="oldPassword"
                rules={[{ required: true, message: "请输入当前原密码" }]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="当前账号的原密码"
                  autoComplete="current-password"
                />
              </Form.Item>

              <Form.Item
                label="新密码"
                name="newPassword"
                rules={[
                  { required: true, message: "请输入新密码" },
                  { min: 5, max: 64, message: "密码长度需 5-64 位" },
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="设置新密码（5-64 位）"
                  autoComplete="new-password"
                />
              </Form.Item>

              <Form.Item
                label="确认新密码"
                name="confirmPassword"
                dependencies={["newPassword"]}
                rules={[
                  { required: true, message: "请再次输入新密码" },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue("newPassword") === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error("两次输入的新密码不一致"));
                    },
                  }),
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="再次输入新密码"
                  autoComplete="new-password"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  danger
                  icon={<LockOutlined />}
                  onClick={handleChangePassword}
                  loading={passwordLoading}
                  block
                >
                  确认修改密码
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
