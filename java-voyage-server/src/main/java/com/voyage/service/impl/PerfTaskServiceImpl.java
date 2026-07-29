package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.*;
import com.voyage.mapper.*;
import com.voyage.service.PerfTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfTaskServiceImpl extends ServiceImpl<PerfTaskMapper, PerfTask> implements PerfTaskService {

    private final PerfTaskTranMapper tranMapper;
    private final PerfTaskBatchMapper batchMapper;
    private final PerfTaskDataMapper dataMapper;
    private final PerfTaskSceneMapper sceneMapper;
    private final PerfTaskSceneDetailMapper sceneDetailMapper;
    private final PerfDataPlanMapper dataPlanMapper;
    private final PerfDataDetailMapper dataDetailMapper;

    @Override
    public List<PerfTask> listTasks(String batchNo, String productId) {
        LambdaQueryWrapper<PerfTask> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(batchNo)) {
            query.eq(PerfTask::getBatchNo, batchNo);
        }
        if (StringUtils.hasText(productId)) {
            query.eq(PerfTask::getProductId, productId);
        }
        query.orderByDesc(PerfTask::getCreateTime);
        return list(query);
    }

    @Override
    public Map<String, Object> getTaskDetail(Long taskId) {
        Map<String, Object> result = new HashMap<>();
        PerfTask task = getById(taskId);
        result.put("task", task);
        if (task != null) {
            result.put("trans", tranMapper.selectList(new LambdaQueryWrapper<PerfTaskTran>().eq(PerfTaskTran::getTaskId, taskId)));
            result.put("batches", batchMapper.selectList(new LambdaQueryWrapper<PerfTaskBatch>().eq(PerfTaskBatch::getTaskId, taskId)));
            result.put("datas", dataDetailMapper.selectList(new LambdaQueryWrapper<PerfDataDetail>().eq(PerfDataDetail::getTaskId, taskId)));
            result.put("scenes", sceneMapper.selectList(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId)));
        }
        return result;
    }

    @Override
    public PerfTask recognizeTaskInfo(String titleText, String timeText, String reqText) {
        PerfTask task = new PerfTask();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 识别标题信息
        if (StringUtils.hasText(titleText)) {
            String[] lines = titleText.split("\\r?\\n");
            if (lines.length > 0) {
                task.setTaskName(lines[0].trim());
            }
            task.setTestTaskNo(getValueByRegex(titleText, "测试任务编号:\\s*(.*)"));
            task.setProdTaskNo(getValueByRegex(titleText, "生产任务编号:\\s*(.*)"));
            task.setProductId(getValueByRegex(titleText, "牵头组件:\\s*(.*)"));
        }

        // 识别时间与人员信息
        if (StringUtils.hasText(timeText)) {
            task.setBatchNo(getValueByRegex(timeText, "批次:\\s*(.*)"));
            task.setTestDept(getValueByRegex(timeText, "测试部门:\\s*(.*)"));
            String startTimeStr = getValueByRegex(timeText, "测试开始时间:\\s*(.*)");
            String endTimeStr = getValueByRegex(timeText, "测试结束时间:\\s*(.*)");
            try {
                if (StringUtils.hasText(startTimeStr)) task.setStartTime(sdf.parse(startTimeStr));
                if (StringUtils.hasText(endTimeStr)) task.setEndTime(sdf.parse(endTimeStr));
            } catch (Exception e) {
                log.error("Parse date error", e);
            }
            task.setPerfManager(getValueByRegex(timeText, "性能测试经理:\\s*([^/\\n\\r]+)"));
            task.setTestArch(getValueByRegex(timeText, "测试架构师:\\s*([^/\\n\\r]+)"));
            task.setProjectManager(getValueByRegex(timeText, "项目经理:\\s*([^/\\n\\r]+)"));
            task.setDevDept(getValueByRegex(timeText, "开发部门:\\s*(.*)"));
        }

        // 识别需求与项目信息
        if (StringUtils.hasText(reqText)) {
            task.setReqNo(getValueByRegex(reqText, "需求编号:\\s*(.*)"));
            task.setProjName(getValueByRegex(reqText, "项目名称:\\s*(.*)"));
            task.setProjNo(getValueByRegex(reqText, "项目编号:\\s*(.*)"));
        }

        return task;
    }

    private String getValueByRegex(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    @Override
    @Transactional
    public void saveOrUpdateTask(PerfTask task) {
        if (task.getId() == null) {
            task.setStatus(10);
            save(task);
        } else {
            updateById(task);
        }
    }

    @Override
    @Transactional
    public void saveOrUpdateTrans(Long taskId, List<PerfTaskTran> trans, PerfTask summary) {
        // 计算选中交易的 TPS 之和
        BigDecimal tpsSum = BigDecimal.ZERO;
        if (trans != null) {
            for (PerfTaskTran tran : trans) {
                if (tran.getIsSelected() != null && tran.getIsSelected() == 1 && tran.getTargetTps() != null) {
                    tpsSum = tpsSum.add(tran.getTargetTps());
                }
            }
        }

        // 更新任务表的汇总字段
        if (summary == null) {
            summary = new PerfTask();
        }
        summary.setId(taskId);
        summary.setSelectedTranTpsSum(tpsSum);
        updateById(summary);

        tranMapper.delete(new LambdaQueryWrapper<PerfTaskTran>().eq(PerfTaskTran::getTaskId, taskId));
        if (trans != null) {
            for (PerfTaskTran tran : trans) {
                tran.setTaskId(taskId);
                tranMapper.insert(tran);
            }
        }
    }

    @Override
    @Transactional
    public void saveOrUpdateBatches(Long taskId, List<PerfTaskBatch> batches, PerfTask summary) {
        // 更新任务表的批量汇总字段
        if (summary != null) {
            summary.setId(taskId);
            updateById(summary);
        }

        batchMapper.delete(new LambdaQueryWrapper<PerfTaskBatch>().eq(PerfTaskBatch::getTaskId, taskId));
        if (batches != null) {
            for (PerfTaskBatch batch : batches) {
                batch.setTaskId(taskId);
                batchMapper.insert(batch);
            }
        }
    }

    @Override
    @Transactional
    public void saveOrUpdateDatas(Long taskId, List<PerfTaskData> datas) {
        dataMapper.delete(new LambdaQueryWrapper<PerfTaskData>().eq(PerfTaskData::getTaskId, taskId));
        if (datas != null) {
            for (PerfTaskData data : datas) {
                data.setTaskId(taskId);
                dataMapper.insert(data);
            }
        }
    }

    @Override
    public PerfDataPlan getDataPlan(Long taskId) {
        return dataPlanMapper.selectOne(new LambdaQueryWrapper<PerfDataPlan>().eq(PerfDataPlan::getTaskId, taskId));
    }

    @Override
    @Transactional
    public void saveDataPlan(PerfDataPlan plan) {
        if (plan.getId() == null) {
            PerfDataPlan exist = getDataPlan(plan.getTaskId());
            if (exist != null) {
                plan.setId(exist.getId());
                dataPlanMapper.updateById(plan);
            } else {
                dataPlanMapper.insert(plan);
            }
        } else {
            dataPlanMapper.updateById(plan);
        }
    }

    @Override
    @Transactional
    public void saveDataDetails(Long taskId, List<PerfDataDetail> details) {
        dataDetailMapper.delete(new LambdaQueryWrapper<PerfDataDetail>().eq(PerfDataDetail::getTaskId, taskId));
        if (details != null) {
            for (PerfDataDetail detail : details) {
                detail.setTaskId(taskId);
                dataDetailMapper.insert(detail);
            }
        }
    }

    @Override
    @Transactional
    public void saveOrUpdateScenes(Long taskId, List<PerfTaskScene> scenes) {
        sceneMapper.delete(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));
        if (scenes != null) {
            for (PerfTaskScene scene : scenes) {
                scene.setTaskId(taskId);
                sceneMapper.insert(scene);
            }
        }
    }

    @Override
    @Transactional
    public void initDefaultScenes(Long taskId) {
        // 1. 删除旧场景及明细
        List<PerfTaskScene> oldScenes = sceneMapper.selectList(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));
        for (PerfTaskScene s : oldScenes) {
            sceneDetailMapper.delete(new LambdaQueryWrapper<PerfTaskSceneDetail>().eq(PerfTaskSceneDetail::getSceneId, s.getId()));
        }
        sceneMapper.delete(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));

        // 2. 获取选中的交易
        List<PerfTaskTran> trans = tranMapper.selectList(new LambdaQueryWrapper<PerfTaskTran>()
                .eq(PerfTaskTran::getTaskId, taskId)
                .eq(PerfTaskTran::getIsSelected, 1));
        if (trans.isEmpty()) {
            return;
        }

        // 3. 定义场景模板
        List<SceneTemplate> templates = new ArrayList<>();
        templates.add(new SceneTemplate(1, "1-单交易基准", new BigDecimal("100"), "探测单交易在低压力下的纯净响应时间，作为后续性能比对的基准数据。", "1并发(VU=1)，每个交易独立运行固定迭代次数（如100次）。", "完成预设迭代次数，成功率100%。"));
        templates.add(new SceneTemplate(2, "2-单负载测试", new BigDecimal("100"), "验证单笔交易在达到生产预估峰值压力时的性能表现。", "按照调研确定的目标TPS，逐步加压至目标值后稳压运行10分钟。", "达到目标TPS且各项指标（RT/成功率）满足SLA。"));
        templates.add(new SceneTemplate(3, "3-混合负载(60%TPS)", new BigDecimal("60"), "模拟生产真实交易分布，验证系统在综合压力下的整体吞吐量与稳定性。", "按照生产交易比例同时发起压力，稳压运行30分钟。", "系统资源充足，各项交易指标平稳达标。"));
        templates.add(new SceneTemplate(3, "3-混合负载(80%TPS)", new BigDecimal("80"), "模拟生产真实交易分布，验证系统在综合压力下的整体吞吐量与稳定性。", "按照生产交易比例同时发起压力，稳压运行30分钟。", "系统资源充足，各项交易指标平稳达标。"));
        templates.add(new SceneTemplate(3, "3-混合负载(100%TPS)", new BigDecimal("100"), "模拟生产真实交易分布，验证系统在综合压力下的整体吞吐量与稳定性。", "按照生产交易比例同时发起压力，稳压运行30分钟。", "系统资源充足，各项交易指标平稳达标。"));
        templates.add(new SceneTemplate(4, "4-稳定性测试", new BigDecimal("80"), "验证系统在长时间持续压力下，是否存在内存泄漏、资源无法回收或性能衰减。", "在混合负载（通常80%-100%压力）下持续运行8-12小时。", "运行时间内无报错、无OOM、性能指标无明显波动。"));
        templates.add(new SceneTemplate(5, "5-极限测试(左值)", new BigDecimal("120"), "探测系统处理能力的上限拐点，识别压力下的首要瓶颈点。", "以混合负载为基础，按比例阶梯式增加压力，直到系统崩溃或RT突变。", "RT超过阈值、成功率下跌或资源使用率(CPU/IO)超过90%。"));
        templates.add(new SceneTemplate(5, "5-极限测试(右值)", new BigDecimal("140"), "探测系统处理能力的上限拐点，识别压力下的首要瓶颈点。", "以混合负载为基础，按比例阶梯式增加压力，直到系统崩溃或RT突变。", "RT超过阈值、成功率下跌或资源使用率(CPU/IO)超过90%。"));

        // 4. 生成场景及明细
        for (SceneTemplate temp : templates) {
            PerfTaskScene scene = new PerfTaskScene();
            scene.setTaskId(taskId);
            scene.setSceneType(temp.type);
            scene.setSceneName(temp.name);
            scene.setTargetTpsRatio(temp.ratio);
            scene.setTestObjective(temp.objective);
            scene.setImplementationMethod(temp.method);
            scene.setEndCondition(temp.condition);
            scene.setIsSelected(1);
            if (temp.type == 4) {
                scene.setGlobalDuration(240); // 4小时
            } else if (temp.type == 3 || temp.type == 5) {
                scene.setGlobalDuration(30); // 30分钟
            } else if (temp.type == 2) {
                scene.setGlobalDuration(10); // 10分钟
            } else {
                scene.setGlobalDuration(0); // 基准等其他场景
            }
            sceneMapper.insert(scene);

            BigDecimal totalTps = BigDecimal.ZERO;
            for (PerfTaskTran tran : trans) {
                PerfTaskSceneDetail detail = calculateDetail(scene, tran);
                detail.setSceneId(scene.getId());
                sceneDetailMapper.insert(detail);
                if (detail.getTargetTps() != null) {
                    totalTps = totalTps.add(detail.getTargetTps());
                }
            }
            scene.setTargetTotalTps(totalTps);
            sceneMapper.updateById(scene);
        }
    }

    private PerfTaskSceneDetail calculateDetail(PerfTaskScene scene, PerfTaskTran tran) {
        PerfTaskSceneDetail detail = new PerfTaskSceneDetail();
        detail.setTranId(tran.getId());
        detail.setTranName(tran.getTranName());
        detail.setTargetRt(tran.getTargetRt());
        detail.setTargetSuccessRate(tran.getTargetSuccessRate());

        BigDecimal ratio = scene.getTargetTpsRatio().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal baseTps = tran.getTargetTps() != null ? tran.getTargetTps() : BigDecimal.ZERO;

        if (scene.getSceneType() == 1) {
            // 基准测试
            detail.setVuCount(1);
            detail.setRampUp(0);
            detail.setIterations(100);
            detail.setTargetTps(null);
        } else {
            // 其他场景
            BigDecimal targetTps = baseTps.multiply(ratio);
            detail.setTargetTps(targetTps);

            // VU = ROUNDUP(TPS * RT * 1.1, 0)
            BigDecimal rt = tran.getTargetRt() != null ? tran.getTargetRt() : new BigDecimal("0.5");
            int vu = targetTps.multiply(rt).multiply(new BigDecimal("1.1")).setScale(0, RoundingMode.CEILING).intValue();
            detail.setVuCount(vu > 0 ? vu : 1);

            // Ramp-up = ROUNDUP(VU / 5, 0)
            detail.setRampUp((int) Math.ceil(detail.getVuCount() / 5.0));

            // Throughput Timer = ROUNDUP(TPS * 60 * 1.1, -1)
            BigDecimal timer = targetTps.multiply(new BigDecimal("66")); // 60 * 1.1
            detail.setThroughputTimer(timer.setScale(-1, RoundingMode.CEILING));
        }
        return detail;
    }

    @Override
    public List<PerfTaskScene> getScenesByTaskId(Long taskId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));
    }

    @Override
    public List<PerfTaskSceneDetail> getSceneDetailsBySceneId(Long sceneId) {
        return sceneDetailMapper.selectList(new LambdaQueryWrapper<PerfTaskSceneDetail>().eq(PerfTaskSceneDetail::getSceneId, sceneId));
    }

    @Override
    @Transactional
    public void updateSceneAndDetails(PerfTaskScene scene, List<PerfTaskSceneDetail> details) {
        if (scene.getId() == null) {
            sceneMapper.insert(scene);
        } else {
            sceneMapper.updateById(scene);
        }

        if (details != null) {
            // 对于明细，我们采取先删后增的方式，因为明细通常没有外部关联
            sceneDetailMapper.delete(new LambdaQueryWrapper<PerfTaskSceneDetail>().eq(PerfTaskSceneDetail::getSceneId, scene.getId()));
            for (PerfTaskSceneDetail detail : details) {
                detail.setSceneId(scene.getId());
                detail.setId(null);
                sceneDetailMapper.insert(detail);
            }
        }
    }

    @Override
    @Transactional
    public void saveAllScenes(Long taskId, List<SceneDTO> sceneDTOs) {
        // 1. 获取当前数据库中的所有场景ID
        List<PerfTaskScene> currentScenes = sceneMapper.selectList(new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));
        Set<Long> incomingIds = new HashSet<>();
        if (sceneDTOs != null) {
            for (SceneDTO dto : sceneDTOs) {
                if (dto.getScene().getId() != null) {
                    incomingIds.add(dto.getScene().getId());
                }
            }
        }

        // 2. 删除不在传入列表中的场景
        for (PerfTaskScene oldScene : currentScenes) {
            if (!incomingIds.contains(oldScene.getId())) {
                // 删除明细
                sceneDetailMapper.delete(new LambdaQueryWrapper<PerfTaskSceneDetail>().eq(PerfTaskSceneDetail::getSceneId, oldScene.getId()));
                // 删除场景
                sceneMapper.deleteById(oldScene.getId());
            }
        }

        // 3. 保存/更新传入的场景
        if (sceneDTOs != null) {
            for (SceneDTO dto : sceneDTOs) {
                PerfTaskScene scene = dto.getScene();
                scene.setTaskId(taskId);
                updateSceneAndDetails(scene, dto.getDetails());
            }
        }
    }

    private static class SceneTemplate {
        int type;
        String name;
        BigDecimal ratio;
        String objective;
        String method;
        String condition;

        SceneTemplate(int type, String name, BigDecimal ratio, String objective, String method, String condition) {
            this.type = type;
            this.name = name;
            this.ratio = ratio;
            this.objective = objective;
            this.method = method;
            this.condition = condition;
        }
    }
}
