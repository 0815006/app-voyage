数据库连接 

这是一个非常优雅且安全的简化思路！将数据库连接的生命周期与“会话前端交互上下文”深度绑定，可以大幅简化持久化设计并提升安全级别。

为什么这个简化方案非常好？
零密码持久化安全（Zero-Password Persistence Security）：

数据库密码完全不在 PostgreSQL 数据库中落盘（即便加密也有密钥泄露风险）。

用户在前端页面侧边栏/弹出框中输入或选择连接参数时，密码仅存留在前端内存/当前 WebSocket 或 HTTP Session 内存中。

轻量化的会话状态：

会话表只需记录基础的对话上下文（Messages）、思维链（Trace）以及连接的元数据快照/别名（如 MySQL-主库、TDSQL-从库，不含敏感密码），极大地简化了数据库 schema。

符合交互直觉的多库横向对比：

用户进入一个数据库分析会话，在界面上打勾/选中当前会话需要用到的 2 个或多个数据库连接配置，并发起分析。

后端应用层在收到请求时，动态用传入的连接参数初始化内存连接池（或使用 ephemeral connection），Agent 即可在同一次会话里通过别名自由切换和对比。

一、 简化后的持久化表结构设计
在数据库场景下，PostgreSQL 只需要维护一个极简的会话表：

SQL
-- 数据库性能分析会话表 (无需存储任何密码)
CREATE TABLE session_db_analysis (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_title VARCHAR(255) NOT NULL,          -- 会话标题，如："主从库慢SQL与索引对比分析"
    
    -- 记录本次会话当前选中的数据库元数据快照 (仅存别名、IP、端口、数据库名、方言，不存密码)
    selected_db_meta JSONB NOT NULL DEFAULT '[]', 
    
    context_messages JSONB NOT NULL DEFAULT '[]', -- 对话历史上下文 (Message 列表)
    execution_trace JSONB DEFAULT '[]',          -- ReAct 思考与 Tool 调用轨迹 (Thought, Tool Call History)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
selected_db_meta 存储示例（JSONB）：
JSON
[
  {
    "alias": "Order_MySQL_Master",
    "dialect": "MYSQL",
    "host": "192.168.1.100",
    "port": 3306,
    "dbName": "order_db"
  },
  {
    "alias": "Order_TDSQL_Slave",
    "dialect": "TDSQL",
    "host": "192.168.1.101",
    "port": 3306,
    "dbName": "order_db"
  }
]
二、 运行时的动态连接传递机制
由于密码不在 PG 中落盘，在应用层与 ai-tool-db 交互时，采用“前端内存凭证 ➔ 后端上下文注入 ➔ SDK 工具执行”的轻量链路：

┌────────────────────────┐     1. 发起分析请求 (含选中的DB连接信息及密码)      ┌────────────────────────┐
│                        │ ─────────────────────────────────────────► │                        │
│   前端页面 (SaaS/SPA)   │                                            │  应用层后端 (SpringBoot)│
│ (内存保存连接及密码)    │ ◄───────────────────────────────────────── │                        │
└────────────────────────┘     4. SSE 流式返回思考过程与分析结果       └───────────┬────────────┘
                                                                               │
                                                                               │ 2. 将临时连接凭证绑定到
                                                                               │    InvocationContext
                                                                               ▼
                                                                      ┌────────────────────────┐
                                                                      │      ai-tool-db        │
                                                                      │ (DbConnectionManager)  │
                                                                      └────────────────────────┘
                                                                               │
                                                                               │ 3. 动态建立临时连接并
                                                                               │    执行探查/EXPLAIN SQL
                                                                               ▼
                                                                      ┌────────────────────────┐
                                                                      │    目标数据库 (MySQL/  │
                                                                      │      TDSQL 等)         │
                                                                      └────────────────────────┘
前端选中与临时凭证提交：

用户在 UI 勾选要参与分析的数据库（例如 MySQL 主库 和 TDSQL 备库）。

提交分析指令（例如：“对比这两个库中 t_order 表的索引差异和慢 SQL 执行计划”）时，前端将所选连接的配置参数（含密码）作为 Request Header 或 Payload 一同传入。

后端 Context 注入与 SDK 调度：

应用层后端接收到请求后，将这些连接凭证塞入本次请求的上下文（如 InvocationContext / ThreadLocal）。

ai-tool-db 的 DbConnectionManager 依据上下文中的配置建立会话作用域的临时连接/连接池。

Agent 多库工具调用：

Agent 在 Reasoning 阶段识别出用户要对比两个库，生成对应的 Tool Calling：

getDbSchema(target_db_alias="Order_MySQL_Master", table="t_order")

getDbSchema(target_db_alias="Order_TDSQL_Slave", table="t_order")

ai-tool-db 根据 target_db_alias 路由到对应的临时连接执行探查，并将对比结果吐回给 Agent。

请求/会话结束与资源释放：

分析完成后，后端销毁该次请求的临时连接池，数据库持久化层只更新对话 Message 和思维 Trace，密码在内存中随请求结束而被垃圾回收（GC）。

三、 总结：场景一持久化简化的核心优势
安全无忧：无需处理复杂的密码加密、解密及密钥轮转机制，PG 数据库即使被导出或泄漏，也不会暴露任何目标数据库的登录凭证。

结构极简：从多表级联依赖（session 表 ➔ db_connection 表）简化为单一的 session_db_analysis 表，开发和维护成本极低。

边界清晰：完全符合你设定的“单次会话选中哪些 DB 就用哪些，支持多 DB 横向对比”的交互模型。


一、 场景一：数据库性能分析 (DB Analysis)
1. 页面与功能设计 (UI/UX & Features)
数据库性能分析场景的的核心定位是“受控探查、多库对比、交互式诊断”。

┌────────────────────────────────────────────────────────────────────────────────────────┐
│  [新会话 / 历史会话列表]  数据库性能分析工作室                                            │
├─────────────────────────┬──────────────────────────────────────────────────────────────┤
│ 1. 目标数据库选择 (多选)│ 3. 主分析对话区                                              │
│ [x] 生产主库 (MySQL)    │ ┌──────────────────────────────────────────────────────────┐ │
│ [x] 迁移备库 (GaussDB)  │ │ AI: 已接入【生产主库】与【迁移备库】。你可以让我：        │ │
│ [ ] 报表库 (TDSQL)      │ │     - 对比两库中 t_order 表的 Schema 与索引差异            │ │
│ ─────────────────────── │ │     - 分析提供的慢 SQL 在两个方言下的 EXPLAIN 执行计划     │ │
│ [+ 配置/添加新连接]     │ └──────────────────────────────────────────────────────────┘ │
│                         │                                                              │
│ 2. 探查范围与动作控制   │ 4. 人工审批/安全拦截弹窗 (HITL Modal)                       │
│ 探查对象: [输入表名/SQL]│ ┌──────────────────────────────────────────────────────────┐ │
│ 触发动作: [执行EXPLAIN] │ │ ⚠️ 警告：Agent 尝试在【生产主库】执行锁表风险探查          │ │
│                         │ │ SQL: EXPLAIN ANALYZE SELECT ...                            │ │
│                         │ │ [ 拒绝执行 ]                   [ 允许并继续 ]             │ │
│                         │ └──────────────────────────────────────────────────────────┘ │
└─────────────────────────┴──────────────────────────────────────────────────────────────┘
顶部/侧边栏数据库配置与勾选区：

支持配置新数据库连接（Host、Port、DB Name、User、Password、Dialect 方言）。

支持下拉/复选框勾选当前会话要使用的 1~N 个数据库连接。

会话级连接隔离：

密码仅在前端/内存中存在，发送分析请求时以 Header/Context 形式传给后端，零密码落盘。

对话与 Agent 执行轨迹区：

Thought Stream 展布：实时展示 Agent 的推理链条与 ai-tool-db 工具调用（如 getDbSchema(dialect="MYSQL") 与 getDbSchema(dialect="GAUSSDB")）。

HITL (Human-In-The-Loop) 人工安全拦截：

当 Agent 尝试执行带写操作、全表扫描无 WHERE 或耗时较长的探查时，前端弹出审批卡片，用户确认后恢复 Agent 执行。


针对**数据库性能分析场景**，在已建好 `session_db_analysis` 表（或现有持久化表）的基础上，我们需要完成从**后端数据结构设计、连接池安全隔离机制、多方言（TDSQL / MySQL / GaussDB）适配，到多库探查 Agent 调度逻辑**的全套设计。

---

### 一、 核心需求拆解与安全边界

1. **连接多方言支持**：原生支持 `MYSQL`、`TDSQL`（基于 MySQL/PostgreSQL 协议架构）、`GAUSSDB`（基于 PostgreSQL / OpenGauss 协议）。
2. **会话级多库选择**：用户在界面勾选 1~N 个数据库，请求时透传连接参数。
3. **“选中即允许，未选严禁访问”隔离原则**：
* Agent **只能在被勾选的连接列表中选库**进行 Schema 获取、EXPLAIN 执行或元数据对比。
* 如果用户只勾选了 DB-A 和 DB-B，即使 Prompt 提示“帮我对比一下 DB-C”，Agent 也会被**硬隔离机制**拦截并提醒用户：“DB-C 未在当前勾选列表中，无法访问”。



---

### 二、 前后端交互的数据结构设计

#### 1. 前端发送给后端的 Context 数据包结构

每次发起分析对话时，前端将**勾选的数据库连接清单**（包含临时密码或内存加密凭证）随请求一起送达：

```json
{
  "session_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "prompt": "对比一下【生产主库】和【迁移Gauss库】中 t_order 表的 Schema 差异，并分别生成 EXPLAIN 执行计划",
  "active_db_connections": [
    {
      "alias": "生产主库",
      "dialect": "MYSQL",          // 可选: MYSQL, TDSQL, GAUSSDB
      "host": "192.168.1.100",
      "port": 3306,
      "db_name": "order_db",
      "user": "read_only_user",
      "password": "EncryptedPasswordOrPlainText" // 内存中传递，不写入数据库
    },
    {
      "alias": "迁移Gauss库",
      "dialect": "GAUSSDB",
      "host": "10.0.2.200",
      "port": 8000,
      "db_name": "order_db",
      "user": "gauss_analyst",
      "password": "EncryptedPasswordOrPlainText"
    }
  ]
}

```

#### 2. 持久化表字段同步（只存元数据，无密码落盘）

更新 `session_db_analysis` 表中的 `selected_db_meta` 字段，抹除敏感密码后存储：

```json
[
  {
    "alias": "生产主库",
    "dialect": "MYSQL",
    "host": "192.168.1.100",
    "port": 3306,
    "db_name": "order_db",
    "user": "read_only_user"
  },
  {
    "alias": "迁移Gauss库",
    "dialect": "GAUSSDB",
    "host": "10.0.2.200",
    "port": 8000,
    "db_name": "order_db",
    "user": "gauss_analyst"
  }
]

```

---

### 三、 后端：会话级多连接隔离与多方言适配器设计

为了实现“只能从选中的数据库去访问”，我们利用 `ai-tool-db` SDK 扩展一个 **`SessionBoundDbManager`（会话绑定数据库管理器）**。

#### 1. 动态连接注册与隔离架构

```
                     请求传入 (Active DB Connections: DB-1, DB-2)
                                      │
                                      ▼
                        ┌───────────────────────────┐
                        │  SessionBoundDbManager    │
                        │ (ThreadLocal / Session)   │
                        └─────────────┬─────────────┘
                                      │ 仅注册并校验勾选的连接
                                      ▼
                   ┌─────────────────────────────────────┐
                   │  内存临时连接池 Map<Alias, DataSource>│
                   │   ├── [生产主库] -> HikariPool-1     │
                   │   └── [迁移Gauss库] -> HikariPool-2  │
                   └──────────────────┬──────────────────┘
                                      │
                 ┌────────────────────┴────────────────────┐
                 │ Agent 工具调用拦截检验 (Strict Route Guard)│
                 └────────────────────┬────────────────────┘
                                      │ 传入 target_alias
                        ┌─────────────┴─────────────┐
                        │ Alias 是否存在于 Map 中?  │
                        └──────┬──────────────┬─────┘
                              YES            NO
                               │              │
                               ▼              ▼
                     允许执行探查/SQL      【硬拦截抛错】
                                        "错误: 数据库别名未经授权或未勾选"

```

#### 2. `SessionBoundDbManager` 核心核心代码实现

```java
public class SessionBoundDbManager implements AutoCloseable {

    private final Map<String, DbClientAdapter> activeAdapterMap = new ConcurrentHashMap<>();

    /**
     * 根据前端传入的已勾选列表，初始化内存中的动态连接池
     */
    public void registerSelectedDatabases(List<DbConnectionConfig> configs) {
        for (DbConnectionConfig config : configs) {
            // 限制连接池大小为 2~3，防止探查给目标库造成压力
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(buildJdbcUrl(config));
            hikariConfig.setUsername(config.getUser());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(3);
            hikariConfig.setConnectionTimeout(5000); // 5秒超时

            HikariDataSource ds = new HikariDataSource(hikariConfig);
            
            // 根据 Dialect 实例化对应方言的探查适配器 (TDSQL / MYSQL / GAUSSDB)
            DbClientAdapter adapter = DbAdapterFactory.createAdapter(config.getDialect(), ds);
            
            // 以 Alias 别名作为唯一 Key 注册
            activeAdapterMap.put(config.getAlias(), adapter);
        }
    }

    /**
     * 强隔离获取连接：只能获取勾选列表中的库
     */
    public DbClientAdapter getAuthorizedAdapter(String targetAlias) {
        DbClientAdapter adapter = activeAdapterMap.get(targetAlias);
        if (adapter == null) {
            throw new SecurityException("非法访问拦截：数据库【" + targetAlias + "】未在当前会话的选中列表中！允许访问的库有: " + activeAdapterMap.keySet());
        }
        return adapter;
    }

    public Set<String> getAuthorizedAliases() {
        return activeAdapterMap.keySet();
    }

    @Override
    public void close() {
        // 请求/会话结束，立即释放所有临时 Hikari 连接池
        activeAdapterMap.values().forEach(DbClientAdapter::close);
        activeAdapterMap.clear();
    }
}

```

---

### 四、 方言适配器（MySQL / TDSQL / GaussDB）的探查抽象

虽然数据库类型不同，但 `ai-tool-db` 需要统一暴露给 Agent 的工具接口。

#### 1. 方言驱动与 SQL 语法映射

| 方言 (Dialect) | 驱动 (Driver) | 协议/方言特性 | Schema 提取逻辑 | EXPLAIN 语法 |
| --- | --- | --- | --- | --- |
| **MYSQL** | `com.mysql.cj.jdbc.Driver` | 标准 MySQL | 查询 `information_schema.columns` / `SHOW CREATE TABLE` | `EXPLAIN FORMAT=JSON <SQL>` |
| **TDSQL** | `com.mysql.cj.jdbc.Driver` (MySQL版) | 腾讯云分布式/单机 MySQL 架构 | 兼容 MySQL 语法，支持分布式 Key 查询 | `EXPLAIN <SQL>` |
| **GAUSSDB** | `org.opengauss.Driver` 或 `org.postgresql.Driver` | Postgres/OpenGauss 方言 | 查询 `pg_catalog` / `information_schema` | `EXPLAIN (VERBOSE, COSTS) <SQL>` |

#### 2. 工厂模式实例化 (`DbAdapterFactory`)

```java
public class DbAdapterFactory {
    public static DbClientAdapter createAdapter(String dialect, DataSource ds) {
        return switch (dialect.toUpperCase()) {
            case "MYSQL", "TDSQL" -> new MySqlDbAdapter(ds);
            case "GAUSSDB" -> new GaussDbAdapter(ds);
            default -> throw new IllegalArgumentException("暂不支持的数据库方言: " + dialect);
        };
    }
}

```

---

### 五、 Agent 侧的 Function Calling 工具定义与严格路由

为了让大模型明确知道“当前只能操作这几个库”，我们在构造 Agent System Prompt 时，**动态注入已授权的数据库别名列表**。

#### 1. System Prompt 动态注入约束

```text
你是一个专业的数据库性能诊断专家。
【严格安全约束】：当前用户在 UI 界面中仅勾选并授权了以下数据库连接：
{{ authorized_db_aliases }} (例如: ["生产主库", "迁移Gauss库"])

你调用的任何工具，必须指定 target_alias 参数，且该参数必须完全匹配上述授权别名之一。
严禁尝试猜测或访问未经授权的数据库。

```

#### 2. 工具定义 (Tools) 示例：`get_db_schema` 与 `explain_sql`

给大模型绑定的 `ai-tool-db` 函数定义如下：

```json
[
  {
    "name": "get_db_schema",
    "description": "获取指定授权数据库中某张表的 Schema 和索引信息",
    "parameters": {
      "type": "object",
      "properties": {
        "target_alias": {
          "type": "string",
          "description": "目标数据库别名，必须在已授权列表 [生产主库, 迁移Gauss库] 中"
        },
        "table_name": {
          "type": "string",
          "description": "需要查询的表名"
        }
      },
      "required": ["target_alias", "table_name"]
    }
  },
  {
    "name": "explain_sql",
    "description": "在指定授权数据库上获取 SQL 的执行计划",
    "parameters": {
      "type": "object",
      "properties": {
        "target_alias": {
          "type": "string",
          "description": "目标数据库别名"
        },
        "sql_query": {
          "type": "string",
          "description": "需要分析的 SELECT 查询 SQL"
        }
      },
      "required": ["target_alias", "sql_query"]
    }
  }
]

```

#### 3. 工具执行层安全校验拦截 (Tool Executor)

当 Agent 触发 `get_db_schema` 时：

```java
@ToolFunction(name = "get_db_schema")
public String getDbSchema(String targetAlias, String tableName) {
    // 1. 强制校验别名合法性（防止 Agent 幻觉或越权）
    DbClientAdapter adapter = sessionBoundDbManager.getAuthorizedAdapter(targetAlias);
    
    // 2. 执行方言对应的 Schema 探查
    TableSchema schema = adapter.extractSchema(tableName);
    
    // 3. 格式化为 Markdown 格式返回给 Agent 供对比
    return schema.toMarkdown();
}

```

---

### 六、 前后端交互完整闭环流程

1. **用户勾选**：前端勾选 `生产主库(MySQL)` 和 `迁移Gauss库(GaussDB)`。
2. **发起分析**：输入“分析 `t_order` 表”，将 2 个库的连接参数（带密码）放入 HTTP Request Header/Body。
3. **后端拦截建立**：
* `LLMTraceContext` 记录 `scene_type="DB_ANALYSIS"`, `session_id`。
* `SessionBoundDbManager` 在内存中建立 2 个临时 Hikari 线程池。
* `ai-client-sdk` 开始记录 Trace 到 PostgreSQL 的 `sys_llm_invoke_log` 表。


4. **Agent 并发探查**：
* Agent 识别到需要对比两库，连续发起两次 Tool Call：
* Tool Call 1: `get_db_schema(target_alias="生产主库", table_name="t_order")`
* Tool Call 2: `get_db_schema(target_alias="迁移Gauss库", table_name="t_order")`


* `SessionBoundDbManager` 路由到各自的适配器执行 SQL 并返回结构。


5. **Agent 对比输出**：Agent 拿到两边的 Schema 和 EXPLAIN 结果，输出 Markdown 表格对比差异。
6. **连接销毁**：SSE 响应流结束，`SessionBoundDbManager.close()` 执行，内存中的 2 个 Hikari 线程池立即销毁，资源释放。

---

### 总结

这种设计做到了：

* **多方言兼容**：统一适配器抹平 MySQL、TDSQL、GaussDB 的 SQL 探查差异。
* **物理级只能读选中库**：动态连接池只按勾选列表建立，未勾选的库连 Connection 都没有，防范越权。
* **零密码落盘与高效销毁**：凭证只存在于单个 HTTP/SSE 请求的生命周期内存中。