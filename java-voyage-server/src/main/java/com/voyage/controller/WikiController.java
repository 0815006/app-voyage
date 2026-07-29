package com.voyage.controller;

import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.WikiDocument;
import com.voyage.model.MoveNodeDTO;
import com.voyage.model.SortOrderDTO;
import com.voyage.model.WikiNodeVO;
import com.voyage.service.WikiDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Wiki 管理 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiDocumentService wikiDocumentService;

    /**
     * 获取完整目录树。
     */
    @GetMapping("/tree")
    public Result<List<WikiNodeVO>> getTree() {
        log.info("[{}] 请求 Wiki 目录树", EmpContext.getEmpNo());
        return Result.ok(wikiDocumentService.buildWikiTree());
    }

    /**
     * 根据 ID 获取文档详情。
     */
    @GetMapping("/{id}")
    public Result<WikiDocument> getById(@PathVariable String id) {
        log.info("[{}] 请求 Wiki 文档详情: id={}", EmpContext.getEmpNo(), id);
        return Result.ok(wikiDocumentService.getById(id));
    }

    /**
     * 根据标题获取文档（双链跳转专用）。
     */
    @GetMapping("/by-title")
    public Result<WikiDocument> getByTitle(@RequestParam String title) {
        log.info("[{}] 双链跳转查询: title={}", EmpContext.getEmpNo(), title);
        return Result.ok(wikiDocumentService.getByTitle(title));
    }

    /**
     * 创建或更新文档。
     */
    @PostMapping("/save")
    public Result<WikiDocument> save(@RequestBody WikiDocument doc) {
        log.info("[{}] 保存 Wiki 文档: title={}, id={}", EmpContext.getEmpNo(), doc.getTitle(), doc.getId());
        return Result.ok(wikiDocumentService.saveDoc(doc));
    }

    /**
     * 删除文档及子节点。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        log.info("[{}] 删除 Wiki 节点: id={}", EmpContext.getEmpNo(), id);
        wikiDocumentService.deleteById(id);
        return Result.ok();
    }

    /**
     * 移动节点到指定父节点和排序位置。
     */
    @PutMapping("/{id}/move")
    public Result<Void> moveNode(@PathVariable String id, @RequestBody MoveNodeDTO dto) {
        log.info("[{}] 移动 Wiki 节点: id={} -> parentId={}, sortOrder={}",
                EmpContext.getEmpNo(), id, dto.getNewParentId(), dto.getNewSortOrder());
        wikiDocumentService.moveNode(id, dto.getNewParentId(), dto.getNewSortOrder());
        return Result.ok();
    }

    /**
     * 批量更新同级节点排序。
     */
    @PutMapping("/sort-batch")
    public Result<Void> batchUpdateSortOrder(@RequestBody SortOrderDTO dto) {
        log.info("[{}] 批量更新 Wiki 排序: {} 个节点", EmpContext.getEmpNo(), dto.getItems().size());
        wikiDocumentService.batchUpdateSortOrder(dto.getItems());
        return Result.ok();
    }

    /**
     * 获取指定文件夹的直接子节点（不递归）。
     */
    @GetMapping("/{folderId}/children")
    public Result<List<WikiNodeVO>> getChildren(@PathVariable String folderId) {
        log.info("[{}] 获取文件夹子节点: folderId={}", EmpContext.getEmpNo(), folderId);
        return Result.ok(wikiDocumentService.getChildrenById(folderId));
    }
}
