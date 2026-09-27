import type { RefineThemedLayoutHeaderProps } from "@refinedev/antd";
import { useGetIdentity, useLogout } from "@refinedev/core";
import {
  Avatar,
  Dropdown,
  Layout as AntdLayout,
  MenuProps,
  Space,
  Switch,
  theme,
  Typography,
  Tag,
} from "antd";
import React, { useContext } from "react";
import { useNavigate } from "react-router-dom";
import {
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
  UserOutlined,
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

export const Header: React.FC<RefineThemedLayoutHeaderProps> = ({
  sticky = true,
}) => {
  const { token } = useToken();
  const { data: user } = useGetIdentity<IUser>();
  const { mode, setMode } = useContext(ColorModeContext);
  const { mutate: logout } = useLogout();
  const navigate = useNavigate();

  const userMenuItems: MenuProps["items"] = [
    {
      key: "profile",
      icon: <SettingOutlined />,
      label: "个人设置",
      onClick: () => navigate("/profile"),
    },
    {
      type: "divider",
    },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      danger: true,
      label: "退出登录",
      onClick: () => logout(),
    },
  ];

  const headerStyles: React.CSSProperties = {
    backgroundColor: token.colorBgElevated,
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "0px 24px",
    height: "64px",
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  };

  if (sticky) {
    headerStyles.position = "sticky";
    headerStyles.top = 0;
    headerStyles.zIndex = 100;
  }

  return (
    <AntdLayout.Header style={headerStyles}>
      <div style={{ display: "flex", alignItems: "center" }}>
        <Text style={{ fontSize: 14, color: "#64748b" }}>
          欢迎使用 Melos 音乐后台管理系统
        </Text>
      </div>

      <Space size="middle" align="center">
        {/* 明暗主题切换 */}
        <Switch
          checkedChildren="深色"
          unCheckedChildren="浅色"
          onChange={() => setMode(mode === "light" ? "dark" : "light")}
          defaultChecked={mode === "dark"}
        />

        {/* 管理员身份下拉 */}
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
          <Space style={{ cursor: "pointer" }} size="small">
            <Avatar
              src={user?.avatar}
              icon={<UserOutlined />}
              style={{ backgroundColor: "#4f46e5" }}
            >
              {user?.name?.[0]}
            </Avatar>
            <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.2 }}>
              <Text strong style={{ fontSize: 13 }}>
                {user?.name || "管理员"}
              </Text>
              <Tag
                color="blue"
                style={{
                  fontSize: 10,
                  margin: "2px 0 0",
                  padding: "0 4px",
                  lineHeight: "16px",
                  borderRadius: 4,
                  alignSelf: "flex-start",
                }}
              >
                管理员
              </Tag>
            </div>
            <DownOutlined style={{ fontSize: 11, color: token.colorTextSecondary }} />
          </Space>
        </Dropdown>
      </Space>
    </AntdLayout.Header>
  );
};
