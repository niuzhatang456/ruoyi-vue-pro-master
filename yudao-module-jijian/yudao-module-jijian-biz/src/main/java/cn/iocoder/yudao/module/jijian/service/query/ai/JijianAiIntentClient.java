package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

/**
 * AI intent-parsing client for the Jijian query module.
 *
 * <p>Safety contract: implementations may only return a controlled
 * {@link JijianAiQueryIntent}. They must never return SQL, table names, column
 * names, or dynamic query fragments.
 */
public interface JijianAiIntentClient {

    String MODE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    String MODE_DEEPSEEK_INTENT = "DEEPSEEK_INTENT";
    String MODE_DEEPSEEK_SUMMARY = "DEEPSEEK_SUMMARY";

    /**
     * Parse the user's natural-language message into a controlled intent.
     */
    IntentResult parseIntent(JijianQueryChatReqVO req);

    /**
     * Generate a Chinese summary from aggregated statistics.
     *
     * @param intent controlled intent
     * @param summaryJson aggregated statistics as JSON, without detail rows or PII
     */
    SummaryResult generateSummary(JijianAiQueryIntent intent, String summaryJson);
}
