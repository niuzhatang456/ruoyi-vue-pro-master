package cn.iocoder.yudao.module.jijian.service.query.ai;

/**
 * Summary generation result, carrying both the text and whether the AI produced it.
 *
 * Used by JijianQueryChatServiceImpl to decide the final aiMode:
 *   - parseIntent succeeded via DeepSeek + summaryAiGenerated=true  → DEEPSEEK_SUMMARY
 *   - parseIntent succeeded via DeepSeek + summaryAiGenerated=false → DEEPSEEK_INTENT
 *   - parseIntent used local rules                                   → LOCAL_FALLBACK
 */
public class SummaryResult {

    /** Human-readable summary text shown to the user. */
    private final String text;

    /** True if DeepSeek produced this summary; false if the local template was used. */
    private final boolean aiGenerated;

    public SummaryResult(String text, boolean aiGenerated) {
        this.text = text;
        this.aiGenerated = aiGenerated;
    }

    public String getText() {
        return text;
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }
}
