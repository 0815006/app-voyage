package com.voyage.dto;

import com.voyage.entity.*;
import lombok.Data;

import java.util.List;

@Data
public class DocGenerateContext {
    private PerfTask task;
    private List<PerfTaskTran> tranList;
    private List<PerfTaskBatch> batchList;
    private PerfDataPlan dataPlan;
    private List<PerfDataDetail> dataDetailList;
    private List<SceneWithDetails> sceneList;
    private List<PerformanceResourceInfo> resourceList;

    @Data
    public static class SceneWithDetails {
        private PerfTaskScene scene;
        private List<PerfTaskSceneDetail> sceneDetailList;
    }
}
