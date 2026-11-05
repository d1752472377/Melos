import type { RefineThemedLayoutHeaderProps } from "@refinedev/antd";
import { useGetIdentity, useLogout } from "@refinedev/core";
import {
  Avatar,
  Dropdown,
  Input,
  Layout as AntdLayout,
  MenuProps,
  Space,
  theme,
  Tooltip,
  Typography,
  Button,
} from "antd";
import React, { useContext, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
  UserOutlined,
  SearchOutlined,
  BulbOutlined,
  MoonOutlined,
  DashboardOutlined,
  TeamOutlined,
  FileTextOutlined,
} from "@ant-design/icons";
import { ColorModeContext } from "../../contexts/color-mode";

const { Text } = Typography;
const { useToken } = theme;

type IUser = {
  id: number;
  name: string;
  avatar: string;
  role?: string;
};

/** 顶部快捷导航（与侧栏分组互补：这里只放最常用的三个入口） */
const QUICK_NAV = [
  { key: "/", label: "主页", icon: <DashboardOutlined /> },
  { key: "/users", label: "用户", icon: <TeamOutlined /> },
  { key: "/logs", label: "日志", icon: <FileTextOutlined /> },
];

/**
 * 顶部导航条：左侧品牌 + 快捷导航，右侧全局搜索 / 主题切换 / 账号菜单。
 *
 * 账号菜单里承载「个人设置」与「退出登录」——退出登录从侧栏移到这里，
 * 原因是它不可撤销且低频，放在导航区容易被误点。
 */
export const Header: React.FC<RefineThemedLayoutHeaderProps> = ({
  sticky = true,
}) => {
  const { token } = useToken();
  const { data: user } = useGetIdentity<IUser>();
  const { mode, setMode } = useContext(ColorModeContext);
  const { mutate: logout } = useLogout();
  const navigate = useNavigate();
  const location = useLocation();
  const [keyword, setKeyword] = useState("");
  const isDark = mode === "dark";

  const userMenuItems: MenuProps["items"] = [
    {
      key: "header",
      label: (
        <div style={{ padding: "4px 0" }}>
          <Text strong style={{ fontSize: 13 }}>
            {user?.name || "管理员"}
          </Text>
          <br />
          <Text type="secondary" style={{ fontSize: 11 }}>
            管理员账号
          </Text>
        </div>
      ),
      disabled: true,
    },
    { type: "divider" },
    {
      key: "profile",
      icon: <SettingOutlined />,
      label: "个人设置",
      onClick: () => navigate("/profile"),
    },
    { type: "divider" },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      danger: true,
      label: "退出登录",
      onClick: () => logout(),
    },
  ];

  const headerStyles: React.CSSProperties = {
    backgroundColor: token.colorBgContainer,
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "0 20px",
    height: 56,
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
    gap: 16,
  };

  if (sticky) {
    headerStyles.position = "sticky";
    headerStyles.top = 0;
    headerStyles.zIndex = 100;
  }

  const isActive = (key: string) =>
    key === "/" ? location.pathname === "/" : location.pathname.startsWith(key);

  return (
    <AntdLayout.Header style={headerStyles}>
      {/* 左：品牌 + 快捷导航 */}
      <Space size={20} align="center" style={{ minWidth: 0 }}>
        <Space size={10} align="center" style={{ cursor: "pointer" }} onClick={() => navigate("/")}>
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: 7,
              background: "#4f46e5",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#fff",
              fontWeight: 800,
              fontSize: 14,
              flexShrink: 0,
            }}
          >
            M
          </div>
          <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.15 }}>
            <Text strong style={{ fontSize: 14 }}>
              Melos 音乐
            </Text>
            <Text type="secondary" style={{ fontSize: 10 }}>
              管理控制台
            </Text>
          </div>
        </Space>

        <Space size={4}>
          {QUICK_NAV.map((item) => {
            const active = isActive(item.key);
            return (
              <Button
                key={item.key}
                type="text"
                size="small"
                icon={item.icon}
                onClick={() => navigate(item.key)}
                style={{
                  fontSize: 13,
                  color: active ? token.colorPrimary : token.colorTextSecondary,
                  background: active ? token.colorPrimaryBg : "transparent",
                  fontWeight: active ? 600 : 400,
                }}
              >
                {item.label}
              </Button>
            );
          })}
        </Space>
      </Space>

      {/* 右：搜索 / 主题 / 账号 */}
      <Space size={12} align="center">
        <Input
          size="small"
          allowClear
          prefix={<SearchOutlined style={{ color: token.colorTextTertiary }} />}
          placeholder="搜索用户（用户名 / 昵称 / 邮箱）"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={() => {
            const q = keyword.trim();
            navigate(q ? `/users?q=${encodeURIComponent(q)}` : "/users");
          }}
          style={{ width: 240, borderRadius: 8 }}
        />

        <Tooltip title={isDark ? "切换到浅色" : "切换到深色"}>
          <Button
            type="text"
            size="small"
            icon={isDark ? <BulbOutlined /> : <MoonOutlined />}
            onClick={() => setMode(isDark ? "light" : "dark")}
          />
        </Tooltip>

        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
          <Space style={{ cursor: "pointer" }} size={6}>
            <Avatar
              size={30}
              src={user?.avatar}
              icon={<UserOutlined />}
              style={{ backgroundColor: "#4f46e5", flexShrink: 0 }}
            >
              {user?.name?.[0]}
            </Avatar>
            <Text strong style={{ fontSize: 13 }}>
              {user?.name || "管理员"}
            </Text>
            <DownOutlined style={{ fontSize: 10, color: token.colorTextSecondary }} />
          </Space>
        </Dropdown>
      </Space>
    </AntdLayout.Header>
  );
};
