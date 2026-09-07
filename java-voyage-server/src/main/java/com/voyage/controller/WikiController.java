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
 * <p>
 * 完全按人隔离：所有端点统一从 {@link EmpContext#getEmpNo()} 取当前操作者工号，
 * 传入 Service 作为归属过滤/校验依据，请求体中的归属字段一律被忽略。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiDocumentService wikiDocumentService;

    /**
     * 获取当前操作者名下的完整目录树。
     */
    @GetMapping("/tree")
    public Result<List<WikiNodeVO>> getTree() {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 请求 Wiki 目录树", empNo);
        return Result.ok(wikiDocumentService.buildWikiTree(empNo));
    }

    /**
     * 根据 ID 获取当前操作者名下文档详情。
     */
    @GetMapping("/{id}")
    public Result<WikiDocument> getById(@PathVariable String id) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 请求 Wiki 文档详情: id={}", empNo, id);
        return Result.ok(wikiDocumentService.getById(empNo, id));
    }

    /**
     * 根据标题获取当前操作者名下文档（双链跳转专用）。
     */
    @GetMapping("/by-title")
    public Result<WikiDocument> getByTitle(@RequestParam String title) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 双链跳转查询: title={}", empNo, title);
        return Result.ok(wikiDocumentService.getByTitle(empNo, title));
    }

    /**
     * 创建或更新当前操作者名下文档。
     */
    @PostMapping("/save")
    public Result<WikiDocument> save(@RequestBody WikiDocument doc) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 保存 Wiki 文档: title={}, id={}", empNo, doc.getTitle(), doc.getId());
        return Result.ok(wikiDocumentService.saveDoc(empNo, doc));
    }

    /**
     * 删除当前操作者名下文档及子节点。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 删除 Wiki 节点: id={}", empNo, id);
        wikiDocumentService.deleteById(empNo, id);
        return Result.ok();
    }

    /**
     * 移动当前操作者名下节点到指定父节点和排序位置。
     */
    @PutMapping("/{id}/move")
    public Result<Void> moveNode(@PathVariable String id, @RequestBody MoveNodeDTO dto) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 移动 Wiki 节点: id={} -> parentId={}, sortOrder={}",
                empNo, id, dto.getNewParentId(), dto.getNewSortOrder());
        wikiDocumentService.moveNode(empNo, id, dto.getNewParentId(), dto.getNewSortOrder());
        return Result.ok();
    }

    /**
     * 批量更新当前操作者名下同级节点排序。
     */
    @PutMapping("/sort-batch")
    public Result<Void> batchUpdateSortOrder(@RequestBody SortOrderDTO dto) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 批量更新 Wiki 排序: {} 个节点", empNo, dto.getItems().size());
        wikiDocumentService.batchUpdateSortOrder(empNo, dto.getItems());
        return Result.ok();
    }

    /**
     * 获取当前操作者名下指定文件夹的直接子节点（不递归）。
     */
    @GetMapping("/{folderId}/children")
    public Result<List<WikiNodeVO>> getChildren(@PathVariable String folderId) {
        String empNo = EmpContext.getEmpNo();
        log.info("[{}] 获取文件夹子节点: folderId={}", empNo, folderId);
        return Result.ok(wikiDocumentService.getChildrenById(empNo, folderId));
    }
}
