import {
  type PropsWithChildren,
  createContext,
  useEffect,
  useState,
} from "react";
import { ConfigProvider, theme } from "antd";
import { RefineThemes } from "@refinedev/antd";

type ColorMode = "light" | "dark";

type ColorModeContextType = {
  mode: ColorMode;
  setMode: (mode?: string) => void;
};

export const ColorModeContext = createContext<ColorModeContextType>({
  mode: "light",
  setMode: () => {},
});

export const ColorModeContextProvider: React.FC<PropsWithChildren> = ({
  children,
}) => {
  const colorModeFromLocalStorage = localStorage.getItem("colorMode") as ColorMode | null;
  const isSystemPreferenceDark = typeof window !== "undefined" && window.matchMedia
    ? window.matchMedia("(prefers-color-scheme: dark)").matches
    : false;

  const defaultMode: ColorMode =
    colorModeFromLocalStorage === "light" || colorModeFromLocalStorage === "dark"
      ? colorModeFromLocalStorage
      : isSystemPreferenceDark
      ? "dark"
      : "light";

  const [mode, setMode] = useState<ColorMode>(defaultMode);

  useEffect(() => {
    window.localStorage.setItem("colorMode", mode);
  }, [mode]);

  const handleSetMode = (targetMode?: string) => {
    if (targetMode === "light" || targetMode === "dark") {
      setMode(targetMode);
    } else {
      setMode((prev) => (prev === "light" ? "dark" : "light"));
    }
  };

  const { darkAlgorithm, defaultAlgorithm } = theme;

  return (
    <ColorModeContext.Provider
      value={{
        setMode: handleSetMode,
        mode,
      }}
    >
      <ConfigProvider
        theme={{
          ...RefineThemes.Blue,
          token: {
            ...RefineThemes.Blue.token,
            colorPrimary: "#1677ff",
          },
          algorithm: mode === "light" ? defaultAlgorithm : darkAlgorithm,
        }}
      >
        {children}
      </ConfigProvider>
    </ColorModeContext.Provider>
  );
};
