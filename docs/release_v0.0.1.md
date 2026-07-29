# 🚀 Voyage Platform v0.0.1 — 脚手架版本

> **发布日期**：2026-07-29
>
> 首个版本，搭建全栈项目骨架，跑通前后端联调链路。

---

## 🧱 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 前端 | Vue 3 + TypeScript + Vite | Vue 3.5 / Vite 6 |
| UI 框架 | Element Plus | 2.9 |
| 路由 | Vue Router | 4.5 |
| HTTP 客户端 | Axios | 1.7 |
| 后端 | Spring Boot + Java 21 | 3.4.1 |
| ORM | MyBatis Plus | 3.5.9 |
| 数据库 | MySQL + Flyway | 8.4 |
| 构建 | Maven | — |

---

## ✅ 已完成

### 前端 (`web-voyage-vue`)

- [x] Vite + Vue 3 + TypeScript 工程搭建
- [x] Element Plus 集成，全局中文语言包
- [x] Vue Router 路由配置（`/dashboard`、`/demo`）
- [x] **Grid 布局框架**：侧边栏 + 顶栏 + 主内容区 + 底部状态栏
- [x] **侧边栏**：Logo 区域 + 导航菜单（仪表盘 / 示例页面）
- [x] **侧边栏折叠**：展开 240px → 折叠 64px，网格过渡动画 0.25s
- [x] **顶栏**：工号就地编辑（Tag ↔ Input 切换，7 位数字校验，localStorage 持久化）
- [x] **底部状态栏**：实时时钟 + 登录 IP 显示
- [x] Axios 请求封装（baseURL `/api`，统一拦截器）
- [x] 仪表盘占位页、示例占位页

### 后端 (`java-voyage-server`)

- [x] Spring Boot 3 + Java 21 工程搭建
- [x] MyBatis Plus + MySQL 数据源配置
- [x] 全局异常处理器（`GlobalExceptionHandler`）
- [x] 统一响应体（`Result<T>`）
- [x] 工号上下文拦截器（`EmpContext`，从请求头 `X-Emp-No` 解析）
- [x] `/api/health` — 健康检查接口
- [x] `/api/system/info` — 系统信息接口（返回登录 IP）

### 数据库 (`sql/`)

- [x] Flyway 迁移脚本 `V1.0__init.sql`
- [x] `voyage_user` 表：工号、姓名、邮箱、手机号（AES 加密）、软删除
- [x] `voyage_operation_log` 表：操作日志（模块、动作、请求参数、IP）
- [x] 默认管理员数据（工号 `0000001`）

---

## 📁 项目结构

```
app-voyage/
├── web-voyage-vue/          # 前端 (Vue 3 + TS + Vite)
│   ├── src/
│   │   ├── api/             # 接口层 (health.ts, system.ts)
│   │   ├── components/
│   │   │   └── Layout/      # 布局组件 (Header, Sidebar, StatusBar)
│   │   ├── router/          # 路由配置
│   │   ├── utils/           # 工具函数 (request.ts, currentUser.ts)
│   │   └── views/           # 页面 (Dashboard, Demo)
│   └── vite.config.ts       # Vite 配置 (代理 /api → localhost:18080)
├── java-voyage-server/      # 后端 (Spring Boot 3 + Java 21)
│   └── src/main/java/com/voyage/
│       ├── common/          # 通用 (Result, EmpContext)
│       ├── config/          # 配置 (异常处理, 拦截器)
│       └── controller/      # 控制器 (Health, System)
└── sql/                     # 数据库迁移脚本 (Flyway)
    └── V1.0__init.sql
```

---

## 🏗️ 本地启动

```bash
# 1. 数据库 (需要 MySQL 8.4，Flyway 自动建表)
# 修改 java-voyage-server/src/main/resources/application.yml 中的数据库连接

# 2. 后端 (端口 18080)
cd java-voyage-server
./mvnw spring-boot:run

# 3. 前端 (端口 8088，代理 /api → localhost:18080)
cd web-voyage-vue
npm install
npm run dev
```

浏览器访问 `http://localhost:8088`。

---

## 🔜 下一版本规划 (v0.1.0)

- [ ] 用户管理 CRUD（列表、新增、编辑、删除）
- [ ] 手机号 AES-256-GCM 加解密
- [ ] 操作日志查询与分页
- [ ] 雪花 ID 自动生成（MyBatis Plus 集成）
- [ ] 前端表格、分页、表单校验
