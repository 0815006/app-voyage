-- ============================================================
-- Voyage Platform - 性能测试 + 造数引擎 + 资源核查 模块建表 V1.1
-- 数据库: MySQL 8.4
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- ============================================================

USE voyage_db;

-- ============================================================
-- 第一部分：公共依赖表
-- ============================================================

-- 1. 假日信息表 (性能测试场景日期计算依赖)
DROP TABLE IF EXISTS `com_workholiday`;
CREATE TABLE `com_workholiday` (
  `date`   DATE         NOT NULL COMMENT '日期',
  `status` VARCHAR(255) DEFAULT NULL COMMENT '状态，0 放假 1 上班',
  `msg`    VARCHAR(255) DEFAULT NULL COMMENT '节日信息',
  PRIMARY KEY (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假日信息表';

-- 2. 部署方案环境资源清单表 (资源核查 + 性能测试共用)
DROP TABLE IF EXISTS `performance_resource_info`;
CREATE TABLE `performance_resource_info` (
  `id`                               INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `serial_number`                    INT          DEFAULT NULL COMMENT '序号',
  `task_name`                        VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
  `task_num`                         VARCHAR(32)  DEFAULT NULL COMMENT '任务编号',
  `service_name`                     VARCHAR(255) DEFAULT NULL COMMENT '服务名称',
  `english_short_name`               VARCHAR(100) DEFAULT NULL COMMENT '英文简称',
  `batch_name`                       VARCHAR(32)  DEFAULT NULL COMMENT '批次名称',
  `business_dept`                    VARCHAR(100) DEFAULT NULL COMMENT '业务部门',
  `project_type`                     VARCHAR(32)  DEFAULT NULL COMMENT '项目类型',
  `disaster_backup_level`            VARCHAR(32)  DEFAULT NULL COMMENT '灾备等级',
  `availability_level`               VARCHAR(32)  DEFAULT NULL COMMENT '可用性等级',
  `deployment_location`              VARCHAR(32)  DEFAULT NULL COMMENT '部署地点',
  `network_deployment`               VARCHAR(32)  DEFAULT NULL COMMENT '网络部署',
  `system_platform`                  VARCHAR(32)  DEFAULT NULL COMMENT '系统平台',
  `paas_platform_type`               VARCHAR(32)  DEFAULT NULL COMMENT 'PAAS平台类型',
  `theme_count`                      INT          DEFAULT NULL COMMENT '主题数量',
  `queue_count`                      INT          DEFAULT NULL COMMENT '队列数量',
  `shard_count`                      INT          DEFAULT NULL COMMENT '分片数量',
  `per_shard_capacity_gb`            INT          DEFAULT NULL COMMENT '每分片容量（G）',
  `redundancy_method`                VARCHAR(32)  DEFAULT NULL COMMENT '冗余方式',
  `operating_system`                 VARCHAR(255) DEFAULT NULL COMMENT '操作系统',
  `middleware`                       VARCHAR(255) DEFAULT NULL COMMENT '中间件',
  `partition_usage`                  VARCHAR(100) DEFAULT NULL COMMENT '分区用途',
  `partition_usage_name`             VARCHAR(100) DEFAULT NULL COMMENT '分区用途名称',
  `hostname`                         VARCHAR(32)  DEFAULT NULL COMMENT '主机名',
  `ip_address`                       VARCHAR(32)  DEFAULT NULL COMMENT 'IP地址',
  `backup_ip`                        VARCHAR(32)  DEFAULT NULL COMMENT '数据备份IP',
  `cpu_cores`                        INT          DEFAULT NULL COMMENT 'CPU核心数',
  `memory_gb`                        INT          DEFAULT NULL COMMENT '内存（GB）',
  `dedicated_storage_gb`             INT          DEFAULT NULL COMMENT '独占存储（GB）',
  `shared_storage_id`                VARCHAR(32)  DEFAULT NULL COMMENT '共享存储编号',
  `san_storage_gb`                   INT          DEFAULT NULL COMMENT 'SAN存储（GB）',
  `nas_storage_gb`                   INT          DEFAULT NULL COMMENT 'NAS存储（GB）',
  `signature_server`                 VARCHAR(32)  DEFAULT NULL COMMENT '是否有签名服务器',
  `encryption_device`                VARCHAR(32)  DEFAULT NULL COMMENT '是否有加密机',
  `load_balancer`                    VARCHAR(32)  DEFAULT NULL COMMENT '是否有负载均衡器',
  `ssl_accelerator`                  VARCHAR(32)  DEFAULT NULL COMMENT '是否有SSL加速器',
  `remarks`                          VARCHAR(2000) DEFAULT NULL COMMENT '备注（外设型号）',
  `partition_role`                   VARCHAR(100) DEFAULT NULL COMMENT '分区角色',
  `revision_time`                    DATETIME     DEFAULT NULL COMMENT '修订时间',
  `middleware_reason_below_baseline` VARCHAR(255) DEFAULT NULL COMMENT '中间件使用低于基线版本原因',
  `os_reason_below_baseline`         VARCHAR(255) DEFAULT NULL COMMENT '操作系统使用低于基线版本原因',
  `resource_pool`                    VARCHAR(255) DEFAULT NULL COMMENT '资源池',
  `original_file_name`               VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `file_name`                        VARCHAR(255) DEFAULT NULL COMMENT '上传文件名',
  `product_id`                       VARCHAR(32)  DEFAULT NULL COMMENT '产品标识',
  `batch_no`                         VARCHAR(32)  NOT NULL COMMENT '性能测试任务-批次 (如：2606)',
  `file_source`                      VARCHAR(32)  DEFAULT NULL COMMENT '文件来源：部署方案 / 资源申请表',
  `create_time`                      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `create_operator`                  VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
  `last_time`                        DATETIME     DEFAULT NULL COMMENT '更新时间',
  `last_operator`                    VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署方案环境资源清单表';

-- ============================================================
-- 第二部分：性能测试模块 (10 张表)
-- ============================================================

-- 3. 性能测试任务主表
DROP TABLE IF EXISTS `perf_task`;
CREATE TABLE `perf_task` (
  `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id`               VARCHAR(64)   NOT NULL COMMENT '产品/牵头组件标识 (如：BPS-D-AUTO)',
  `batch_no`                 VARCHAR(32)   NOT NULL COMMENT '批次 (如：2606)',
  `task_name`                VARCHAR(255)  DEFAULT NULL COMMENT '任务名称',
  `test_task_no`             VARCHAR(64)   DEFAULT NULL COMMENT '测试任务编号',
  `prod_task_no`             VARCHAR(64)   DEFAULT NULL COMMENT '生产任务编号',
  `req_no`                   VARCHAR(64)   DEFAULT NULL COMMENT '需求编号',
  `proj_name`                VARCHAR(255)  DEFAULT NULL COMMENT '项目名称',
  `proj_no`                  VARCHAR(64)   DEFAULT NULL COMMENT '项目编号',
  `test_dept`                VARCHAR(128)  DEFAULT NULL COMMENT '测试部门',
  `dev_dept`                 VARCHAR(128)  DEFAULT NULL COMMENT '开发部门',
  `start_time`               DATETIME      DEFAULT NULL COMMENT '测试开始时间',
  `end_time`                 DATETIME      DEFAULT NULL COMMENT '测试结束时间',
  `perf_manager`             VARCHAR(64)   DEFAULT NULL COMMENT '性能测试经理姓名',
  `test_arch`                VARCHAR(64)   DEFAULT NULL COMMENT '测试架构师姓名',
  `project_manager`          VARCHAR(64)   DEFAULT NULL COMMENT '项目经理姓名',
  `recorder_range`           TEXT          DEFAULT NULL COMMENT '填报人员范围 (7位员工号，逗号分隔)',
  `creator_id`               VARCHAR(7)    NOT NULL COMMENT '创建人ID (7位员工号)',
  `status`                   INT           DEFAULT 10 COMMENT '任务状态 (10:新建/调研中, 20:方案待明确, 30:已定稿)',
  `total_user_count`         DECIMAL(12,4) DEFAULT NULL COMMENT '用户数总数',
  `daily_online_user_count`  DECIMAL(12,4) DEFAULT NULL COMMENT '日均在线用户数',
  `daily_peak_tps`           DECIMAL(10,3) DEFAULT NULL COMMENT '日交易峰值TPS',
  `annual_peak_tps`          DECIMAL(10,3) DEFAULT NULL COMMENT '年交易峰值TPS',
  `selected_tran_tps_sum`    DECIMAL(10,3) DEFAULT NULL COMMENT '选中交易TPS之和',
  `batch_total_duration`     VARCHAR(64)   DEFAULT NULL COMMENT '预估整体批量时长',
  `batch_total_data_volume`  VARCHAR(128)  DEFAULT NULL COMMENT '预估整体数据量',
  `batch_parallel_degree`    VARCHAR(64)   DEFAULT NULL COMMENT '并行度',
  `batch_max_parallel_count` VARCHAR(32)   DEFAULT NULL COMMENT '最大并行数',
  `create_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch_product` (`batch_no`, `product_id`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='性能测试任务主表';

-- 4. 联机交易调研与指标方案表
DROP TABLE IF EXISTS `perf_task_tran`;
CREATE TABLE `perf_task_tran` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`               BIGINT        NOT NULL COMMENT '关联主表perf_task的id',
  `module_name`           VARCHAR(64)   DEFAULT NULL COMMENT '产品名称/模块名称',
  `tran_name`             VARCHAR(128)  NOT NULL COMMENT '交易名称',
  `tran_code`             VARCHAR(128)  DEFAULT NULL COMMENT '交易代码/apicode',
  `interface_type`        VARCHAR(64)   DEFAULT NULL COMMENT '用户群说明/接口类型',
  `tran_daily_vol`        DECIMAL(12,4) DEFAULT NULL COMMENT '生产日峰值交易量(万笔/日)',
  `tran_peak_hour_vol`    DECIMAL(12,4) DEFAULT NULL COMMENT '生产高峰时段量(万笔/小时)',
  `tran_peak_tps`         DECIMAL(10,3) DEFAULT NULL COMMENT '生产每秒最大TPS',
  `tran_avg_rt`           DECIMAL(10,3) DEFAULT NULL COMMENT '生产平均响应时间(秒)',
  `tran_max_rt`           DECIMAL(10,3) DEFAULT NULL COMMENT '生产最大响应时间(秒)',
  `target_daily_vol`      DECIMAL(12,4) DEFAULT NULL COMMENT '目标日峰值量(万笔/日)',
  `target_peak_hour_vol`  DECIMAL(12,4) DEFAULT NULL COMMENT '目标高峰时段量(万笔/小时)',
  `target_tps`            DECIMAL(10,3) DEFAULT NULL COMMENT '测试目标TPS',
  `target_rt`             DECIMAL(10,3) DEFAULT NULL COMMENT '目标平均响应时间(秒)',
  `target_max_rt`         DECIMAL(10,3) DEFAULT NULL COMMENT '目标最大响应时间(秒)',
  `target_success_rate`   DECIMAL(5,2)  DEFAULT 100.00 COMMENT '目标交易成功率(%)',
  `target_think_time`     DECIMAL(10,3) DEFAULT NULL COMMENT '用户平均操作时间/思考时间(秒)',
  `is_selected`           TINYINT(1)    DEFAULT 1 COMMENT '是否选为性能测试交易',
  `select_reason`         VARCHAR(512)  DEFAULT NULL COMMENT '选取原因(多选枚举，含复杂度说明)',
  `indicator_source`      INT           DEFAULT 1 COMMENT '指标来源(1:实测, 2:采样折算, 3:经验对标)',
  `calculation_process`   TEXT          DEFAULT NULL COMMENT '指标推算过程',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联机交易调研与指标方案表';

-- 5. 批量作业调研与指标方案表
DROP TABLE IF EXISTS `perf_task_batch`;
CREATE TABLE `perf_task_batch` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`              BIGINT       NOT NULL COMMENT '关联主表perf_task的id',
  `job_no`               VARCHAR(32)  DEFAULT NULL COMMENT '作业编号',
  `job_name`             VARCHAR(128) NOT NULL COMMENT '批量作业名称',
  `job_count`            VARCHAR(32)  DEFAULT '1' COMMENT '批量作业数',
  `job_parallel_mode`    VARCHAR(64)  DEFAULT NULL COMMENT '并行方式',
  `job_desc`             TEXT         DEFAULT NULL COMMENT '批量作业功能简述',
  `job_trigger_cond`     VARCHAR(255) DEFAULT NULL COMMENT '触发条件',
  `job_pre_name`         VARCHAR(255) DEFAULT NULL COMMENT '前导作业名',
  `job_concurrent_names` TEXT         DEFAULT NULL COMMENT '可同时并行作业名',
  `job_frequency`        VARCHAR(32)  DEFAULT NULL COMMENT '执行频率',
  `job_data_type`        VARCHAR(64)  DEFAULT NULL COMMENT '最大数据量级数据类型',
  `job_data_volume`      VARCHAR(128) DEFAULT NULL COMMENT '预估数据量级',
  `job_actual_duration`  VARCHAR(64)  DEFAULT NULL COMMENT '生产调研实际运行时长',
  `job_duration`         VARCHAR(64)  DEFAULT NULL COMMENT '预估处理时长/性能要求时长',
  `job_exec_time_point`  VARCHAR(255) DEFAULT NULL COMMENT '生产上批量执行时间点',
  `is_mixed_link`        VARCHAR(16)  DEFAULT '否' COMMENT '是否叠加联机交易',
  `mixed_tran_names`     VARCHAR(255) DEFAULT NULL COMMENT '叠加联机交易名称',
  `has_retry`            VARCHAR(64)  DEFAULT '是' COMMENT '是否有重做机制',
  `retry_desc`           TEXT         DEFAULT NULL COMMENT '重做机制简述',
  `select_reason`        VARCHAR(512) DEFAULT NULL COMMENT '选取原因',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量作业调研与指标方案表';

-- 6. 性能测试数据准备方案主表(定性描述)
DROP TABLE IF EXISTS `perf_data_plan`;
CREATE TABLE `perf_data_plan` (
  `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`           BIGINT   NOT NULL COMMENT '关联性能任务ID',
  `model_analysis`    TEXT     DEFAULT NULL COMMENT '相关数据内容分析',
  `data_constraint`   TEXT     DEFAULT NULL COMMENT '数据约束说明',
  `data_source_desc`  TEXT     DEFAULT NULL COMMENT '数据来源说明',
  `prep_method_desc`  TEXT     DEFAULT NULL COMMENT '数据构造/准备方法描述',
  `cleaning_rule`     TEXT     DEFAULT NULL COMMENT '数据脱敏/清洗规则',
  `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='性能测试数据准备方案主表(定性描述)';

-- 7. 性能测试数据准备明细表(表级规模)
DROP TABLE IF EXISTS `perf_data_detail`;
CREATE TABLE `perf_data_detail` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`           BIGINT        NOT NULL COMMENT '关联性能任务ID',
  `data_type`         INT           DEFAULT 1 COMMENT '数据分类(1:核心业务表, 2:基础数据/码表)',
  `table_name_en`     VARCHAR(128)  NOT NULL COMMENT '英文表名',
  `table_name_cn`     VARCHAR(128)  DEFAULT NULL COMMENT '中文表名/描述',
  `table_rows_count`  BIGINT        DEFAULT NULL COMMENT '生产当前存量行数(万行)',
  `table_growth_rate` DECIMAL(5,2)  DEFAULT NULL COMMENT '预估年增长率(%)',
  `target_rows_count` BIGINT        DEFAULT NULL COMMENT '测试目标造数行数(万行)',
  `data_dist_desc`    TEXT          DEFAULT NULL COMMENT '数据量情况/数据特征分布',
  `prep_method`       VARCHAR(64)   DEFAULT '脚本自造' COMMENT '准备方式(生产脱敏借数/脚本自造)',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='性能测试数据准备明细表(表级规模)';

-- 8. 性能测试场景定义主表
DROP TABLE IF EXISTS `perf_task_scene`;
CREATE TABLE `perf_task_scene` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`               BIGINT        NOT NULL COMMENT '关联主表id',
  `scene_type`            INT           NOT NULL COMMENT '1:基准, 2:单负载, 3:混合负载, 4:稳定性, 5:极限, 6:批量',
  `scene_name`            VARCHAR(128)  NOT NULL COMMENT '场景名称',
  `target_tps_ratio`      DECIMAL(5,2)  DEFAULT 100.00 COMMENT '目标TPS百分比',
  `target_total_tps`      DECIMAL(10,2) DEFAULT NULL COMMENT '目标总TPS值',
  `test_objective`        TEXT          DEFAULT NULL COMMENT '测试目的',
  `implementation_method` TEXT          DEFAULT NULL COMMENT '实施方法',
  `end_condition`         TEXT          DEFAULT NULL COMMENT '结束条件(主要针对极限测试)',
  `is_selected`           TINYINT(1)    DEFAULT 1 COMMENT '是否勾选执行',
  `global_duration`       INT           DEFAULT NULL COMMENT '全局预计持续时间(分)',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='性能测试场景定义主表';

-- 9. 场景内交易配置明细表
DROP TABLE IF EXISTS `perf_task_scene_detail`;
CREATE TABLE `perf_task_scene_detail` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id`          BIGINT        NOT NULL COMMENT '关联perf_task_scene.id',
  `tran_id`           BIGINT        NOT NULL COMMENT '关联perf_task_tran.id',
  `tran_name`         VARCHAR(128)  DEFAULT NULL COMMENT '交易名称(冗余)',
  `target_tps`        DECIMAL(10,2) DEFAULT NULL COMMENT '预期目标TPS',
  `target_rt`         DECIMAL(10,3) DEFAULT NULL COMMENT '预期响应时间(秒)',
  `target_success_rate` DECIMAL(5,2) DEFAULT 100.00 COMMENT '预期成功率(%)',
  `vu_count`          INT           DEFAULT NULL COMMENT '并发用户数',
  `ramp_up`           INT           DEFAULT NULL COMMENT '启动时间(秒)',
  `pacing`            DECIMAL(10,2) DEFAULT NULL COMMENT '迭代间隔',
  `throughput_timer`  DECIMAL(10,2) DEFAULT NULL COMMENT '常数吞吐量定时器配置',
  `iterations`        INT           DEFAULT NULL COMMENT '迭代次数',
  PRIMARY KEY (`id`),
  KEY `idx_scene_id` (`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景内交易配置明细表';

-- 10. 场景执行结果主表(记录压测轮次和时间轴)
DROP TABLE IF EXISTS `perf_task_scene_result`;
CREATE TABLE `perf_task_scene_result` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id`         BIGINT        NOT NULL COMMENT '关联perf_task_scene.id',
  `round_number`     INT           DEFAULT 1 COMMENT '轮次(第1轮, 第2轮...)',
  `test_env`         VARCHAR(64)   DEFAULT NULL COMMENT '执行环境',
  `run_status`       INT           DEFAULT 0 COMMENT '执行状态(0:执行中, 1:已完成, 2:异常中断)',
  `run_start_time`   DATETIME      DEFAULT NULL COMMENT '压测实际开始时间',
  `run_end_time`     DATETIME      DEFAULT NULL COMMENT '压测实际结束时间',
  `is_standard`      TINYINT(1)    DEFAULT 0 COMMENT '是否作为达标轮次(1:是, 0:否)',
  `actual_total_tps` DECIMAL(10,2) DEFAULT NULL COMMENT '本轮实测总TPS',
  `result_status`    INT           DEFAULT 0 COMMENT '结果状态(0:未达标, 1:已达标, 2:待评审)',
  `summary_remark`   VARCHAR(512)  DEFAULT NULL COMMENT '执行总结',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_scene_id` (`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景执行结果主表(记录压测轮次和时间轴)';

-- 11. 场景执行结果交易明细表(记录每轮各交易实测值)
DROP TABLE IF EXISTS `perf_task_scene_result_detail`;
CREATE TABLE `perf_task_scene_result_detail` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `result_id`           BIGINT        NOT NULL COMMENT '关联perf_task_scene_result.id',
  `tran_id`             BIGINT        NOT NULL COMMENT '关联perf_task_tran.id',
  `tran_name`           VARCHAR(128)  DEFAULT NULL COMMENT '交易名称(冗余)',
  `actual_tps`          DECIMAL(10,2) DEFAULT NULL COMMENT '实测TPS',
  `actual_rt`           DECIMAL(10,3) DEFAULT NULL COMMENT '实测平均响应时间(s)',
  `actual_rt_90`        DECIMAL(10,3) DEFAULT NULL COMMENT '实测90%响应时间(s)',
  `actual_rt_max`       DECIMAL(10,3) DEFAULT NULL COMMENT '实测最大响应时间(s)',
  `actual_success_rate` DECIMAL(5,2)  DEFAULT NULL COMMENT '实测成功率(%)',
  `total_request_count` BIGINT        DEFAULT NULL COMMENT '总请求笔数',
  `actual_db_count`     BIGINT        DEFAULT NULL COMMENT '实际落表笔数',
  `is_compliant`        TINYINT(1)    DEFAULT 1 COMMENT '单交易是否达标(1:是, 0:否)',
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_result_id` (`result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景执行结果交易明细表(记录每轮各交易实测值)';

-- 12. 性能测试数据信息表 (关联任务+批次的数据快照)
DROP TABLE IF EXISTS `perf_task_data`;
CREATE TABLE `perf_task_data` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id`     BIGINT       NOT NULL COMMENT '关联perf_task.id',
  `batch_name`  VARCHAR(32)  DEFAULT NULL COMMENT '批次名称',
  `data_label`  VARCHAR(128) DEFAULT NULL COMMENT '数据标签',
  `data_desc`   TEXT         DEFAULT NULL COMMENT '数据描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='性能测试数据信息表';

-- ============================================================
-- 第三部分：造数引擎模块 (7 张表)
-- ============================================================

-- 13. 文件模型主表
DROP TABLE IF EXISTS `meta_file_model`;
CREATE TABLE `meta_file_model` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name`       VARCHAR(100) NOT NULL COMMENT '模型名称',
  `has_header`       TINYINT(1)   DEFAULT 1 COMMENT '是否有文件头：1-有，0-无',
  `has_footer`       TINYINT(1)   DEFAULT 1 COMMENT '是否有文件尾：1-有，0-无',
  `split_type`       VARCHAR(20)  NOT NULL COMMENT '分隔符或定长: DELIMITER / FIXED',
  `delimiter`        VARCHAR(10)  DEFAULT NULL COMMENT '分隔符内容',
  `line_ending_char` VARCHAR(10)  DEFAULT NULL COMMENT '行结尾固定符号',
  `encoding`         VARCHAR(20)  DEFAULT 'UTF-8' COMMENT 'UTF-8/GBK',
  `max_rows_limit`   INT          DEFAULT 10000 COMMENT '记录数安全阈值-超过则报错',
  `owner_id`         VARCHAR(50)  NOT NULL COMMENT '创建人ID-记录员工工号',
  `status`           VARCHAR(20)  DEFAULT 'DRAFT' COMMENT '模型状态: DRAFT/PUBLISHED/DISABLED',
  `model_version`    INT          DEFAULT 1 COMMENT '模型版本号',
  `shared_with`      VARCHAR(100) DEFAULT NULL COMMENT '共享用户ID逗号分隔',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件模型主表';

-- 14. 字段定义明细表
DROP TABLE IF EXISTS `meta_field_definition`;
CREATE TABLE `meta_field_definition` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`          BIGINT      NOT NULL COMMENT '所属模型ID',
  `section`           VARCHAR(20) NOT NULL COMMENT '字段所属区域: FILENAME/HEADER/BODY/FOOTER',
  `parent_id`         BIGINT      DEFAULT NULL COMMENT '仅支持两层，子字段填父ID',
  `field_key`         VARCHAR(50) NOT NULL COMMENT '字段唯一变量名，供后面引用',
  `field_name`        VARCHAR(100) DEFAULT NULL COMMENT '业务描述',
  `sort_index`        INT         NOT NULL COMMENT '后端真实排序依据',
  `level`             TINYINT     DEFAULT 1 COMMENT '字段层级：1-一级，2-二级',
  `length`            INT         NOT NULL COMMENT '定长模式下的字节/字符长度',
  `is_required`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否必填：1-是，0-否',
  `padding_direction` VARCHAR(10) DEFAULT 'NONE' COMMENT '补位方向：LEFT/RIGHT/NONE',
  `padding_char`      VARCHAR(5)  DEFAULT ' ' COMMENT '补位字符',
  `rule_type`         VARCHAR(30) NOT NULL COMMENT '规则类型: FIXED/DATE/ENUM/REF_FILE/REF_FIELD/SEQ/SUM/COUNT/RANDOM/EXPRESSION',
  `ref_field_key`     VARCHAR(50) DEFAULT NULL COMMENT '引用的同报文内字段Key',
  `ref_enum_key`      VARCHAR(50) DEFAULT NULL COMMENT '引用的枚举库Key',
  `ref_sequence_key`  VARCHAR(50) DEFAULT NULL COMMENT '引用的序列号Key',
  `ref_file_id`       BIGINT      DEFAULT NULL COMMENT '引用的素材文件ID',
  `rule_config_json`  JSON        NOT NULL COMMENT '包含金额DSL、表达式DSL、随机规则等',
  PRIMARY KEY (`id`),
  KEY `idx_model_sort` (`model_id`, `section`, `sort_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段定义明细表';

-- 15. 枚举库
DROP TABLE IF EXISTS `meta_enum_library`;
CREATE TABLE `meta_enum_library` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enum_key`  VARCHAR(50)  NOT NULL COMMENT '枚举唯一标识，如 sex',
  `enum_name` VARCHAR(100) DEFAULT NULL COMMENT '枚举名称/业务描述',
  `items`     JSON         NOT NULL COMMENT '枚举项JSON: [{"val":"0", "desc":"男"}, {"val":"1", "desc":"女"}]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enum_key` (`enum_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举库';

-- 16. 引用素材文件
DROP TABLE IF EXISTS `meta_ref_file`;
CREATE TABLE `meta_ref_file` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ref_name`       VARCHAR(50)  NOT NULL COMMENT '引用名称/唯一标识',
  `file_path`      VARCHAR(255) NOT NULL COMMENT '素材文件路径',
  `parse_type`     VARCHAR(20)  NOT NULL COMMENT '解析类型：DELIMITER / FIXED',
  `delimiter`      VARCHAR(10)  DEFAULT NULL COMMENT '分隔符内容',
  `column_mapping` JSON         DEFAULT NULL COMMENT '列索引与Key映射: {"userName": 1, "age": 2}',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref_name` (`ref_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引用素材文件';

-- 17. 并发锁控与游标表
DROP TABLE IF EXISTS `meta_sequence_tracker`;
CREATE TABLE `meta_sequence_tracker` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `target_type`   VARCHAR(20)  NOT NULL COMMENT '追踪类型：SEQ / REF_FILE',
  `target_id`     VARCHAR(100) NOT NULL COMMENT '追踪目标标识: modelId:fieldKey 或 refFileId',
  `current_value` BIGINT       DEFAULT 0 COMMENT '当前值/游标位置',
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='并发锁控与游标表';

-- 18. FTP配置表
DROP TABLE IF EXISTS `meta_ftp_config`;
CREATE TABLE `meta_ftp_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name`        VARCHAR(100) NOT NULL COMMENT '配置名称',
  `ftp_ip`      VARCHAR(100) NOT NULL COMMENT 'FTP服务器IP',
  `ftp_port`    INT          DEFAULT 21 COMMENT 'FTP端口',
  `username`    VARCHAR(100) NOT NULL COMMENT 'FTP用户名',
  `password`    VARCHAR(200) NOT NULL COMMENT 'FTP密码',
  `remote_path` VARCHAR(500) NOT NULL COMMENT '远程目录路径',
  `create_user` VARCHAR(50)  DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FTP配置表';

-- 19. 生成历史与任务表
DROP TABLE IF EXISTS `meta_entity_file`;
CREATE TABLE `meta_entity_file` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`     BIGINT       NOT NULL COMMENT '所属模型ID',
  `file_name`    VARCHAR(255) NOT NULL COMMENT '生成的文件名',
  `file_type`    VARCHAR(20)  NOT NULL COMMENT '文件类型：PREVIEW / FORMAL',
  `storage_path` VARCHAR(255) NOT NULL COMMENT '文件存储路径',
  `row_count`    INT          DEFAULT 0 COMMENT '生成行数',
  `status`       VARCHAR(20)  DEFAULT 'RUNNING' COMMENT '生成状态：SUCCESS/FAILED/RUNNING',
  `create_user`  VARCHAR(50)  DEFAULT NULL COMMENT '创建人',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `duration_ms`  INT          DEFAULT NULL COMMENT '生成耗时(毫秒)',
  `error_msg`    TEXT         DEFAULT NULL COMMENT '错误信息',
  `temp_path`    VARCHAR(255) DEFAULT NULL COMMENT '生成中的临时路径',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成历史与任务表';
