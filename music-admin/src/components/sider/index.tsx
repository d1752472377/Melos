import React, { useContext } from "react";
import { ThemedSider } from "@refinedev/antd";
import { Menu, theme } from "antd";
import { useLocation, useNavigate } from "react-router-dom";
import {
  DashboardOutlined,
  TeamOutlined,
  AndroidOutlined,
  SyncOutlined,
  FileTextOutlined,
  DownloadOutlined,
} from "@ant-design/icons";
import { ColorModeContext } from "../../contexts/color-mode";

/**
 * 侧栏导航：按「概览 / 数据 / 发布」分组，组标题是小号灰字。
 *
 * 为什么不直接用 ThemedSider 给的 items：
 * Refine 渲染出来的 items 是平铺的 Menu 项，插不进分组标题。这里按配置自己
 * 生成 Menu，路由与高亮仍由 react-router 决定（`syncWithLocation` 已开启）。
 *
 * 两点刻意的取舍：
 * 1. **侧栏不放 logo**——品牌标识统一在顶部导航条，视觉重心与参考布局一致；
 * 2. **侧栏不放退出登录**——那是低频且不可撤销的动作，放在右上角头像菜单里，
 *    避免在导航区误点（原实现就挂在侧栏最底部）。
 */
const NAV_GROUPS: {
  title: string;
  items: { key: string; label: string; icon: React.ReactNode }[];
}[] = [
  {
    title: "概览",
    items: [{ key: "/", label: "仪表盘", icon: <DashboardOutlined /> }],
  },
  {
    title: "数据",
    items: [
      { key: "/users", label: "用户管理", icon: <TeamOutlined /> },
      { key: "/logs", label: "客户端日志", icon: <FileTextOutlined /> },
    ],
  },
  {
    title: "发布",
    items: [
      { key: "/versions", label: "版本发布", icon: <AndroidOutlined /> },
      { key: "/sync", label: "同步监控", icon: <SyncOutlined /> },
      { key: "/download", label: "下载页", icon: <DownloadOutlined /> },
    ],
  },
];

export const CustomSider: React.FC = (props) => {
  const { mode } = useContext(ColorModeContext);
  const isDark = mode === "dark";
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();

  // 选中项取最长匹配前缀：/users/123 也要高亮「用户管理」
  const selectedKey =
    NAV_GROUPS.flatMap((g) => g.items.map((i) => i.key))
      .filter((k) =>
        k === "/" ? location.pathname === "/" : location.pathname.startsWith(k)
      )
      .sort((a, b) => b.length - a.length)[0] || "/";

  return (
    <ThemedSider
      {...props}
      Title={() => null}
      render={({ collapsed }) => {
        const menuItems = NAV_GROUPS.map((group) => ({
          key: group.title,
          type: "group" as const,
          label: collapsed ? undefined : (
            <span
              style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: 0.5,
                color: isDark ? "#64748b" : "#94a3b8",
              }}
            >
              {group.title}
            </span>
          ),
          children: group.items.map((item) => ({
            key: item.key,
            icon: item.icon,
            label: item.label,
          })),
        }));

        return (
          <div style={{ paddingTop: 4 }}>
            <Menu
              mode="inline"
              selectedKeys={[selectedKey]}
              items={menuItems}
              inlineIndent={16}
              style={{
                borderInlineEnd: "none",
                background: "transparent",
                fontSize: 13,
              }}
              onClick={({ key }) => {
                if (key.startsWith("/")) navigate(key);
              }}
            />
          </div>
        );
      }}
    />
  );
};
