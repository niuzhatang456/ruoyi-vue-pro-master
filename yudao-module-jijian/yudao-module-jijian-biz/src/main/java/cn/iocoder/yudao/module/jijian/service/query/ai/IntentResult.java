package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

/**
 * Intent parsing result, carrying both the parsed intent and whether DeepSeek
 * actually produced it.
 *
 * <p>aiGenerated=true  → DeepSeek HTTP call succeeded and JSON validated
 * <p>aiGenerated=false → local rule-based fallback was used (key absent, HTTP
 *                        non-2xx, JSON parse error, or validation failure)
 *
 * The final aiMode is computed in JijianQueryChatServiceImpl:
 * <ul>
 *   <li>!intentAiGenerated                          → LOCAL_FALLBACK</li>
 *   <li>intentAiGenerated &amp;&amp; summaryAiGenerated  → DEEPSEEK_SUMMARY</li>
 *   <li>intentAiGenerated &amp;&amp; !summaryAiGenerated → DEEPSEEK_INTENT</li>
 * </ul>
 */
public class IntentResult {

    private final JijianAiQueryIntent intent;

    /** True only when DeepSeek HTTP call succeeded and the response passed white-list validation. */
    private final boolean aiGenerated;

    public IntentResult(JijianAiQueryIntent intent, boolean aiGenerated) {
        this.intent = intent;
        this.aiGenerated = aiGenerated;
    }

    public JijianAiQueryIntent getIntent() {
        return intent;
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }
}
