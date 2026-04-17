package com.ticket.config;

import com.ticket.entity.KnowledgeBase;
import com.ticket.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 知识库初始化器
 * 应用启动时自动初始化基础知识库
 */
@Slf4j
@Component
public class KnowledgeBaseInitializer implements ApplicationRunner {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化知识库...");

        // 检查是否已有知识
        if (knowledgeBaseService.count() > 0) {
            log.info("知识库已有数据，跳过初始化");
        } else {
            // 预置知识库内容
            List<KnowledgeBase> initialKnowledge = getInitialKnowledge();

            for (KnowledgeBase kb : initialKnowledge) {
                try {
                    knowledgeBaseService.addOrUpdateKnowledge(
                            kb.getCategory(),
                            kb.getQuestion(),
                            kb.getAnswer(),
                            kb.getKeywords()
                    );
                } catch (Exception e) {
                    log.warn("初始化知识失败: {}", kb.getQuestion());
                }
            }

            log.info("知识库初始化完成，共加载 {} 条知识", initialKnowledge.size());
        }

        // 每次启动都同步到向量数据库，确保向量库与MySQL数据一致
        try {
            knowledgeBaseService.syncToVectorStore();
            log.info("知识库已同步到向量数据库");
        } catch (Exception e) {
            log.info("向量数据库集合尚未创建，跳过知识库同步（将在文档加载后自动创建）");
        }
    }

    /**
     * 获取初始知识库
     */
    private List<KnowledgeBase> getInitialKnowledge() {
        // 硬编码初始知识库，与 database/init.sql 保持一致
        List<KnowledgeBase> list = new java.util.ArrayList<>();
        
        // 知识1
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setCategory("booking");
        kb1.setQuestion("如何购买火车票？");
        kb1.setAnswer("您可以通过我们的网站或APP购买火车票。选择出发站、到达站和乘车日期后，系统会显示可用的车次，选择合适的车次和席别后，选择乘客并支付即可。");
        kb1.setKeywords("购票,买票,如何购买");
        kb1.setStatus(1);
        list.add(kb1);
        
        // 知识2
        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setCategory("refund");
        kb2.setQuestion("如何退票？");
        kb2.setAnswer("您可以在订单列表中找到需要退票的订单，点击退票按钮即可。退票后，票款将原路返回到您的支付账户。请注意，开车前一定时间退票可能会收取手续费。");
        kb2.setKeywords("退票,退款,如何退");
        kb2.setStatus(1);
        list.add(kb2);
        
        // 知识3
        KnowledgeBase kb3 = new KnowledgeBase();
        kb3.setCategory("query");
        kb3.setQuestion("如何查询订单？");
        kb3.setAnswer("登录后，点击\"我的订单\"即可查看您的所有订单。您可以按订单状态（待支付、已支付、已退票等）进行筛选。");
        kb3.setKeywords("订单,查询,查订单");
        kb3.setStatus(1);
        list.add(kb3);
        
        // 知识4
        KnowledgeBase kb4 = new KnowledgeBase();
        kb4.setCategory("common");
        kb4.setQuestion("可以携带多少行李？");
        kb4.setAnswer("每名旅客免费携带物品重量为：成人20千克，儿童10千克。携带物品的长、宽、高相加不得超过130厘米。超过规定重量或体积的物品需要办理托运。");
        kb4.setKeywords("行李,携带,托运");
        kb4.setStatus(1);
        list.add(kb4);
        
        return list;
    }
}
