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
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化知识库...");

        // 检查是否已有知识
        if (knowledgeBaseService.count() > 0) {
            log.info("知识库已有数据，跳过初始化");
            return;
        }

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

        // 同步到向量数据库
        try {
            knowledgeBaseService.syncToVectorStore();
            log.info("知识库已同步到向量数据库");
        } catch (Exception e) {
            log.warn("同步到向量数据库失败: {}", e.getMessage());
        }
    }

    /**
     * 获取初始知识库
     */
    private List<KnowledgeBase> getInitialKnowledge() {
        return Arrays.asList(
                // 购票类
                createKnowledge("booking", "如何购买火车票",
                        "您可以通过以下方式购买火车票：\n1. 网上购票：登录12306官网或手机APP\n2. 电话购票：拨打12306客服热线\n3. 代售点购票：前往火车站或代售点\n\n网上购票需注册账号并完成身份核验，每位用户每次最多购买5张车票。",
                        "购票,买票,如何买,网上订票,APP购票"),

                createKnowledge("booking", "如何添加乘车人",
                        "添加乘车人步骤：\n1. 登录12306账号\n2. 进入“我的”页面\n3. 点击“乘客信息”\n4. 点击“添加乘客”\n5. 填写乘客信息（姓名、身份证号、手机号）\n6. 提交后等待核验通过\n\n每位用户最多可添加20位常用联系人。",
                        "添加乘客,新增乘车人,常用联系人,添加同行人"),

                createKnowledge("booking", "学生票如何购买",
                        "购买学生票条件：\n1. 在读学生（限学校和家庭所在地之间）\n2. 已办理学生优惠卡\n3. 优惠区间与购票区间一致\n\n学生票可在寒暑假期间购买硬座、硬卧和高铁二等座，享7.5折优惠。取票时需凭学生证和身份证。",
                        "学生票,学生优惠,学生证,打折"),

                // 改签类
                createKnowledge("refund", "如何改签火车票",
                        "改签规则：\n1. 开车前48小时以上可改签任意车次\n2. 开车前48小时内只可改签至当日的其他车次\n3. 开车后可在12306 APP办理当日改签\n4. 改签费：开车前48小时以上免费，48小时内收取5%-15%手续费\n\n同一订单只能改签一次。",
                        "改签,改签费,改签规则,如何改签,更改车次"),

                createKnowledge("refund", "改签可以改目的地吗",
                        "改签不支持更改出发地和目的地，只能更改出发日期或车次。\n\n如需更改目的地，建议：\n1. 办理退票\n2. 重新购买新车票\n\n退票可能收取一定手续费，请注意。",
                        "改目的地,更改到站,改签目的地"),

                // 退票类
                createKnowledge("refund", "如何退票",
                        "退票规则：\n1. 开车前15天以上：免费退票\n2. 开车前48小时以上：收取票价5%手续费\n3. 开车前24-48小时：收取票价10%手续费\n4. 开车前24小时内：收取票价20%手续费\n5. 开车后：仅能改签当日其他车次，不办理退票\n\n退票可通过12306 APP、官网或车站窗口办理。",
                        "退票,退票规则,如何退票,退款"),

                createKnowledge("refund", "退票多久到账",
                        "退票退款到账时间：\n1. 使用支付宝/微信支付：1-7个工作日\n2. 使用银行卡支付：3-15个工作日\n3. 使用积分支付：退积分，立即到账\n\n如超过时间未到账，请拨打12306客服查询。",
                        "退款到账,退款时间,多久到账,退款查询"),

                // 查询类
                createKnowledge("query", "如何查询余票",
                        "查询余票方法：\n1. 打开12306 APP或官网\n2. 输入出发地、目的地、出发日期\n3. 点击查询\n4. 查看各车次余票情况\n\n余票显示：\n- 有车票：显示具体数量\n- 候补：可提交候补订单\n- 无票：该座位类型已售完",
                        "余票查询,查票,怎么看余票,有没有票"),

                createKnowledge("query", "候补购票是什么",
                        "候补购票是指当所需车次无票时，可以提交候补订单。\n\n规则：\n1. 预付票款，系统自动匹配\n2. 候补成功率高，支持最多3个候补订单\n3. 候补截止时间：开车前兑现\n4. 候补不成功全额退款\n\n候补是官方推荐的购票方式。",
                        "候补,候补购票,候补订单,候补成功"),

                createKnowledge("query", "如何查看订单",
                        "查看订单步骤：\n1. 登录12306账号\n2. 点击“我的”\n3. 点击“我的订单”\n4. 可查看：待支付、待出行、已完成、候补订单\n\n订单详情包含：车次信息、乘客信息、取票号等。",
                        "查看订单,我的订单,订单查询,订单记录"),

                // 常见问题类
                createKnowledge("common", "身份证丢失如何乘车",
                        "身份证丢失乘车方法：\n1. 临时身份证明：可到车站公安制证口办理临时身份证\n2. 电子临时身份证明：12306 APP可申请电子临时身份证明\n3. 其他有效证件：护照、户口本等\n\n建议提前办理临时证件，以免耽误行程。",
                        "身份证丢了,没带身份证,临时身份证明,证件丢失"),

                createKnowledge("common", "取票需要什么",
                        "取票方式：\n1. 刷身份证进站：无需取票，直接刷身份证即可\n2. 换取纸质票：可到车站自助机或窗口取票\n\n学生票、取报销凭证等特殊情况需要取票。",
                        "取票,怎么取票,取票凭证,纸质票"),

                createKnowledge("common", "什么是电子客票",
                        "电子客票是指购票后无需换取纸质车票，直接凭身份证即可进站乘车。\n\n优点：\n1. 免去取票环节，省时省力\n2. 避免丢失车票的风险\n3. 环保便捷\n\n如需报销，可在30日内到车站窗口或自助机换取报销凭证。",
                        "电子客票,电子票,无纸化,刷证乘车"),

                createKnowledge("common", "客服电话是多少",
                        "12306铁路客服热线：\n- 人工服务时间：6:00-23:00\n- 自动语音服务：24小时\n\n也可以通过12306 APP在线咨询客服。",
                        "客服电话,人工客服,咨询电话,联系客服")
        );
    }

    private KnowledgeBase createKnowledge(String category, String question, String answer, String keywords) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setCategory(category);
        kb.setQuestion(question);
        kb.setAnswer(answer);
        kb.setKeywords(keywords);
        kb.setStatus(1);
        kb.setHitCount(0);
        return kb;
    }
}
