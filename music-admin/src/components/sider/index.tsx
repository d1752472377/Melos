import React, { useContext } from "react";
import { ThemedSider } from "@refinedev/antd";
import { ColorModeContext } from "../../contexts/color-mode";

export const CustomSider: React.FC = (props) => {
  const { mode } = useContext(ColorModeContext);
  const isDark = mode === "dark";

  return (
    <ThemedSider
      {...props}
      Title={({ collapsed }) => (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: collapsed ? "center" : "flex-start",
            padding: collapsed ? "16px 8px" : "16px 20px",
            width: "100%",
            borderBottom: `1px solid ${isDark ? "#334155" : "#f1f5f9"}`,
            gap: 12,
          }}
        >
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              background: "#4f46e5",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#ffffff",
              fontWeight: 800,
              fontSize: 16,
              flexShrink: 0,
            }}
          >
            M
          </div>
          {!collapsed && (
            <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.2 }}>
              <span style={{ fontWeight: 700, fontSize: 16, color: isDark ? "#f8fafc" : "#0f172a" }}>
                Melos 音乐
              </span>
              <span
                style={{
                  fontSize: 11,
                  color: "#64748b",
                  marginTop: 2,
                }}
              >
                管理控制台
              </span>
            </div>
          )}
        </div>
      )}
      render={({ items, logout }) => (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            justifyContent: "space-between",
            height: "calc(100% - 65px)",
          }}
        >
          {/* 菜单列表 */}
          <div style={{ flex: 1, overflowY: "auto", overflowX: "hidden", paddingTop: 8 }}>
            {items}
          </div>

          {/* 底部退出按钮 */}
          <div style={{ borderTop: `1px solid ${isDark ? "#334155" : "#f1f5f9"}` }}>
            {logout}
          </div>
        </div>
      )}
    />
  );
};
