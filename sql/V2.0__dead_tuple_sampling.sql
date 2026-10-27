-- ============================================================
-- Voyage Platform - 死元组监测采样 V2.0
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: 通用采样监测底座（当前服务于高斯数据库死元组监测）
--       · sampling_task 按操作员工号(emp_no)隔离
--       · connection_id 复用 db_connection_config（零新增密码落盘）
--       · sampling_type 为通用类型标识，当前固定 DEAD_TUPLE
--       · sampling_history.row_data 为纯通用 JSONB：SQL 查出的一行原样序列化，
--         不设任何业务列，业务语义解析全部在前端完成
-- ============================================================

-- 若存在旧表则重建（开发期幂等策略与既有脚本一致）
DROP TABLE IF EXISTS sampling_history;
DROP TABLE IF EXISTS sampling_task;

-- -----------------------------------------------------------
-- 1. 采样任务表
-- -----------------------------------------------------------
CREATE TABLE sampling_task (
  id            VARCHAR(32)   NOT NULL,                 -- 主键(雪花ID)
  emp_no        VARCHAR(7)    NOT NULL,                 -- 归属操作员工号(7位)
  task_name     VARCHAR(100)  NOT NULL,                 -- 任务名称
  connection_id VARCHAR(32)   NOT NULL,                 -- 复用的数据库连接ID(db_connection_config.id)
  sampling_type VARCHAR(32)   NOT NULL DEFAULT 'DEAD_TUPLE', -- 采样类型: DEAD_TUPLE(预留扩展)
  custom_sql    TEXT          NOT NULL,                 -- 自定义采样SQL(仅允许 SELECT/WITH)
  start_time    TIMESTAMP     NOT NULL,                 -- 采样窗口开始时间
  end_time      TIMESTAMP     NOT NULL,                 -- 采样窗口结束时间
  frequency     INTEGER       NOT NULL DEFAULT 2,       -- 采样频率(秒/次)
  status        VARCHAR(16)   NOT NULL DEFAULT 'STOPPED', -- 状态: RUNNING/STOPPED/COMPLETED
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

COMMENT ON TABLE  sampling_task IS '通用采样任务表（死元组监测等）';
COMMENT ON COLUMN sampling_task.id IS '主键，雪花ID(VARCHAR 32)';
COMMENT ON COLUMN sampling_task.emp_no IS '归属操作员工号(7位)，按操作者隔离';
COMMENT ON COLUMN sampling_task.task_name IS '任务名称';
COMMENT ON COLUMN sampling_task.connection_id IS '复用的数据库连接ID(db_connection_config.id)，采样时后端按归属解密，密码不落本表';
COMMENT ON COLUMN sampling_task.sampling_type IS '采样类型: DEAD_TUPLE=死元组监测采样（通用架构类型标识，预留扩展）';
COMMENT ON COLUMN sampling_task.custom_sql IS '自定义采样SQL，后端卡口仅允许 SELECT/WITH 开头';
COMMENT ON COLUMN sampling_task.start_time IS '采样窗口开始时间';
COMMENT ON COLUMN sampling_task.end_time IS '采样窗口结束时间，到达后任务自动置 COMPLETED';
COMMENT ON COLUMN sampling_task.frequency IS '采样频率(秒/次)，如高频采样 2 秒 1 次';
COMMENT ON COLUMN sampling_task.status IS '任务状态: RUNNING/STOPPED/COMPLETED';
COMMENT ON COLUMN sampling_task.create_time IS '创建时间';
COMMENT ON COLUMN sampling_task.update_time IS '更新时间';

-- 按操作者列出任务清单
CREATE INDEX idx_sampling_task_emp_no ON sampling_task (emp_no);

-- 自动维护 update_time
CREATE TRIGGER trg_sampling_task_update_time
BEFORE UPDATE ON sampling_task
FOR EACH ROW EXECUTE FUNCTION set_update_time();

-- -----------------------------------------------------------
-- 2. 通用采样结果明细表（行级 JSON 平铺，无任何业务列）
-- -----------------------------------------------------------
CREATE TABLE sampling_history (
  id            BIGSERIAL     NOT NULL,                 -- 主键自增
  task_id       VARCHAR(32)   NOT NULL REFERENCES sampling_task(id) ON DELETE CASCADE,
  sampling_time TIMESTAMP     NOT NULL,                 -- 采样时间戳(同批次多行共享)
  row_data      JSONB         NOT NULL,                 -- 纯通用行数据: SQL查出的一行原样序列化
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

COMMENT ON TABLE  sampling_history IS '通用采样结果明细表（行级JSON平铺存储，业务语义由前端解析）';
COMMENT ON COLUMN sampling_history.id IS '主键自增';
COMMENT ON COLUMN sampling_history.task_id IS '关联采样任务ID(级联删除)';
COMMENT ON COLUMN sampling_history.sampling_time IS '采样时间戳，同一批次执行拿到的多行数据共享此时间戳';
COMMENT ON COLUMN sampling_history.row_data IS '纯通用JSONB大字段，如 {"relname":"orders","n_dead_tup":1500,"n_live_tup":5000}';
COMMENT ON COLUMN sampling_history.create_time IS '入库时间';

-- 看板核心查询：按任务+时间窗口取历史
CREATE INDEX idx_sampling_history_task_time ON sampling_history (task_id, sampling_time);
