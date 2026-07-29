package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.WikiDocument;
import com.voyage.model.SortOrderDTO;
import com.voyage.model.WikiNodeVO;

import java.util.List;

/**
 * Wiki 文档 Service 接口。
 */
public interface WikiDocumentService extends IService<WikiDocument> {

    /**
     * 获取完整树形目录。
     */
    List<WikiNodeVO> buildWikiTree();

    /**
     * 根据 ID 获取文档详情。
     */
    WikiDocument getById(String id);

    /**
     * 根据标题获取文档（用于双链跳转）。
     */
    WikiDocument getByTitle(String title);

    /**
     * 保存或更新文档（含标题唯一性校验）。
     */
    WikiDocument saveDoc(WikiDocument doc);

    /**
     * 删除文档（若为文件夹则级联删除所有子节点）。
     */
    void deleteById(String id);

    /**
     * 移动节点到指定父节点和排序位置。
     */
    void moveNode(String id, String newParentId, Integer newSortOrder);

    /**
     * 批量更新同级节点的 sort_order。
     */
    void batchUpdateSortOrder(List<SortOrderDTO.SortItem> items);

    /**
     * 获取指定文件夹的直接子节点（不递归）。
     */
    List<WikiNodeVO> getChildrenById(String folderId);
}
