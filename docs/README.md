# Voyage 项目开发指引

## 项目概述

基于 **Java 21 (虚拟线程)** + **Vue 3 (Vite 6)** 的全栈项目。

| 层 | 技术栈 |
|---|---|
| 后端 | Spring Boot 3.4+, Maven, Java 21, MyBatis Plus 3.5.x |
| 前端 | Vue 3.5+, Vite 6, TypeScript, Element Plus |
| 数据库 | MySQL 8.4 LTS (`voyage_db`) |

## 目录结构

```
app-voyage/
├── java-voyage-server/    # 后端 Spring Boot 项目
├── web-voyage-vue/        # 前端 Vue 3 项目
├── sql/                    # 数据库变更脚本（手工执行）
├── docs/                   # 项目开发文档
└── .roo/rules/rules.md    # AI 编码规范
```

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 22+
- MySQL 8.4 LTS
- IDE：推荐 VS Code 或 IntelliJ IDEA

### 2. 数据库初始化

数据库脚本位于 [`sql/`](sql/) 目录。**需要手工执行**，不依赖 Flyway 等自动迁移工具。

```bash
# 连接 MySQL 并执行脚本
mysql -u root -p < sql/V1.0__init.sql
```

详细说明见 [sql/README.md](sql/README.md)。

### 3. 启动后端

```bash
cd java-voyage-server
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8098`，所有 API 前缀为 `/api`。

### 4. 启动前端

```bash
cd web-voyage-vue
npm install
npm run dev
```

前端默认运行在 `http://localhost:8088`。

## 核心约定

### 身份认证

本项目**无传统登录流程**。前端右上角设置 7 位工号，所有请求自动注入 `X-Emp-No` 请求头，后端通过 `EmpContext.getEmpNo()` 获取当前操作者身份。

### 接口规范

- 所有 Controller 路径以 `/api` 开头，禁止添加版本号（如 `/v1`）
- 统一响应格式 `Result<T>`：`{ "code": 200, "message": "success", "data": {...} }`

### 代码规范

- 后端：Lombok (`@Data`, `@Slf4j`)，接口返回优先使用 Java Record
- 前端：Vue 3 `<script setup>` + TypeScript，禁止 `any`
- 详细规范见 [.roo/rules/rules.md](.roo/rules/rules.md)

## 数据库变更流程

1. 在 [`sql/`](sql/) 下创建新版本脚本，如 `V1.1__add_xxx.sql`
2. 在本地 MySQL 中手工执行该脚本
3. 将脚本提交到 Git，团队成员拉取后各自手工执行

**禁止**通过代码自动执行数据库变更。
