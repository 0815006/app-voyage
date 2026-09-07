package com.voyage.service;

import com.voyage.entity.WorkspaceSubDir;

import java.util.List;

/**
 * 文件分析工作区（物理隔离子目录）Service 接口。
 */
public interface WorkspaceSubDirService {

    /**
     * 查询当前操作者的历史工作区（按更新时间倒序）。
     *
     * @param empNo 操作员工号
     */
    List<WorkspaceSubDir> listByEmpNo(String empNo);

    /**
     * 根据 ID 查询工作区，不存在时抛 404；仅校验存在性，不做归属拦截（文件层负责）。
     */
    WorkspaceSubDir getById(String subDirId);

    /**
     * 新建工作区：物理创建 UUID 隔离子目录并落库元数据。
     *
     * @param empNo   操作员工号
     * @param dirName 展示名称，为空时自动生成
     * @return 已保存的工作区
     */
    WorkspaceSubDir create(String empNo, String dirName);

    /**
     * 删除工作区（物理递归删除子目录 + 数据库级联清理），
     * 仅允许删除归属当前操作者的工作区。
     */
    void delete(String empNo, String subDirId);
}
