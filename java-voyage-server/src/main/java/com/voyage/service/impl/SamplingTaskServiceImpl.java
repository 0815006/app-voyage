package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyage.common.BusinessException;
import com.voyage.dbanalysis.DbConnectionConfig;
import com.voyage.entity.DbConnectionConfigEntity;
import com.voyage.entity.SamplingHistory;
import com.voyage.entity.SamplingTask;
import com.voyage.mapper.SamplingHistoryMapper;
import com.voyage.mapper.SamplingTaskMapper;
import com.voyage.service.DbConnectionConfigService;
import com.voyage.service.SamplingTaskService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通用采样监测 Service 实现（死元组监测采样调度器）。
 * <p>
 * 每个运行中任务独占一个单线程 {@link ScheduledExecutorService} 与临时 Hikari 连接池：
 * 按任务频率执行自定义 SQL，结果集逐行原样序列化为 JSON 落入 sampling_history
 * （同一批次多行共享采样时间戳），后端不感知行内业务语义。
 * 密码仅在启动任务时经 {@link DbConnectionConfigService#getDecrypted} 解密进入内存，
 * 零新增落盘；到达 end_time 自动置 COMPLETED 并销毁资源；
 * 服务重启时将残留 RUNNING 任务恢复为 STOPPED（无孤儿线程）。
 * </p>
 */
@Slf4j
@Service
public class SamplingTaskServiceImpl
        extends ServiceImpl<SamplingTaskMapper, SamplingTask>
        implements SamplingTaskService {

    /** 任务状态：运行中 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态：已停止 */
    private static final String STATUS_STOPPED = "STOPPED";
    /** 任务状态：已完成（采样窗口结束） */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 单次采样语句查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    /** 单次采样最大落库行数（防超大结果集打爆明细表） */
    private static final int MAX_ROWS_PER_SAMPLE = 5000;

    private final SamplingHistoryMapper historyMapper;
    private final DbConnectionConfigService connectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 运行中任务的调度句柄：taskId -> 调度器+连接池 */
    private final Map<String, RunningHandle> runningHandles = new ConcurrentHashMap<>();

    public SamplingTaskServiceImpl(SamplingHistoryMapper historyMapper,
                                   DbConnectionConfigService connectionService) {
        this.historyMapper = historyMapper;
        this.connectionService = connectionService;
    }

    /**
     * 服务重启恢复：上一实例遗留的 RUNNING 任务没有调度线程，统一置 STOPPED，
     * 避免状态与实际不符（无孤儿线程原则）。
     */
    @PostConstruct
    public void recoverOrphanRunningTasks() {
        List<SamplingTask> orphans = list(new LambdaQueryWrapper<SamplingTask>()
                .eq(SamplingTask::getStatus, STATUS_RUNNING));
        for (SamplingTask t : orphans) {
            t.setStatus(STATUS_STOPPED);
            updateById(t);
            log.warn("[采样调度] 服务重启，遗留 RUNNING 任务置为 STOPPED: taskId={}, name={}", t.getId(), t.getTaskName());
        }
    }

    @PreDestroy
    public void shutdownAll() {
        runningHandles.keySet().forEach(this::teardown);
    }

    // ==================== 任务 CRUD ====================

    @Override
    public List<SamplingTask> listTasks(String empNo) {
        return list(new LambdaQueryWrapper<SamplingTask>()
                .eq(SamplingTask::getEmpNo, empNo)
                .orderByDesc(SamplingTask::getCreateTime));
    }

    @Override
    public SamplingTask getTask(String empNo, String id) {
        SamplingTask task = getOwned(empNo, id);
        if (task == null) {
            throw new BusinessException(404, "采样任务不存在或无权操作");
        }
        return task;
    }

    @Override
    @Transactional
    public SamplingTask saveTask(String empNo, SamplingTask task) {
        validateSql(task.getCustomSql());
        if (task.getStartTime() == null || task.getEndTime() == null) {
            throw new BusinessException("采样起止时间窗口不能为空");
        }
        if (!task.getEndTime().isAfter(task.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        if (task.getFrequency() == null || task.getFrequency() < 1) {
            throw new BusinessException("采样频率必须大于等于 1 秒");
        }
        task.setEmpNo(empNo);
        if (task.getSamplingType() == null || task.getSamplingType().isBlank()) {
            task.setSamplingType("DEAD_TUPLE");
        }

        if (task.getId() == null || task.getId().isBlank()) {
            // —— 新增 ——
            checkConnectionOwned(empNo, task.getConnectionId());
            probeColumnsOrThrow(empNo, task); // 保存前验证 SQL 可解析，写错列名/语法即时拦截
            task.setId(null); // 交给雪花主键生成
            task.setStatus(STATUS_STOPPED);
            save(task);
            log.info("[采样任务] 新增: id={}, name={}, connectionId={}, empNo={}",
                    task.getId(), task.getTaskName(), task.getConnectionId(), empNo);
        } else {
            // —— 更新 ——
            SamplingTask existing = getOwned(empNo, task.getId());
            if (existing == null) {
                throw new BusinessException(404, "采样任务不存在或无权操作");
            }
            if (STATUS_RUNNING.equals(existing.getStatus())) {
                throw new BusinessException("任务运行中，请先停止再编辑");
            }
            checkConnectionOwned(empNo, task.getConnectionId());
            probeColumnsOrThrow(empNo, task); // 保存前验证 SQL 可解析，写错列名/语法即时拦截
            task.setStatus(existing.getStatus()); // 状态由启动/停止接口驱动，编辑不改
            updateById(task);
            log.info("[采样任务] 更新: id={}, name={}, empNo={}", task.getId(), task.getTaskName(), empNo);
        }
        return task;
    }

    @Override
    @Transactional
    public void deleteTask(String empNo, String id) {
        SamplingTask task = getOwned(empNo, id);
        if (task == null) {
            throw new BusinessException(404, "采样任务不存在或无权操作");
        }
        if (STATUS_RUNNING.equals(task.getStatus())) {
            stopScheduler(id); // 运行中先销毁调度线程与连接池（DB 状态随删行一并清除）
        }
        // sampling_history 已声明 ON DELETE CASCADE，删任务即删明细
        removeById(id);
        log.info("[采样任务] 删除: id={}, name={}, empNo={}", id, task.getTaskName(), empNo);
    }

    // ==================== 生命周期控制 ====================

    @Override
    public SamplingTask startTask(String empNo, String id) {
        SamplingTask task = getOwned(empNo, id);
        if (task == null) {
            throw new BusinessException(404, "采样任务不存在或无权操作");
        }
        if (runningHandles.containsKey(id)) {
            throw new BusinessException("任务已在运行中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(task.getStartTime())) {
            throw new BusinessException("采样窗口尚未开始（开始时间：" + task.getStartTime() + "），到时再启动");
        }
        if (!now.isBefore(task.getEndTime())) {
            throw new BusinessException("采样窗口已结束（当前时间已过结束时间），请调整起止时间");
        }
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务采样窗口已结束，请将起止时间调整后重新启用");
        }

        // 按归属操作者解密连接密码（仅存本方法内存），建立临时连接池
        DbConnectionConfigEntity conn = connectionService.getDecrypted(empNo, task.getConnectionId());
        HikariDataSource ds = buildDataSource(conn);

        task.setStatus(STATUS_RUNNING);
        updateById(task);

        long periodSeconds = task.getFrequency();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sampling-task-" + id);
            t.setDaemon(true);
            return t;
        });
        // 立即首轮采样，其后按频率周期执行；调度和采样都在线程内进行，不阻塞接口响应
        executor.scheduleAtFixedRate(
                () -> sampleOnce(task.getConnectionId(), id, periodSeconds, ds),
                0, periodSeconds, TimeUnit.SECONDS);
        runningHandles.put(id, new RunningHandle(executor, ds));
        log.info("[采样调度] 任务启动: taskId={}, name={}, 频率={}s, alias={}, 操作人={}",
                id, task.getTaskName(), periodSeconds, conn.getAlias(), empNo);
        return task;
    }

    @Override
    public SamplingTask stopTask(String empNo, String id) {
        SamplingTask task = getOwned(empNo, id);
        if (task == null) {
            throw new BusinessException(404, "采样任务不存在或无权操作");
        }
        if (STATUS_COMPLETED.equals(task.getStatus()) && !runningHandles.containsKey(id)) {
            throw new BusinessException("任务已完成，无需停止");
        }
        stopScheduler(id);
        task.setStatus(STATUS_STOPPED);
        updateById(task);
        log.info("[采样调度] 任务停止: taskId={}, name={}, 操作人={}", id, task.getTaskName(), empNo);
        return task;
    }

    // ==================== 采样明细查询 ====================

    @Override
    public List<SamplingHistory> listHistory(String empNo, String taskId,
                                             LocalDateTime start, LocalDateTime end) {
        // 归属校验：仅允许读取自己的任务明细
        getTask(empNo, taskId);
        LambdaQueryWrapper<SamplingHistory> wrapper = new LambdaQueryWrapper<SamplingHistory>()
                .eq(SamplingHistory::getTaskId, taskId)
                .orderByAsc(SamplingHistory::getSamplingTime)
                .orderByAsc(SamplingHistory::getId);
        if (start != null) {
            wrapper.ge(SamplingHistory::getSamplingTime, start);
        }
        if (end != null) {
            wrapper.le(SamplingHistory::getSamplingTime, end);
        }
        return historyMapper.selectList(wrapper);
    }

    // ==================== 列结构预览（SQL 解析画图适配） ====================

    /**
     * 解析采样 SQL 的结果集列结构：优先 prepare 阶段 {@link PreparedStatement#getMetaData()}
     * （不执行查询）；驱动不支持时回退 {@code SELECT * FROM (sql) t WHERE 1=0} 零行执行。
     * 两种路径都不会返回业务数据，仅用于确定维度列/展示列候选与语法校验。
     */
    @Override
    public List<ColumnDescriptor> previewColumns(String empNo, String connectionId, String customSql) {
        validateSql(customSql);
        DbConnectionConfigEntity conn = connectionService.getDecrypted(empNo, connectionId);
        HikariDataSource ds = buildDataSource(conn);
        try (Connection jdbc = ds.getConnection()) {
            ResultSetMetaData meta = null;
            // 路径①： prepare 阶段拿元数据，不执行（PG/GaussDB 驱动支持）
            try (PreparedStatement ps = jdbc.prepareStatement(customSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                meta = ps.getMetaData();
            } catch (java.sql.SQLException e) {
                log.debug("[列预览] getMetaData 不可用，回退零行执行: {}", e.getMessage());
            }
            // 路径②： 包一层 WHERE 1=0 零行执行，从结果集拿元数据
            if (meta == null || safeColumnCount(meta) == 0) {
                try (Statement stmt = jdbc.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT * FROM (" + customSql + ") _meta_probe WHERE 1 = 0")) {
                    meta = rs.getMetaData();
                }
            }
            if (meta == null || safeColumnCount(meta) == 0) {
                throw new BusinessException("未能解析出结果集列结构，请检查 SQL");
            }
            List<ColumnDescriptor> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String label = stripPrefix(meta.getColumnLabel(i));
                columns.add(new ColumnDescriptor(label, meta.getColumnTypeName(i),
                        isNumeric(meta.getColumnType(i)), isTemporal(meta.getColumnType(i))));
            }
            return columns;
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            log.warn("[列预览] 解析失败 empNo={}, connectionId={}: {}", empNo, connectionId, root.getMessage());
            throw new BusinessException("SQL 解析失败：" + root.getMessage());
        } finally {
            ds.close();
        }
    }

    /** 安全读取列数（meta 为 null 时返回 0，用于分支判断）。 */
    private static int safeColumnCount(ResultSetMetaData meta) {
        try {
            return meta.getColumnCount();
        } catch (Exception e) {
            return 0;
        }
    }

    /** JDBC 类型码是否数值列（整数/浮点/定点）。 */
    private static boolean isNumeric(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> true;
            default -> false;
        };
    }

    /** JDBC 类型码是否时间列。 */
    private static boolean isTemporal(int sqlType) {
        return switch (sqlType) {
            case Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE,
                    Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> true;
            default -> false;
        };
    }

    // ==================== 调度线程内部 ====================

    /**
     * 单次采样：在调度线程内执行自定义 SQL，结果逐行序列化落库。
     * 到达结束时间则自动完成并销毁调度资源；异常只记录日志，不让调度线程中断。
     */
    private void sampleOnce(String connectionId, String taskId, long periodSeconds, HikariDataSource ds) {
        SamplingTask task = getById(taskId);
        if (task == null) {
            // 任务已被删除：停止自身
            teardown(taskId);
            return;
        }
        if (!LocalDateTime.now().isBefore(task.getEndTime())) {
            markCompletedAndTeardown(task);
            return;
        }
        try {
            long samplingTime = System.currentTimeMillis();
            int rowCount = executeAndStore(task.getCustomSql(), taskId, samplingTime, ds);
            log.debug("[采样调度] taskId={} 本轮落库 {} 行", taskId, rowCount);
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            log.warn("[采样调度] taskId={} 本轮采样失败: {}", taskId, root.getMessage());
            // 连接池已不可用时停止任务，避免无效高频重试刷屏
            if (e instanceof java.sql.SQLTransientConnectionException
                    || (root instanceof java.sql.SQLTransientConnectionException)) {
                markStoppedAndTeardown(taskId);
            }
        }
    }

    /** 执行采样 SQL 并将每行结果原样序列化为 JSON 落库，返回落库行数。 */
    private int executeAndStore(String sql, String taskId, long epochMillis, DataSource ds) throws Exception {
        LocalDateTime samplingTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault());
        List<SamplingHistory> batch = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setMaxRows(MAX_ROWS_PER_SAMPLE);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next() && batch.size() < MAX_ROWS_PER_SAMPLE) {
                    // 列名 → 原始值，忠实平铺一行数据，不做任何业务加工
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(stripPrefix(meta.getColumnLabel(i)), rs.getObject(i));
                    }
                    SamplingHistory history = new SamplingHistory();
                    history.setTaskId(taskId);
                    history.setSamplingTime(samplingTime);
                    history.setRowData(objectMapper.writeValueAsString(row));
                    batch.add(history);
                }
            }
        }
        for (SamplingHistory h : batch) {
            historyMapper.insert(h);
        }
        return batch.size();
    }

    /** 采样窗口到期：任务置 COMPLETED 并销毁调度资源。 */
    private void markCompletedAndTeardown(SamplingTask task) {
        task.setStatus(STATUS_COMPLETED);
        updateById(task);
        teardown(task.getId());
        log.info("[采样调度] 采样窗口结束，任务自动完成: taskId={}, name={}", task.getId(), task.getTaskName());
    }

    /** 连接持续异常：停止调度并置 STOPPED，避免无效高频重试。 */
    private void markStoppedAndTeardown(String taskId) {
        SamplingTask task = getById(taskId);
        if (task != null) {
            task.setStatus(STATUS_STOPPED);
            updateById(task);
        }
        teardown(taskId);
        log.warn("[采样调度] 连接不可用，任务已停止: taskId={}", taskId);
    }

    // ==================== 私有工具 ====================

    /** 停止调度线程并关闭临时连接池。 */
    private void stopScheduler(String taskId) {
        RunningHandle handle = runningHandles.remove(taskId);
        if (handle == null) {
            return;
        }
        handle.executor.shutdownNow();
        handle.dataSource.close();
    }

    /** 销毁调度资源（不操作状态表，供删除/停止路径复用）。 */
    private void teardown(String taskId) {
        stopScheduler(taskId);
    }

    /** 依据连接配置构建临时只读用途连接池（与数据库性能分析模块同规则，池大小 1）。 */
    private HikariDataSource buildDataSource(DbConnectionConfigEntity conn) {
        DbConnectionConfig cfg = new DbConnectionConfig(
                conn.getAlias(), conn.getDialect(), conn.getHost(),
                conn.getPort() == null ? 5432 : conn.getPort(),
                conn.getDbName(), conn.getDbUser(), conn.getDbPass());
        String jdbcUrl = buildJdbcUrl(cfg);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(cfg.user());
        config.setPassword(cfg.password());
        // 采样为串行单连接，最小化对目标库的压力
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(8000);
        config.setPoolName("sampling-" + Integer.toHexString(jdbcUrl.hashCode()));
        return new HikariDataSource(config);
    }

    private static String buildJdbcUrl(DbConnectionConfig cfg) {
        String d = cfg.dialect() == null ? "MYSQL" : cfg.dialect().toUpperCase(Locale.ROOT);
        return switch (d) {
            case "GAUSSDB", "OPENGAUSS", "POSTGRESQL" ->
                    "jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.dbName();
            default -> "jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.dbName()
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        };
    }

    /** 保存前置校验：借列预览验证 SQL 在目标库可解析（不执行查询，失败直接中断保存）。 */
    private void probeColumnsOrThrow(String empNo, SamplingTask task) {
        try {
            previewColumns(empNo, task.getConnectionId(), task.getCustomSql());
        } catch (BusinessException e) {
            throw new BusinessException("采样 SQL 校验未通过：" + e.getMessage());
        }
    }

    /** 采样 SQL 安全卡口：仅允许 SELECT / WITH 开头（定时任务绝不允许触发写操作）。 */
    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("自定义采样 SQL 不能为空");
        }
        String normalized = sql.strip().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new BusinessException("采样 SQL 仅允许 SELECT / WITH 开头的只读查询");
        }
        if (normalized.contains(";")) {
            // 去掉末尾分号后仍含分号即视为多语句，拒绝
            String withoutTail = normalized.substring(0, normalized.lastIndexOf(';'));
            if (withoutTail.contains(";")) {
                throw new BusinessException("采样 SQL 不允许多语句");
            }
        }
    }

    /** PostgreSQL 驱动对表达式列可能返回 "table.column" 形式的列标签，取末段列名。 */
    private String stripPrefix(String columnLabel) {
        int dot = columnLabel.lastIndexOf('.');
        return dot >= 0 ? columnLabel.substring(dot + 1) : columnLabel;
    }

    private SamplingTask getOwned(String empNo, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<SamplingTask>()
                .eq(SamplingTask::getId, id)
                .eq(SamplingTask::getEmpNo, empNo));
    }

    /** 校验连接存在且归属当前操作者（复用 db_connection_config 的隔离规则）。 */
    private void checkConnectionOwned(String empNo, String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new BusinessException("请选择数据库连接");
        }
        // 不存在或越权时抛 404
        connectionService.getDecrypted(empNo, connectionId);
    }

    /** 运行中任务的调度句柄。 */
    private record RunningHandle(ScheduledExecutorService executor, HikariDataSource dataSource) {
    }
}

