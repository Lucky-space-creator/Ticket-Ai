package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.KnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 获取所有启用的知识
     */
    List<KnowledgeBase> getEnabledKnowledge();

    /**
     * 同步知识库到向量数据库
     */
    void syncToVectorStore();

    /**
     * 添加或更新知识并同步到向量库
     */
    KnowledgeBase addOrUpdateKnowledge(String category, String question, String answer, String keywords);

    /**
     * 删除知识并从向量库移除
     */
    boolean deleteKnowledge(Long id);
}
