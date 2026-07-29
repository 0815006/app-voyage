package com.voyage.service;

import com.voyage.entity.MetaEntityFile;

public interface MetaGenEngineService {

    /**
     * 预览生成：同步返回3行文本流，不更新 tracker
     */
    String preview(Long modelId, String operator);

    /**
     * 异步批量生成
     */
    MetaEntityFile generateAsync(Long modelId, Integer rowCount, String batchName, String operator);

    /**
     * 查询生成进度
     */
    MetaEntityFile getStatus(Long taskId);

    /**
     * 获取物理文件路径
     */
    String getFilePath(Long fileId);

    /**
     * 删除生成记录及物理文件
     */
    void deleteFile(Long fileId, String operator);
}
