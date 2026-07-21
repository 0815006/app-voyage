# SQL 变更脚本

本目录存放所有数据库变更脚本，由开发者**手工**在 MySQL 中执行。

## 命名规范

按版本顺序命名，格式为 `V[主版本].[次版本]__[描述].sql`，例如：

- `V1.0__init.sql` — 初始建库建表脚本
- `V1.1__add_user_table.sql` — 新增用户表
- `V2.0__refactor_order.sql` — 重构订单表

## 执行方式

使用任意 MySQL 客户端（如 Navicat、DBeaver、mysql CLI）连接目标数据库 `voyage_db` 后，按版本顺序依次执行脚本。

```bash
# 示例：通过 mysql CLI 执行
mysql -u root -p voyage_db < sql/V1.0__init.sql
```

## 注意事项

- **严格按版本顺序执行**，不要跳过中间版本。
- 执行前建议先备份数据库。
- 每个脚本应幂等，支持重复执行不报错（如使用 `CREATE TABLE IF NOT EXISTS`）。
- 脚本执行完毕后请在团队内部同步执行记录。

## SQL 编写规范

- MySQL 8.4 语法
- `ENGINE=InnoDB`
- 字符集 `utf8mb4`
- 所有字段必须带 `COMMENT`
- 核心审计字段命名：`create_time` 和 `update_time`，默认 `CURRENT_TIMESTAMP`
- 核心业务表主键：`VARCHAR(32)`，使用雪花 ID
