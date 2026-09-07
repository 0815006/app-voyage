package com.voyage.service;

import com.voyage.entity.WorkspaceFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件分析工作区文件 Service 接口（轻量化存储：磁盘文件 + 数据库元数据原子同步）。
 */
public interface WorkspaceFileService {

    /**
     * 获取指定工作区（归属当前操作者）的文件树列表（按创建时间倒序）。
     *
     * @param empNo     操作员工号
     * @param subDirId  工作区 ID
     */
    List<WorkspaceFile> listBySubDir(String empNo, String subDirId);

    /**
     * 上传文件到指定工作区：写入物理磁盘 + ai-tool-doc 预解析 Markdown 并落库。
     *
     * @param empNo     操作员工号
     * @param subDirId  工作区 ID
     * @param file      上传文件
     * @return 保存后的文件元数据
     */
    WorkspaceFile upload(String empNo, String subDirId, MultipartFile file);

    /**
     * 预览：读取文件元数据（含 parsedMarkdown / parsedSchema）。pdf 类由前端走 preview 流。
     */
    WorkspaceFile previewMeta(String empNo, String fileId);

    /**
     * 计算预览/下载所需的物理安全路径（阻断 ../ 穿越），供 Controller 读写磁盘流。
     *
     * @param file 文件元数据（含 subDirId + fileName）
     * @return 规范化安全绝对路径
     */
    java.nio.file.Path resolvePhysicalPath(WorkspaceFile file);

    /**
     * 删除文件：原子化「删除 biz_workspace_file 记录 + 删除物理磁盘文件」。
     * 仅允许操作归属当前操作者的工作区文件。
     */
    void deleteFile(String empNo, String fileId);

    /**
     * 同步工作区内 ai-tool-doc 渲染生成的新文件（output/ 或根目录中 afterEpochMillis 之后
     * 出现的可注册文件），注册为 biz_workspace_file 元数据，供文件树展示与下载。
     *
     * @param empNo            操作员工号
     * @param subDirId         工作区 ID
     * @param afterEpochMillis 只同步该时间之后生成的物理文件（毫秒时间戳）
     * @return 本次新注册的文件元数据列表
     */
    List<WorkspaceFile> syncGeneratedFiles(String empNo, String subDirId, long afterEpochMillis);
}
