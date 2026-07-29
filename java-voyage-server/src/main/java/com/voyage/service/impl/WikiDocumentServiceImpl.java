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
 * Wiki 核心业务 Service 实现。
 */
@Slf4j
@Service
public class WikiDocumentServiceImpl extends ServiceImpl<WikiDocumentMapper, WikiDocument> implements WikiDocumentService {

    @Override
    public List<WikiNodeVO> buildWikiTree() {
        List<WikiDocument> allDocs = list(
                new LambdaQueryWrapper<WikiDocument>().orderByAsc(WikiDocument::getSortOrder)
        );
        return allDocs.stream()
                .filter(doc -> "0".equals(doc.getParentId()))
                .map(doc -> convertToVO(doc, allDocs))
                .toList();
    }

    /**
     * 递归转换平铺记录为树形 VO。
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
    public WikiDocument getById(String id) {
        WikiDocument doc = super.getById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return doc;
    }

    @Override
    public WikiDocument getByTitle(String title) {
        WikiDocument doc = getOne(
                new LambdaQueryWrapper<WikiDocument>().eq(WikiDocument::getTitle, title)
        );
        if (doc == null) {
            throw new BusinessException(404, "未找到标题为「" + title + "」的文档");
        }
        return doc;
    }

    @Override
    @Transactional
    public WikiDocument saveDoc(WikiDocument doc) {
        // 标题唯一性校验
        LambdaQueryWrapper<WikiDocument> wrapper = new LambdaQueryWrapper<WikiDocument>()
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
    public void deleteById(String id) {
        WikiDocument doc = super.getById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        // 收集所有需要删除的子节点 ID
        List<String> idsToDelete = new ArrayList<>();
        idsToDelete.add(id);
        collectChildrenIds(id, idsToDelete);
        removeBatchByIds(idsToDelete);
        log.info("删除 Wiki 节点 {} 及其子节点共 {} 个", id, idsToDelete.size());
    }

    /**
     * 递归收集所有子孙节点 ID。
     */
    private void collectChildrenIds(String parentId, List<String> collector) {
        List<WikiDocument> children = list(
                new LambdaQueryWrapper<WikiDocument>().eq(WikiDocument::getParentId, parentId)
        );
        for (WikiDocument child : children) {
            collector.add(child.getId());
            collectChildrenIds(child.getId(), collector);
        }
    }

    @Override
    @Transactional
    public void moveNode(String id, String newParentId, Integer newSortOrder) {
        WikiDocument doc = super.getById(id);
        if (doc == null) {
            throw new BusinessException(404, "节点不存在");
        }
        // 防止循环引用：目标父节点不能是自己的子孙节点
        if (!"0".equals(newParentId)) {
            List<String> descendantIds = new ArrayList<>();
            collectChildrenIds(id, descendantIds);
            if (descendantIds.contains(newParentId) || newParentId.equals(id)) {
                throw new BusinessException("不能将节点移动到自身或自身的子孙节点下");
            }
            // 校验目标父节点存在且为文件夹
            WikiDocument targetParent = super.getById(newParentId);
            if (targetParent == null || targetParent.getType() != 1) {
                throw new BusinessException("目标父节点不存在或不是文件夹");
            }
        }
        // 如果父节点变了，需要调整原父节点下的同级排序
        if (!newParentId.equals(doc.getParentId())) {
            reorderSiblings(doc.getParentId());
        }
        // 更新 parentId 和 sortOrder
        doc.setParentId(newParentId);
        doc.setSortOrder(newSortOrder);
        updateById(doc);
        // 重排目标父节点下的所有同级节点
        reorderSiblings(newParentId);
    }

    @Override
    @Transactional
    public void batchUpdateSortOrder(List<SortOrderDTO.SortItem> items) {
        for (SortOrderDTO.SortItem item : items) {
            WikiDocument doc = super.getById(item.getId());
            if (doc != null) {
                doc.setSortOrder(item.getSortOrder());
                updateById(doc);
            }
        }
    }

    /**
     * 重排指定父节点下所有子节点的 sort_order（使用整数间隔法：0, 10, 20, ...）。
     */
    private void reorderSiblings(String parentId) {
        List<WikiDocument> siblings = list(
                new LambdaQueryWrapper<WikiDocument>()
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
    public List<WikiNodeVO> getChildrenById(String folderId) {
        WikiDocument folder = super.getById(folderId);
        if (folder == null || folder.getType() != 1) {
            throw new BusinessException(404, "文件夹不存在");
        }
        List<WikiDocument> children = list(
                new LambdaQueryWrapper<WikiDocument>()
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
}
