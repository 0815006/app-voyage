-- ============================================================
-- Voyage Platform - 采样任务绘图配置 V2.1
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: sampling_task 新增 chart_config 字段，存放前端绘图配置的 JSON 文本
--       （筛选列 / 展示列等「怎么画」的结构性设定）。后端按黑盒原样存取，
--       不做任何语义解析，通用采样底座保持不变；具体筛选项(数据值)属于
--       查看态临时筛选，不落库。
-- 配套规则: 任务已产生 sampling_history 明细后禁止编辑任务定义，
--       需先「清理已采集记录」才能改，或另建新任务。
-- ============================================================

ALTER TABLE sampling_task ADD COLUMN IF NOT EXISTS chart_config TEXT;

COMMENT ON COLUMN sampling_task.chart_config IS '绘图配置JSON(筛选列/展示列等)，后端黑盒存取不解析语义';
