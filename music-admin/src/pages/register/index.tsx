import { useRegister } from "@refinedev/core";
import { Button, Card, Form, Input, Typography, message } from "antd";
import { UserOutlined, LockOutlined, SmileOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";

interface RegisterFormValues {
  username: string;
  password: string;
  confirm: string;
  nickname?: string;
}

export const Register = () => {
  const { mutate: register, isLoading } = useRegister();

  const onFinish = (values: RegisterFormValues) => {
    const { confirm, ...payload } = values;
    register(payload, {
      onSuccess: () => {
        message.success("注册成功");
      },
      onError: (error: any) => {
        message.error(error?.message || "注册失败，请重试");
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
          注册账号
        </Typography.Title>
        <Form<RegisterFormValues> layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[
              { required: true, message: "请输入用户名" },
              {
                pattern: /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/,
                message: "2-20 位，字母/数字/下划线/中文",
              },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, max: 64, message: "密码需 6-64 位" },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码（6-64 位）" autoComplete="new-password" />
          </Form.Item>

          <Form.Item
            label="确认密码"
            name="confirm"
            dependencies={["password"]}
            rules={[
              { required: true, message: "请再次输入密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("password") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="确认密码" autoComplete="new-password" />
          </Form.Item>

          <Form.Item
            label="昵称（选填）"
            name="nickname"
            rules={[{ max: 24, message: "昵称最长 24 字" }]}
          >
            <Input prefix={<SmileOutlined />} placeholder="给自己起个名字" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={isLoading}>
              注册
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: "center" }}>
          已有账号？ <Link to="/login">去登录</Link>
        </div>
      </Card>
    </div>
  );
};