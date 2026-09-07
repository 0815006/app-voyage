package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.WorkspaceSubDir;
import com.voyage.mapper.WorkspaceSubDirMapper;
import com.voyage.service.WorkspaceSubDirService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 文件分析工作区（物理隔离子目录）Service 实现。
 * <p>
 * 物理边界：workspace.root-path 下每行一个 UUID 子目录，目录名即主键 subDirId，
 * 所有文件读写/解析/渲染均以该目录为沙箱，路径穿越由文件层 resolveSafePath 阻断。
 */
@Slf4j
@Service
public class WorkspaceSubDirServiceImpl
        extends ServiceImpl<WorkspaceSubDirMapper, WorkspaceSubDir>
        implements WorkspaceSubDirService {

    private static final DateTimeFormatter NAME_TS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    @Value("${workspace.root-path}")
    private String rootPath;

    @Override
    public List<WorkspaceSubDir> listByEmpNo(String empNo) {
        return list(new LambdaQueryWrapper<WorkspaceSubDir>()
                .eq(WorkspaceSubDir::getEmpNo, empNo)
                .orderByDesc(WorkspaceSubDir::getUpdateTime));
    }

    @Override
    public WorkspaceSubDir getById(String subDirId) {
        WorkspaceSubDir ws = super.getById(subDirId);
        if (ws == null) {
            throw new BusinessException(404, "工作区不存在");
        }
        return ws;
    }

    @Override
    @Transactional
    public WorkspaceSubDir create(String empNo, String dirName) {
        // 1. 生成 UUID 主键（同时作为物理子目录名）
        String subDirId = UUID.randomUUID().toString();

        // 2. 物理创建隔离子目录
        Path subDirPath = Path.of(rootPath, subDirId).toAbsolutePath().normalize();
        try {
            Files.createDirectories(subDirPath);
        } catch (IOException e) {
            log.error("创建工作区物理目录失败: {}", subDirPath, e);
            throw new BusinessException("创建工作区目录失败: " + e.getMessage());
        }

        // 3. 落库元数据
        WorkspaceSubDir ws = new WorkspaceSubDir();
        ws.setSubDirId(subDirId);
        ws.setEmpNo(empNo);
        ws.setDirName(dirName == null || dirName.isBlank()
                ? "工作区 " + LocalDateTime.now().format(NAME_TS) : dirName.trim());
        ws.setAbsPath(subDirPath.toString());
        save(ws);
        log.info("创建文件分析工作区: id={}, empNo={}, dirName={}, absPath={}",
                subDirId, empNo, ws.getDirName(), ws.getAbsPath());
        return getById(subDirId);
    }

    @Override
    @Transactional
    public void delete(String empNo, String subDirId) {
        WorkspaceSubDir ws = getById(subDirId);
        if (!ws.getEmpNo().equals(empNo)) {
            throw new BusinessException(403, "无权删除他人工作区");
        }
        // 1. 物理递归删除子目录（尽力而为，不因失败阻断级联清理）
        try {
            deleteDirectory(Path.of(ws.getAbsPath()));
        } catch (IOException e) {
            log.warn("删除工作区物理目录失败: {} -> {}", ws.getAbsPath(), e.getMessage());
        }
        // 2. 数据库级联清理（file/session/vector 均 ON DELETE CASCADE）
        removeById(subDirId);
        log.info("删除文件分析工作区: id={}, empNo={}", subDirId, empNo);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("删除路径失败: {} -> {}", p, e.getMessage());
                }
            });
        }
    }
}
