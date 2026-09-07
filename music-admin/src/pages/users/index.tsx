import React, { useState, useEffect, useCallback } from "react";
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Select,
  Modal,
  Form,
  Popconfirm,
  message,
  Avatar,
  Typography,
} from "antd";
import {
  UserOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  KeyOutlined,
  EditOutlined,
  DeleteOutlined,
  CrownOutlined,
} from "@ant-design/icons";
import { request } from "../../providers/api";

const { Title, Text } = Typography;

interface UserItem {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  role: "admin" | "user";
  created_at: number;
  updated_at: number;
}

export const UsersPage: React.FC = () => {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("");

  // 模态框状态
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [resetPassModalVisible, setResetPassModalVisible] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserItem | null>(null);

  // 表单实例
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [resetPassForm] = Form.useForm();

  // 当前登录账号信息
  const loginUser = (() => {
    try {
      return JSON.parse(localStorage.getItem("user") || "{}");
    } catch {
      return {};
    }
  })();

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        limit: String(pageSize),
      });
      if (search.trim()) params.append("search", search.trim());
      if (roleFilter) params.append("role", roleFilter);

      const res = await request<{ items: UserItem[]; total: number }>(`/admin/users?${params.toString()}`);
      if (res.code === 200 && res.data) {
        setUsers(res.data.items || []);
        setTotal(res.data.total || 0);
      } else {
        message.error(res.msg || "加载用户列表失败");
      }
    } catch (err: any) {
      message.error(err.message || "请求失败");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, roleFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // 新建用户
  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields();
      const res = await request("/admin/users", {
        method: "POST",
        body: JSON.stringify(values),
      });
      if (res.code === 200) {
        message.success("用户创建成功");
        setCreateModalVisible(false);
        createForm.resetFields();
        fetchUsers();
      } else {
        message.error(res.msg || "创建失败");
      }
    } catch (err: any) {
      if (err.message) message.error(err.message);
    }
  };

  // 编辑用户
  const handleEdit = async () => {
    if (!currentUser) return;
    try {
      const values = await editForm.validateFields();
      const res = await request(`/admin/users/${currentUser.id}`, {
        method: "PUT",
        body: JSON.stringify(values),
      });
      if (res.code === 200) {
        message.success("用户信息修改成功");
        setEditModalVisible(false);
        fetchUsers();
      } else {
        message.error(res.msg || "修改失败");
      }
    } catch (err: any) {
      if (err.message) message.error(err.message);
    }
  };

  // 重置密码
  const handleResetPassword = async () => {
    if (!currentUser) return;
    try {
      const values = await resetPassForm.validateFields();
      const res = await request(`/admin/users/${currentUser.id}`, {
        method: "PUT",
        body: JSON.stringify({ password: values.newPassword }),
      });
      if (res.code === 200) {
        message.success("密码重置成功");
        setResetPassModalVisible(false);
        resetPassForm.resetFields();
      } else {
        message.error(res.msg || "重置密码失败");
      }
    } catch (err: any) {
      if (err.message) message.error(err.message);
    }
  };

  // 删除用户
  const handleDelete = async (id: number) => {
    try {
      const res = await request(`/admin/users/${id}`, {
        method: "DELETE",
      });
      if (res.code === 200) {
        message.success("用户已删除");
        fetchUsers();
      } else {
        message.error(res.msg || "删除失败");
      }
    } catch (err: any) {
      message.error(err.message || "删除失败");
    }
  };

  const columns = [
    {
      title: "ID",
      dataIndex: "id",
      key: "id",
      width: 70,
    },
    {
      title: "头像",
      dataIndex: "avatar",
      key: "avatar",
      width: 70,
      render: (avatar: string, record: UserItem) => (
        <Avatar src={avatar} icon={<UserOutlined />}>
          {record.nickname?.[0] || record.username?.[0]}
        </Avatar>
      ),
    },
    {
      title: "用户名",
      dataIndex: "username",
      key: "username",
      render: (text: string, record: UserItem) => (
        <Space>
          <Text strong>{text}</Text>
          {record.id === loginUser.id && <Tag color="gold">当前账号</Tag>}
        </Space>
      ),
    },
    {
      title: "昵称",
      dataIndex: "nickname",
      key: "nickname",
      render: (text: string) => text || "-",
    },
    {
      title: "角色权限",
      dataIndex: "role",
      key: "role",
      width: 120,
      render: (role: string) =>
        role === "admin" ? (
          <Tag color="blue" icon={<CrownOutlined />}>
            管理员
          </Tag>
        ) : (
          <Tag color="default">普通用户</Tag>
        ),
    },
    {
      title: "注册时间",
      dataIndex: "created_at",
      key: "created_at",
      width: 180,
      render: (val: number) => (val ? new Date(val).toLocaleString() : "-"),
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      render: (_: any, record: UserItem) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setCurrentUser(record);
              editForm.setFieldsValue({
                nickname: record.nickname,
                avatar: record.avatar,
                role: record.role,
              });
              setEditModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<KeyOutlined />}
            onClick={() => {
              setCurrentUser(record);
              resetPassForm.resetFields();
              setResetPassModalVisible(true);
            }}
          >
            重置密码
          </Button>
          <Popconfirm
            title="确认删除该用户？"
            description="删除后该用户的所有收藏、歌单及历史数据均将被级联删除且不可恢复！"
            onConfirm={() => handleDelete(record.id)}
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            disabled={record.id === loginUser.id}
          >
            <Button
              type="link"
              danger
              size="small"
              icon={<DeleteOutlined />}
              disabled={record.id === loginUser.id}
            >
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
            👥 用户管理
          </Title>
          <Text type="secondary">管理系统内所有账号及其权限角色</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchUsers} loading={loading}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              createForm.resetFields();
              setCreateModalVisible(true);
            }}
          >
            新建用户
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size="middle">
          <Input
            placeholder="搜索用户名或昵称"
            prefix={<SearchOutlined />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onPressEnter={() => {
              setPage(1);
              fetchUsers();
            }}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="角色筛选"
            value={roleFilter}
            onChange={(val) => {
              setRoleFilter(val);
              setPage(1);
            }}
            style={{ width: 140 }}
            options={[
              { label: "全部角色", value: "" },
              { label: "管理员", value: "admin" },
              { label: "普通用户", value: "user" },
            ]}
          />
          <Button type="primary" onClick={() => { setPage(1); fetchUsers(); }}>
            筛选
          </Button>
        </Space>
      </Card>

      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 位用户`,
            showSizeChanger: true,
            pageSizeOptions: ["10", "20", "50"],
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      {/* 新建用户弹窗 */}
      <Modal
        title="新建用户"
        open={createModalVisible}
        onOk={handleCreate}
        onCancel={() => setCreateModalVisible(false)}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" initialValues={{ role: "user" }}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: "请输入用户名" },
              { pattern: /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/, message: "2-20 位字符（字母/数字/下划线/中文）" },
            ]}
          >
            <Input placeholder="输入用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: "请输入初始密码" },
              { min: 5, max: 64, message: "密码需 5-64 位" },
            ]}
          >
            <Input.Password placeholder="输入初始密码（5-64 位）" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="用户昵称（选填）"
            rules={[{ max: 24, message: "昵称最长 24 字" }]}
          >
            <Input placeholder="用户昵称" />
          </Form.Item>
          <Form.Item name="role" label="权限角色" rules={[{ required: true }]}>
            <Select
              options={[
                { label: "普通用户 (user) - 仅可使用客户端同步", value: "user" },
                { label: "管理员 (admin) - 可登录管理后台", value: "admin" },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑用户弹窗 */}
      <Modal
        title={`编辑用户: ${currentUser?.username}`}
        open={editModalVisible}
        onOk={handleEdit}
        onCancel={() => setEditModalVisible(false)}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="nickname"
            label="用户昵称"
            rules={[{ max: 24, message: "昵称最长 24 字" }]}
          >
            <Input placeholder="用户昵称" />
          </Form.Item>
          <Form.Item name="avatar" label="头像 URL">
            <Input placeholder="头像图片直链（选填）" />
          </Form.Item>
          <Form.Item name="role" label="权限角色" rules={[{ required: true }]}>
            <Select
              options={[
                { label: "普通用户 (user) - 仅可使用客户端同步", value: "user" },
                { label: "管理员 (admin) - 可登录管理后台", value: "admin" },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码弹窗 */}
      <Modal
        title={`重置密码: ${currentUser?.username}`}
        open={resetPassModalVisible}
        onOk={handleResetPassword}
        onCancel={() => setResetPassModalVisible(false)}
        destroyOnClose
      >
        <Form form={resetPassForm} layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: "请输入新密码" },
              { min: 5, max: 64, message: "密码需 5-64 位" },
            ]}
          >
            <Input.Password placeholder="输入新密码（5-64 位）" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={["newPassword"]}
            rules={[
              { required: true, message: "请确认新密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("newPassword") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
