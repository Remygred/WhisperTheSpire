package whispers.thespire.llm.model;

import java.util.List;

public class LLMResult {
    public boolean ok;
    public String errorMessage;
    public String raw;
    public String contextType;
    public String summary;
    public List<LLMRecommendation> recommendations;

    public static LLMResult success(String contextType, String summary, List<LLMRecommendation> recs) {
        LLMResult result = new LLMResult();
        result.ok = true;
        result.contextType = contextType;
        result.summary = summary;
        result.recommendations = recs;
        return result;
    }

    public static LLMResult failure(String message, String raw) {
        LLMResult result = new LLMResult();
        result.ok = false;
        result.errorMessage = message;
        result.raw = raw;
        return result;
    }
}
