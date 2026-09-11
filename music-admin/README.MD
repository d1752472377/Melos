# music-admin

Melos 音乐平台的运营与系统管理控制台，基于 **React 19 + TypeScript + Vite + Ant Design 5 + Refine** 构建。

---

## 目录

- [一、项目定位](#一项目定位)
- [二、核心功能](#二核心功能)
- [三、技术栈](#三技术栈)
- [四、工程目录结构](#四工程目录结构)
- [五、快速开始](#五快速开始)
- [六、环境变量说明](#六环境变量说明)
- [七、前后端交互架构](#七前后端交互架构)
- [八、生产构建与部署](#八生产构建与部署)

---

## 一、项目定位

`music-admin` 是 Melos 生态中的 Web 管理端，专门为系统管理员提供可视化操作界面，主要负责：
1. **用户全生命周期管理**：查看全站用户、注册时间、权限分配、账号编辑与密码重置。
2. **多端数据同步状态观测**：直观查看用户的同步版本号、收藏数、歌单数及听歌流水。
3. **管理员安全中心**：个人资料维护与密码安全修改。

---

## 二、核心功能

### 1. 📊 仪表盘（Dashboard）
- **全站用户总数**：实时统计后端注册的用户总量。
- **当前登录管理员**：展示当前操作者身份与在线状态。
- **同步数据预览**：实时获取当前账号在多端同步引擎中的数据快照（版本游标、收藏数、歌单数等）。

### 2. 👥 用户管理（User Management）
- **全量用户检索**：支持分页查询、每页条数切换（10/20/50）、用户名/昵称模糊搜索、角色（`admin` / `user`）筛选。
- **注册时间展示**：精确展示每位用户的注册时间与头像预览。
- **创建新用户**：管理员可在后台直接建立新账号，并指定初始密码与角色权限。
- **编辑用户资料**：支持在线修改用户昵称、头像直链与权限提权/降权（内置系统唯一管理员保护机制）。
- **重置用户密码**：管理员可为遗忘密码的用户快速重置新密码，重置成功后自动踢除该用户除当前操作外的所有在线设备。
- **安全删除账号**：支持物理清理违规账号，级联删除该用户的全部收藏、歌单、历史及多设备会话（禁止删除自身及最后一位管理员）。

### 3. 🔄 数据同步监控（Sync Monitor）
- **版本与事件统计**：监控数据同步引擎当前 Version、已接收的听歌事件总数及各集合数据量。
- **听歌事件流水**：可视化展示近期上报的听歌明细（歌曲名、歌手、平台及监听时间）。

### 4. ⚙️ 账号设置（Profile）
- **基本资料修改**：支持更换管理员昵称、自定义头像图片（带实时输入预览）。
- **安全修改密码**：校验旧密码并设定 5-64 位高强度新密码，修改后其他端会话立即失效。

### 5. 🌓 主题与体验
- **色彩模式切换**：支持右上角一键切换「明亮模式」与「暗黑模式」。
- **全站响应式布局**：基于 Ant Design Grid，自适应桌面端与移动端浏览器。

---

## 三、技术栈

| 模块 | 技术选型 | 说明 |
|---|---|---|
| **框架核心** | React 19 + TypeScript 5 | 现代化前端核心与严谨类型系统 |
| **构建工具** | Vite 6 | 秒级极速热重载与轻量构建 |
| **中后台支撑**| `@refinedev/core` + `@refinedev/antd` | 提供后台框架布局、侧边栏导航与 Auth 体系 |
| **UI 组件库** | Ant Design 5 (`antd`) | 企业级 UI 组件库 |
| **图标库** | `@ant-design/icons` | 官方矢量图标组件 |
| **路由驱动** | `react-router-dom` v7 | 标准客户端单页路由 |

---

## 四、工程目录结构

```
music-admin/
├── src/
│   ├── components/
│   │   └── header/       # 顶部全局导航栏（深浅色切换、当前用户信息、快捷退出）
│   ├── contexts/
│   │   └── color-mode/   # Ant Design 动态明暗主题上下文
│   ├── pages/
│   │   ├── dashboard/    # 系统仪表盘（用户总数与同步大盘）
│   │   ├── users/        # 用户管理（列表、分页、搜索、新建、编辑、重置密码、删除）
│   │   ├── sync/         # 同步监控（版本游标、事件流水）
│   │   ├── profile/      # 个人设置（资料修改、安全改密）
│   │   └── login/        # 管理员专用登录页
│   ├── providers/
│   │   ├── api.ts        # 统一 Fetch 请求封装（自动附加 Bearer Token、401 自动跳转）
│   │   ├── auth.ts       # Refine AuthProvider（登录、登出、角色守卫、Session 持久化）
│   │   ├── constants.ts  # API 基础地址与全局 Token 常量
│   │   └── data.ts       # Refine SimpleRest 数据适配器声明
│   ├── App.tsx           # 路由挂载、资源声明（Resources）与权限守卫拦截
│   └── index.tsx         # Web 应用入口挂载
├── public/               # 静态资源文件
├── .env.example          # 环境变量示例模板
├── Dockerfile            # 生产环境容器化构建配置
├── package.json          # 依赖声明与编译脚本
├── tsconfig.json         # TypeScript 编译配置
└── vite.config.ts        # Vite 插件与服务器配置
```

---

## 五、快速开始

### 1. 确保后端已启动

本前端依赖 `melos-backend` 服务。请先确认后端已在本地或远端运行（默认监听 `http://localhost:8787`）。

### 2. 安装依赖

推荐使用 `pnpm`（也可以使用 `npm`）：

```bash
cd music-admin
pnpm install
# 或者
npm install
```

### 3. 配置环境变量

复制 `.env.example` 为 `.env`：

```bash
# Linux / macOS:
cp .env.example .env

# Windows PowerShell:
Copy-Item .env.example .env

# Windows CMD:
copy .env.example .env
```

如后端运行在默认端口 `8787`，则保持 `.env` 默认配置即可：
```env
VITE_API_URL=http://localhost:8787/api
```

### 4. 启动开发服务器

```bash
pnpm dev
# 或者
npm run dev
```

浏览器访问 `http://localhost:5173` 即可进入管理后台。

### 5. 登录凭证

使用系统默认初始管理员账号登录：
- **用户名**：`admin`
- **密码**：`12346`（若后端配置了 `ADMIN_INITIAL_PASSWORD`，请使用自定义密码）

> **安全提示**：登录后，请点击右上角头像前往「账号设置」立即修改初始密码！

---

## 六、环境变量说明

| 环境变量名 | 默认值 | 说明 |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8787/api` | 后端服务 API 基础地址（末尾需包含 `/api`，无结尾斜杠） |

---

## 七、前后端交互架构

```
[浏览器 Client]
      │
      ├─ 1. POST /api/auth/login ──> 校验成功且 role === 'admin' ──> 存入 localStorage("refine-auth")
      │
      ├─ 2. 携带 Header Authorization: Bearer <token>
      │
      ├─ 3. 页面路由拦截器 (App.tsx) ──> 调用 GET /api/me ──> 确认保持 admin 权限
      │
      └─ 4. 业务数据请求 (api.ts) ──> /api/admin/users/*  (用户管理)
                                  ──> /api/me/password    (安全修改密码)
                                  ──> /api/sync/pull      (同步监控大盘)
```

- **权限隔离**：非 `admin` 角色的普通用户在登录时会被前端直接拦截并自动调用登出注销 Token。
- **会话失效机制**：任何接口返回 `401 Unauthorized` 时，前端自动清理本地缓存凭证并重定向至 `/login`。

---

## 八、生产构建与部署

### 1. 静态打包构建

```bash
pnpm build
# 或 npm run build
```
打包产物将输出在 `dist/` 目录中。

### 2. Nginx 静态托管配置

生产环境推荐使用 Nginx 进行静态资源代理（需配置 SPA 路由重定向）：

```nginx
server {
    listen 80;
    server_name admin.music.yourdomain.com;

    root /var/www/music-admin/dist;
    index index.html;

    # 单页应用路由重定向
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 可选：直接由 Nginx 代理后端 API 消除跨域
    location /api/ {
        proxy_pass http://127.0.0.1:8787/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 3. Docker 容器化部署

利用项目根目录自带的 `Dockerfile` 构建并运行：

```bash
docker build -t melos-music-admin .
docker run -d -p 8080:80 --name melos-admin melos-music-admin
```
访问宿主机 `http://<服务器IP>:8080` 即可使用。
