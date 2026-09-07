import { useLogin } from "@refinedev/core";
import { Button, Card, Form, Input, Typography, message } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";

interface LoginFormValues {
  username: string;
  password: string;
}

export const Login = () => {
  const { mutate: login, isPending } = useLogin();

  const onFinish = (values: LoginFormValues) => {
    login(values, {
      onSuccess: () => {
        message.success("登录成功");
      },
      onError: (error: any) => {
        message.error(error?.message || "登录失败，请重试");
      },
    });
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#f5f5f5",
      }}
    >
      <Card style={{ width: 400 }}>
        <Typography.Title level={3} style={{ textAlign: "center", marginBottom: 8 }}>
          Melos 管理后台
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: "center", marginBottom: 24 }}>
          请使用管理员账号登录
        </Typography.Paragraph>
        <Form<LoginFormValues> layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名（如 admin）" autoComplete="username" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={isPending}>
              登录
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: "center", fontSize: 13, color: "#8c8c8c" }}>
          仅管理员账号可访问后台，如需开通请联系系统管理员
        </div>
      </Card>
    </div>
  );
};
