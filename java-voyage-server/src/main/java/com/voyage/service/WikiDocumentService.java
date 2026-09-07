package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.WikiDocument;
import com.voyage.model.SortOrderDTO;
import com.voyage.model.WikiNodeVO;

import java.util.List;

/**
 * Wiki 文档 Service 接口。
 * <p>
 * 完全按人隔离：所有方法均显式携带归属员工号 {@code empNo}，
 * 由调用方（Controller）从 {@code EmpContext} 注入，禁止前端指定。
 * </p>
 */
public interface WikiDocumentService extends IService<WikiDocument> {

    /**
     * 获取指定员工号下的完整树形目录。
     */
    List<WikiNodeVO> buildWikiTree(String empNo);

    /**
     * 根据 ID 获取本人名下文档详情（他人文档返回 404）。
     */
    WikiDocument getById(String empNo, String id);

    /**
     * 根据标题获取本人名下文档（用于双链跳转，仅限个人知识库）。
     */
    WikiDocument getByTitle(String empNo, String title);

    /**
     * 保存或更新本人名下文档（新建自动注入归属；更新前校验归属；标题按人唯一校验）。
     */
    WikiDocument saveDoc(String empNo, WikiDocument doc);

    /**
     * 删除本人名下文档（若为文件夹则级联删除本人名下所有子节点）。
     */
    void deleteById(String empNo, String id);

    /**
     * 移动本人名下节点到指定父节点和排序位置（目标父节点必须归属本人）。
     */
    void moveNode(String empNo, String id, String newParentId, Integer newSortOrder);

    /**
     * 批量更新本人名下同级节点的 sort_order。
     */
    void batchUpdateSortOrder(String empNo, List<SortOrderDTO.SortItem> items);

    /**
     * 获取本人名下指定文件夹的直接子节点（不递归）。
     */
    List<WikiNodeVO> getChildrenById(String empNo, String folderId);
}
