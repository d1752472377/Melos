import React, { useState, useEffect, useCallback, useRef } from "react";
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
  Tooltip,
} from "antd";
import {
  UserOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  KeyOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  CrownOutlined,
  InfoCircleOutlined,
  MailOutlined,
  CopyOutlined,
} from "@ant-design/icons";
import { useGetIdentity } from "@refinedev/core";
import { useSearchParams } from "react-router-dom";
import { request } from "../../providers/api";
import { UserDetailPage } from "../user-detail";

const { Title, Text } = Typography;

interface UserItem {
  id: number;
  username: string;
  nickname: string;
  avatar: string;
  email?: string;
  role: "admin" | "user";
  created_at: number;
  updated_at: number;
  last_active_at?: number;
}

interface IdentityUser {
  id: number;
  name: string;
  avatar?: string;
  role?: string;
}

export const UsersPage: React.FC = () => {
  const { data: identityUser } = useGetIdentity<IdentityUser>();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 搜索输入状态与实际触发查询的状态分离
  const [searchParams] = useSearchParams();
  const initialQuery = searchParams.get("q") || "";
  const [searchInput, setSearchInput] = useState(initialQuery);
  const [searchQuery, setSearchQuery] = useState(initialQuery);
  const [roleFilter, setRoleFilter] = useState<string>("");

  // 顶部导航条的搜索会带 ?q= 跳过来：URL 变化时同步到查询条件
  useEffect(() => {
    const q = searchParams.get("q") || "";
    setSearchInput(q);
    setSearchQuery(q);
    setPage(1);
  }, [searchParams]);

  // 模态框状态与 loading
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);

  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editLoading, setEditLoading] = useState(false);

  const [resetPassModalVisible, setResetPassModalVisible] = useState(false);
  const [resetPassLoading, setResetPassLoading] = useState(false);

  const [currentUser, setCurrentUser] = useState<UserItem | null>(null);

  // 用户详情弹窗
  const [detailUserId, setDetailUserId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  // 表单实例
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [resetPassForm] = Form.useForm();

  // 请求版本号防止竞态条件覆盖
  const requestIdRef = useRef(0);

  const fetchUsers = useCallback(async () => {
    const currentRequestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        limit: String(pageSize),
      });
      if (searchQuery.trim()) params.append("search", searchQuery.trim());
      if (roleFilter) params.append("role", roleFilter);

      const res = await request<{ items: UserItem[]; total: number }>(`/admin/users?${params.toString()}`);
      if (currentRequestId === requestIdRef.current) {
        if (res.code === 200 && res.data) {
          setUsers(res.data.items || []);
          setTotal(res.data.total || 0);
        } else {
          message.error(res.msg || "加载用户列表失败");
        }
      }
    } catch (err: any) {
      if (currentRequestId === requestIdRef.current) {
        message.error(err.message || "请求失败");
      }
    } finally {
      if (currentRequestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [page, pageSize, searchQuery, roleFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // 执行搜索
  const handleApplySearch = () => {
    setPage(1);
    setSearchQuery(searchInput);
  };

  // 重置搜索
  const handleResetSearch = () => {
    setSearchInput("");
    setSearchQuery("");
    setRoleFilter("");
    setPage(1);
  };

  // 新建用户
  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields();
      setCreateLoading(true);
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
      if (err.message && !err.errorFields) message.error(err.message);
    } finally {
      setCreateLoading(false);
    }
  };

  // 编辑用户
  const handleEdit = async () => {
    if (!currentUser) return;
    try {
      const values = await editForm.validateFields();
      setEditLoading(true);
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
      if (err.message && !err.errorFields) message.error(err.message);
    } finally {
      setEditLoading(false);
    }
  };

  // 重置密码
  const handleResetPassword = async () => {
    if (!currentUser) return;
    try {
      const values = await resetPassForm.validateFields();
      setResetPassLoading(true);
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
      if (err.message && !err.errorFields) message.error(err.message);
    } finally {
      setResetPassLoading(false);
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

  const isCurrentLoginUser = (userItem: UserItem) => {
    return identityUser?.id === userItem.id;
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
          {isCurrentLoginUser(record) && <Tag color="gold">当前账号</Tag>}
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
      title: "绑定邮箱",
      dataIndex: "email",
      key: "email",
      width: 200,
      render: (email: string) =>
        email ? (
          <Space size={4}>
            <Text style={{ fontSize: 13 }}>{email}</Text>
            <Tooltip title="复制完整邮箱">
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined style={{ fontSize: 12, color: "#94a3b8" }} />}
                onClick={() => {
                  navigator.clipboard.writeText(email);
                  message.success("邮箱已复制");
                }}
                style={{ padding: "0 2px", height: 20 }}
              />
            </Tooltip>
          </Space>
        ) : (
          <Tag color="default">未绑定</Tag>
        ),
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
      title: "最后活跃",
      dataIndex: "last_active_at",
      key: "last_active_at",
      width: 180,
      render: (val: number) => {
        if (!val) return <Tag color="default">从未活跃</Tag>;
        const diff = Date.now() - val;
        const mins = Math.floor(diff / 60000);
        let label: string;
        if (mins < 1) label = "刚刚";
        else if (mins < 60) label = `${mins} 分钟前`;
        else if (mins < 60 * 24) label = `${Math.floor(mins / 60)} 小时前`;
        else label = new Date(val).toLocaleString();
        return <Tag color={mins < 60 ? "green" : "default"}>{label}</Tag>;
      },
    },
    {
      title: "操作",
      key: "action",
      width: 280,
      render: (_: any, record: UserItem) => {
        const isSelf = isCurrentLoginUser(record);
        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => {
                setDetailUserId(record.id);
                setDetailOpen(true);
              }}
            >
              详情
            </Button>
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
                  email: record.email || "",
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
              disabled={isSelf}
            >
              <Tooltip title={isSelf ? "不能删除当前登录的管理员账号" : ""}>
                <Button
                  type="link"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  disabled={isSelf}
                >
                  删除
                </Button>
              </Tooltip>
            </Popconfirm>
          </Space>
        );
      },
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
            placeholder="搜索用户名、昵称或邮箱"
            prefix={<SearchOutlined />}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onPressEnter={handleApplySearch}
            allowClear
            onClear={() => {
              setSearchInput("");
              setSearchQuery("");
              setPage(1);
            }}
            style={{ width: 260 }}
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
          <Button type="primary" onClick={handleApplySearch}>
            筛选
          </Button>
          <Button onClick={handleResetSearch}>
            重置
          </Button>
        </Space>
      </Card>

      <Card styles={{ body: { padding: 0 } }}>
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
        confirmLoading={createLoading}
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
        confirmLoading={editLoading}
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
          <Form.Item name="email" label="绑定邮箱">
            <Input disabled prefix={<MailOutlined />} placeholder="未绑定邮箱" />
          </Form.Item>
          <Form.Item name="avatar" label="头像 URL">
            <Input placeholder="头像图片直链（选填）" />
          </Form.Item>
          <Form.Item
            name="role"
            label={
              <Space>
                <span>权限角色</span>
                {currentUser && isCurrentLoginUser(currentUser) && (
                  <Tooltip title="当前登录账号无法修改自身角色，防止自锁">
                    <InfoCircleOutlined style={{ color: "#faad14" }} />
                  </Tooltip>
                )}
              </Space>
            }
            rules={[{ required: true }]}
            extra={
              currentUser && isCurrentLoginUser(currentUser) ? (
                <Text type="warning" style={{ fontSize: 12 }}>
                  提示：当前登录管理员不可修改自身角色，防止权限失效造成自锁。
                </Text>
              ) : undefined
            }
          >
            <Select
              disabled={currentUser ? isCurrentLoginUser(currentUser) : false}
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
        confirmLoading={resetPassLoading}
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

      {/* 用户数据详情弹窗 */}
      <UserDetailPage
        userId={detailUserId ?? 0}
        open={detailOpen && detailUserId != null}
        onClose={() => setDetailOpen(false)}
      />
    </div>
  );
};
