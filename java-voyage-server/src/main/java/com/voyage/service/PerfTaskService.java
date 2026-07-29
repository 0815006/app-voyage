package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.*;

import java.util.List;
import java.util.Map;

public interface PerfTaskService extends IService<PerfTask> {
    List<PerfTask> listTasks(String batchNo, String productId);

    Map<String, Object> getTaskDetail(Long taskId);

    PerfTask recognizeTaskInfo(String titleText, String timeText, String reqText);

    void saveOrUpdateTask(PerfTask task);

    void saveOrUpdateTrans(Long taskId, List<PerfTaskTran> trans, PerfTask summary);

    void saveOrUpdateBatches(Long taskId, List<PerfTaskBatch> batches, PerfTask summary);

    void saveOrUpdateDatas(Long taskId, List<PerfTaskData> datas);

    void saveOrUpdateScenes(Long taskId, List<PerfTaskScene> scenes);

    void initDefaultScenes(Long taskId);

    List<PerfTaskScene> getScenesByTaskId(Long taskId);

    List<PerfTaskSceneDetail> getSceneDetailsBySceneId(Long sceneId);

    void updateSceneAndDetails(PerfTaskScene scene, List<PerfTaskSceneDetail> details);

    void saveAllScenes(Long taskId, List<SceneDTO> scenes);

    PerfDataPlan getDataPlan(Long taskId);

    void saveDataPlan(PerfDataPlan plan);

    void saveDataDetails(Long taskId, List<PerfDataDetail> details);
}
