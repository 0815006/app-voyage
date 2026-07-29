package com.voyage.controller;

import com.voyage.common.Result;
import com.voyage.entity.*;
import com.voyage.service.PerfTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@Slf4j
@RequiredArgsConstructor
public class PerformanceController {

    private final PerfTaskService perfTaskService;

    @GetMapping("/list")
    public Result<List<PerfTask>> listTasks(@RequestParam(required = false) String batchNo,
                                            @RequestParam(required = false) String productId) {
        return Result.ok(perfTaskService.listTasks(batchNo, productId));
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> getTaskDetail(@RequestParam Long taskId) {
        return Result.ok(perfTaskService.getTaskDetail(taskId));
    }

    @PostMapping("/recognize")
    public Result<PerfTask> recognizeTaskInfo(@RequestBody RecognizeRequest request) {
        return Result.ok(perfTaskService.recognizeTaskInfo(request.getTitleText(), request.getTimeText(), request.getReqText()));
    }

    @PostMapping("/saveTask")
    public Result<String> saveTask(@RequestBody PerfTask task) {
        perfTaskService.saveOrUpdateTask(task);
        return Result.ok("保存成功");
    }

    @PostMapping("/saveTrans")
    public Result<String> saveTrans(@RequestParam Long taskId, @RequestBody SaveTransRequest request) {
        perfTaskService.saveOrUpdateTrans(taskId, request.getTrans(), request.getSummary());
        return Result.ok("保存成功");
    }

    @Data
    public static class SaveTransRequest {
        private List<PerfTaskTran> trans;
        private PerfTask summary;
    }

    @PostMapping("/saveBatches")
    public Result<String> saveBatches(@RequestParam Long taskId, @RequestBody SaveBatchesRequest request) {
        perfTaskService.saveOrUpdateBatches(taskId, request.getBatches(), request.getSummary());
        return Result.ok("保存成功");
    }

    @Data
    public static class SaveBatchesRequest {
        private List<PerfTaskBatch> batches;
        private PerfTask summary;
    }

    @PostMapping("/saveDatas")
    public Result<String> saveDatas(@RequestParam Long taskId, @RequestBody List<PerfTaskData> datas) {
        perfTaskService.saveOrUpdateDatas(taskId, datas);
        return Result.ok("保存成功");
    }

    @GetMapping("/initScenes")
    public Result<List<PerfTaskScene>> initScenes(@RequestParam Long taskId) {
        perfTaskService.initDefaultScenes(taskId);
        return Result.ok(perfTaskService.getScenesByTaskId(taskId));
    }

    @GetMapping("/getScenes")
    public Result<List<PerfTaskScene>> getScenes(@RequestParam Long taskId) {
        return Result.ok(perfTaskService.getScenesByTaskId(taskId));
    }

    @GetMapping("/getSceneDetails")
    public Result<List<PerfTaskSceneDetail>> getSceneDetails(@RequestParam Long sceneId) {
        return Result.ok(perfTaskService.getSceneDetailsBySceneId(sceneId));
    }

    @PostMapping("/saveAllScenes")
    public Result<String> saveAllScenes(@RequestParam Long taskId, @RequestBody List<SceneDTO> scenes) {
        perfTaskService.saveAllScenes(taskId, scenes);
        return Result.ok("保存成功");
    }

    @GetMapping("/getDataPlan")
    public Result<PerfDataPlan> getDataPlan(@RequestParam Long taskId) {
        return Result.ok(perfTaskService.getDataPlan(taskId));
    }

    @PostMapping("/saveDataPlan")
    public Result<String> saveDataPlan(@RequestBody PerfDataPlan plan) {
        perfTaskService.saveDataPlan(plan);
        return Result.ok("保存成功");
    }

    @PostMapping("/saveDataDetails")
    public Result<String> saveDataDetails(@RequestParam Long taskId, @RequestBody List<PerfDataDetail> details) {
        perfTaskService.saveDataDetails(taskId, details);
        return Result.ok("保存成功");
    }

    @Data
    public static class RecognizeRequest {
        private String titleText;
        private String timeText;
        private String reqText;
    }
}
