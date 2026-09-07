package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.WikiDocument;
import com.voyage.mapper.WikiDocumentMapper;
import com.voyage.model.SortOrderDTO;
import com.voyage.model.WikiNodeVO;
import com.voyage.service.WikiDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki 核心业务 Service 实现 —— 完全按人隔离（emp_no 归属）。
 * <p>
 * 所有查询/写操作都以 {@code empNo} 作为硬性过滤与归属校验依据：
 * 用户只能看到与操作本人名下的文档树，越权访问他人文档统一表现为"不存在"（404）。
 * </p>
 */
@Slf4j
@Service
public class WikiDocumentServiceImpl extends ServiceImpl<WikiDocumentMapper, WikiDocument> implements WikiDocumentService {

    @Override
    public List<WikiNodeVO> buildWikiTree(String empNo) {
        List<WikiDocument> allDocs = list(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getEmpNo, empNo)
                        .orderByAsc(WikiDocument::getSortOrder)
        );
        return allDocs.stream()
                .filter(doc -> "0".equals(doc.getParentId()))
                .map(doc -> convertToVO(doc, allDocs))
                .toList();
    }

    /**
     * 递归转换平铺记录为树形 VO（入参 allDocs 已限定归属员工号）。
     */
    private WikiNodeVO convertToVO(WikiDocument doc, List<WikiDocument> allDocs) {
        List<WikiNodeVO> children = allDocs.stream()
                .filter(child -> doc.getId().equals(child.getParentId()))
                .map(child -> convertToVO(child, allDocs))
                .toList();
        return new WikiNodeVO(
                doc.getId(),
                doc.getTitle(),
                doc.getType(),
                doc.getParentId(),
                doc.getUpdateTime(),
                children.isEmpty() ? null : children
        );
    }

    @Override
    public WikiDocument getById(String empNo, String id) {
        WikiDocument doc = getOwned(empNo, id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return doc;
    }

    @Override
    public WikiDocument getByTitle(String empNo, String title) {
        WikiDocument doc = getOne(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getEmpNo, empNo)
                        .eq(WikiDocument::getTitle, title)
        );
        if (doc == null) {
            throw new BusinessException(404, "未找到标题为「" + title + "」的文档");
        }
        return doc;
    }

    @Override
    @Transactional
    public WikiDocument saveDoc(String empNo, WikiDocument doc) {
        String id = doc.getId();
        if (id == null || id.isBlank()) {
            // —— 新建：强制注入归属（忽略请求体可能携带的 empNo）——
            doc.setId(null);
            doc.setEmpNo(empNo);
            doc.setParentId(doc.getParentId() == null ? "0" : doc.getParentId());
        } else {
            // —— 更新：必须先校验归属，防止越权修改他人文档 ——
            WikiDocument existing = getOwned(empNo, id);
            if (existing == null) {
                throw new BusinessException(404, "文档不存在");
            }
            // 保持归属与创建时的结构字段，仅允许更新标题/内容/类型/父级/排序
            doc.setEmpNo(empNo);
            doc.setCreateTime(existing.getCreateTime());
            if (doc.getParentId() == null) {
                doc.setParentId(existing.getParentId());
            }
        }

        // 标题按人唯一性校验
        LambdaQueryWrapper<WikiDocument> wrapper = new LambdaQueryWrapper<WikiDocument>()
                .eq(WikiDocument::getEmpNo, empNo)
                .eq(WikiDocument::getTitle, doc.getTitle());
        WikiDocument existByTitle = getOne(wrapper);
        if (existByTitle != null && !existByTitle.getId().equals(doc.getId())) {
            throw new BusinessException("标题「" + doc.getTitle() + "」已存在，请更换标题");
        }

        super.saveOrUpdate(doc);
        // 重新查询返回完整数据（包含自动生成的 id 和时间戳）
        return super.getById(doc.getId());
    }

    @Override
    @Transactional
    public void deleteById(String empNo, String id) {
        WikiDocument doc = getOwned(empNo, id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        // 收集所有需要删除的本人名下子节点 ID
        List<String> idsToDelete = new ArrayList<>();
        idsToDelete.add(id);
        collectChildrenIds(empNo, id, idsToDelete);
        removeBatchByIds(idsToDelete);
        log.info("[{}] 删除 Wiki 节点 {} 及其子节点共 {} 个", empNo, id, idsToDelete.size());
    }

    /**
     * 递归收集指定员工号下的所有子孙节点 ID。
     */
    private void collectChildrenIds(String empNo, String parentId, List<String> collector) {
        List<WikiDocument> children = list(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getEmpNo, empNo)
                        .eq(WikiDocument::getParentId, parentId)
        );
        for (WikiDocument child : children) {
            collector.add(child.getId());
            collectChildrenIds(empNo, child.getId(), collector);
        }
    }

    @Override
    @Transactional
    public void moveNode(String empNo, String id, String newParentId, Integer newSortOrder) {
        WikiDocument doc = getOwned(empNo, id);
        if (doc == null) {
            throw new BusinessException(404, "节点不存在");
        }
        // 防止循环引用：目标父节点不能是自己的子孙节点
        if (!"0".equals(newParentId)) {
            List<String> descendantIds = new ArrayList<>();
            collectChildrenIds(empNo, id, descendantIds);
            if (descendantIds.contains(newParentId) || newParentId.equals(id)) {
                throw new BusinessException("不能将节点移动到自身或自身的子孙节点下");
            }
            // 校验目标父节点存在、归属本人且为文件夹
            WikiDocument targetParent = getOwned(empNo, newParentId);
            if (targetParent == null || targetParent.getType() != 1) {
                throw new BusinessException("目标父节点不存在或不是文件夹");
            }
        }
        // 如果父节点变了，需要调整原父节点下的同级排序
        if (!newParentId.equals(doc.getParentId())) {
            reorderSiblings(empNo, doc.getParentId());
        }
        // 更新 parentId 和 sortOrder
        doc.setParentId(newParentId);
        doc.setSortOrder(newSortOrder);
        updateById(doc);
        // 重排目标父节点下的所有同级节点
        reorderSiblings(empNo, newParentId);
    }

    @Override
    @Transactional
    public void batchUpdateSortOrder(String empNo, List<SortOrderDTO.SortItem> items) {
        for (SortOrderDTO.SortItem item : items) {
            WikiDocument doc = getOwned(empNo, item.getId());
            if (doc != null) {
                doc.setSortOrder(item.getSortOrder());
                updateById(doc);
            }
        }
    }

    /**
     * 重排指定员工号下某父节点的所有子节点 sort_order（使用整数间隔法：0, 10, 20, ...）。
     */
    private void reorderSiblings(String empNo, String parentId) {
        List<WikiDocument> siblings = list(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getEmpNo, empNo)
                        .eq(WikiDocument::getParentId, parentId)
                        .orderByAsc(WikiDocument::getSortOrder)
        );
        for (int i = 0; i < siblings.size(); i++) {
            WikiDocument sib = siblings.get(i);
            sib.setSortOrder(i * 10);
            updateById(sib);
        }
    }

    @Override
    public List<WikiNodeVO> getChildrenById(String empNo, String folderId) {
        WikiDocument folder = getOwned(empNo, folderId);
        if (folder == null || folder.getType() != 1) {
            throw new BusinessException(404, "文件夹不存在");
        }
        List<WikiDocument> children = list(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getEmpNo, empNo)
                        .eq(WikiDocument::getParentId, folderId)
                        .orderByAsc(WikiDocument::getSortOrder)
        );
        return children.stream()
                .map(doc -> new WikiNodeVO(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getType(),
                        doc.getParentId(),
                        doc.getUpdateTime()
                ))
                .toList();
    }

    /**
     * 按归属员工号 + ID 精确查询本人名下的节点（越权查询返回 null）。
     */
    private WikiDocument getOwned(String empNo, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return getOne(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getId, id)
                        .eq(WikiDocument::getEmpNo, empNo)
        );
    }
}
