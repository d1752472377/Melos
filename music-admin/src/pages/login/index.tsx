import { useLogin } from "@refinedev/core";
import { Button, Card, Form, Input, Typography, message } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";

interface LoginFormValues {
  username: string;
  password: string;
}

export const Login = () => {
  const { mutate: login, isLoading } = useLogin();

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
        <Typography.Title level={3} style={{ textAlign: "center", marginBottom: 24 }}>
          登录
        </Typography.Title>
        <Form<LoginFormValues> layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={isLoading}>
              登录
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: "center" }}>
          还没有账号？ <Link to="/register">去注册</Link>
        </div>
      </Card>
    </div>
  );
};
