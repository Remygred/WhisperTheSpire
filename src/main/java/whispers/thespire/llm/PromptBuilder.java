package whispers.thespire.llm;

import whispers.thespire.llm.model.LLMRequest;

import java.util.HashMap;
import java.util.Map;

public class PromptBuilder {
    private static final int MAX_SNAPSHOT_CHARS = 7800;

    private PromptBuilder() {}

    public static Prompt build(String contextType, String snapshotJson, String snapshotHash) {
        String safeContext = contextType == null ? "OTHER" : contextType;
        String safeJson = snapshotJson == null ? "{}" : snapshotJson;
        boolean isCombat = "COMBAT".equalsIgnoreCase(safeContext);

        boolean truncated = false;
        if (safeJson.length() > MAX_SNAPSHOT_CHARS) {
            String suffix = "...(truncated)";
            int limit = Math.max(0, MAX_SNAPSHOT_CHARS - suffix.length());
            safeJson = safeJson.substring(0, limit) + suffix;
            truncated = true;
        }

        String system = "You are a decision assistant for Slay the Spire. "
                + "You MUST output only a single JSON object and nothing else. "
                + "Do NOT wrap in markdown. Follow the exact schema and field limits.";
        if (isCombat) {
            system += " For COMBAT: recommendations max 2, action_type must be combat_line, summary<=30, action<=50, reason<=60.";
        }

        StringBuilder user = new StringBuilder();
        user.append("context_type: ").append(safeContext).append("\n");
        user.append("snapshot_hash: ").append(snapshotHash == null ? "" : snapshotHash).append("\n");
        user.append("snapshot_json: ").append(safeJson).append("\n");
        if (truncated) {
            user.append("snapshot_json_truncated: true\n");
        }
        user.append("Output JSON schema:\n");
        user.append("{\n");
        user.append("  \"context_type\": \"MAP|COMBAT|CARD_REWARD|SHOP|EVENT|OTHER\",\n");
        if (isCombat) {
            user.append("  \"summary\": \"<=30 chars\",\n");
        } else {
            user.append("  \"summary\": \"<=40 chars\",\n");
        }
        user.append("  \"recommendations\": [\n");
        user.append("    {\n");
        if (isCombat) {
            user.append("      \"action_type\": \"combat_line\",\n");
            user.append("      \"title\": \"<=12 chars\",\n");
            user.append("      \"action\": \"<=50 chars\",\n");
            user.append("      \"reason\": \"<=60 chars\",\n");
        } else {
            user.append("      \"action_type\": \"path|card_pick|potion|combat_line|shop|general\",\n");
            user.append("      \"title\": \"<=12 chars\",\n");
            user.append("      \"action\": \"<=60 chars\",\n");
            user.append("      \"reason\": \"<=80 chars\",\n");
        }
        user.append("      \"confidence\": 0.0\n");
        user.append("    }\n");
        user.append("  ]\n");
        user.append("}\n");
        if (isCombat) {
            user.append("Rules: recommendations max 2; action_type must be combat_line; all fields required; output JSON only.");
        } else {
            user.append("Rules: recommendations max 3; all fields required; output JSON only.");
        }

        return new Prompt(system, user.toString());
    }

    public static Map<String, Object> buildRequestBody(LLMRequest request) {
        Prompt prompt = build(request.contextType, request.snapshotJson, request.snapshotHash);
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model);
        body.put("temperature", request.temperature);
        body.put("max_tokens", request.maxTokens);
        body.put("messages", prompt.toMessages());
        return body;
    }

    public static class Prompt {
        public final String system;
        public final String user;

        public Prompt(String system, String user) {
            this.system = system;
            this.user = user;
        }

        public Object[] toMessages() {
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", system);
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", user);
            return new Object[]{systemMsg, userMsg};
        }
    }
}
