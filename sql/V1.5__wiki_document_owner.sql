-- ============================================================
-- Voyage Platform - Wiki 文档按操作者归属隔离 V1.5
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: wiki_document 由"全员共享"改为"完全按人隔离"：
--       · 新增 emp_no 归属列(7位工号)，仅由后端 EmpContext 从 X-Emp-No 注入
--       · 标题唯一由"全局唯一(uk_wiki_document_title)"改为"按人唯一(emp_no,title)"：
--         不同员工可拥有相同标题，同员工号树内仍唯一，保证 [[双链]] 精准定位
--       · 存量数据无法追溯真实归属，统一归属默认用户 '0000000'(未设置工号默认值)，
--         回填完成后移除列默认值，强制后续插入显式携带归属
-- ============================================================

-- 1. 移除旧的全局标题唯一约束（按人隔离后不再成立，需释放该全局语义）
ALTER TABLE wiki_document DROP CONSTRAINT IF EXISTS uk_wiki_document_title;

-- 2. 新增归属员工号列；存量数据统一归属默认用户 '0000000'
ALTER TABLE wiki_document ADD COLUMN emp_no VARCHAR(7) NOT NULL DEFAULT '0000000';

-- 3. 回填完成后移除默认值，避免漏注归属的写入静默落为 '0000000'
ALTER TABLE wiki_document ALTER COLUMN emp_no DROP DEFAULT;

-- 4. 按人唯一：同员工号下标题唯一（保障同人双链精准定位，跨人允许重名）
CREATE UNIQUE INDEX uk_wiki_document_emp_title ON wiki_document (emp_no, title);

-- 5. 按人快速检索目录树 / 子节点 / 排序重排
CREATE INDEX idx_wiki_document_emp_no ON wiki_document (emp_no);

COMMENT ON COLUMN wiki_document.emp_no IS '归属操作员工号(7位)，完全按人隔离，仅由后端从 X-Emp-No 请求头注入';
