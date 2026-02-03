package whispers.thespire.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import whispers.thespire.llm.model.LLMRecommendation;
import whispers.thespire.llm.model.LLMResult;
import whispers.thespire.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class ResponseParser {
    private static final Gson GSON = new Gson();

    private ResponseParser() {}

    public static LLMResult parseChatCompletion(String responseBody) {
        try {
            JsonObject root = new JsonParser().parse(responseBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return LLMResult.failure("missing_choices", JsonUtil.truncate(responseBody, 4000));
            }
            JsonObject choice0 = choices.get(0).getAsJsonObject();
            JsonObject message = choice0.getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return LLMResult.failure("missing_content", JsonUtil.truncate(responseBody, 4000));
            }
            String content = message.get("content").getAsString();
            return parseContent(content);
        } catch (Exception e) {
            return LLMResult.failure("parse_failed:" + e.getClass().getSimpleName(), JsonUtil.truncate(responseBody, 4000));
        }
    }

    private static LLMResult parseContent(String content) {
        String cleaned = JsonUtil.stripCodeFences(content);
        String jsonObject = JsonUtil.extractFirstJsonObject(cleaned);
        if (jsonObject == null) {
            return LLMResult.failure("parse_failed:no_json", JsonUtil.truncate(content, 4000));
        }

        try {
            LLMResponse parsed = GSON.fromJson(jsonObject, LLMResponse.class);
            if (parsed == null || parsed.recommendations == null) {
                return LLMResult.failure("parse_failed:missing_fields", JsonUtil.truncate(content, 4000));
            }
            List<LLMRecommendation> recs = new ArrayList<>();
            boolean combat = "COMBAT".equalsIgnoreCase(parsed.context_type);
            for (LLMRecommendation rec : parsed.recommendations) {
                if (rec == null) {
                    continue;
                }
                if (combat) {
                    rec.action_type = "combat_line";
                } else if (rec.action_type == null) {
                    rec.action_type = "general";
                }
                if (rec.title == null) rec.title = "";
                if (rec.action == null) rec.action = "";
                if (rec.reason == null) rec.reason = "";
                if (rec.confidence == null) rec.confidence = 0.5f;
                recs.add(rec);
                if (combat && recs.size() >= 2) {
                    break;
                }
                if (!combat && recs.size() >= 3) {
                    break;
                }
            }
            return LLMResult.success(parsed.context_type, parsed.summary == null ? "" : parsed.summary, recs);
        } catch (Exception e) {
            return LLMResult.failure("parse_failed:" + e.getClass().getSimpleName(), JsonUtil.truncate(content, 4000));
        }
    }

    private static class LLMResponse {
        String context_type;
        String summary;
        List<LLMRecommendation> recommendations;
    }
}
