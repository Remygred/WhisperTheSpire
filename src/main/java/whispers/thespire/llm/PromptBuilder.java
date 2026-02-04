package whispers.thespire.llm;

import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.config.ModConfig;
import whispers.thespire.knowledge.KnowledgeBase;

import java.util.HashMap;
import java.util.Map;

public class PromptBuilder {
    private static final int MAX_SNAPSHOT_CHARS = 7800;

    private PromptBuilder() {}

    public static Prompt build(LLMRequest request) {
        String safeContext = request == null || request.contextType == null ? "OTHER" : request.contextType;
        String safeJson = request == null || request.snapshotJson == null ? "{}" : request.snapshotJson;
        String snapshotHash = request == null ? null : request.snapshotHash;
        boolean isCombat = "COMBAT".equalsIgnoreCase(safeContext);
        boolean isShop = "SHOP".equalsIgnoreCase(safeContext);
        boolean showReasons = ModConfig.showReasons;
        boolean multi = ModConfig.multiRecommendations;
        int baseMax = multi ? (isCombat ? 2 : 3) : 1;
        int maxRecs = isShop ? 3 : baseMax;

        boolean truncated = false;
        if (safeJson.length() > MAX_SNAPSHOT_CHARS) {
            String suffix = "...(truncated)";
            int limit = Math.max(0, MAX_SNAPSHOT_CHARS - suffix.length());
            safeJson = safeJson.substring(0, limit) + suffix;
            truncated = true;
        }

        String system = "You are a decision assistant for Slay the Spire. "
                + "You MUST output only a single JSON object and nothing else. "
                + "Do NOT wrap in markdown. Follow the exact schema and field limits. "
                + "Be decisive and practical, avoid generic advice. "
                + "Hard rule: if you mention ascension (A#), it MUST match facts.ascension; "
                + "if facts.ascension is missing, do NOT mention ascension.";
        if (isCombat) {
            system += " For COMBAT: recommendations max " + maxRecs + ", action_type must be combat_line, summary<=30, action<=50, reason<=60.";
        }
        if ("zh".equalsIgnoreCase(ModConfig.language)) {
            system += " Use Simplified Chinese for summary/title/action/reason fields.";
        } else {
            system += " Use English for summary/title/action/reason fields.";
        }
        system += " Use in-game localized names from snapshot_json/essential_facts when referring to cards/relics/potions; do not translate names.";

        StringBuilder user = new StringBuilder();
        user.append("context_type: ").append(safeContext).append("\n");
        user.append("snapshot_hash: ").append(snapshotHash == null ? "" : snapshotHash).append("\n");
        user.append("facts: ")
                .append("ascension=").append(value(request == null ? null : request.ascension)).append(", ")
                .append("character=").append(value(request == null ? null : request.character)).append(", ")
                .append("seed=").append(value(request == null ? null : request.seed)).append(", ")
                .append("floor=").append(value(request == null ? null : request.floor)).append(", ")
                .append("hp=").append(value(request == null ? null : request.hp)).append("/")
                .append(value(request == null ? null : request.maxHp)).append(", ")
                .append("gold=").append(value(request == null ? null : request.gold))
                .append("\n");
        if (request != null && request.essentialFacts != null && !request.essentialFacts.trim().isEmpty()) {
            user.append("essential_facts: ").append(request.essentialFacts).append("\n");
        }
        if (request != null && request.mapCurrent != null && !request.mapCurrent.isEmpty()) {
            user.append("map_current: ").append(request.mapCurrent).append("\n");
        }
        if (request != null && request.mapNext != null && !request.mapNext.isEmpty()) {
            user.append("map_next: ").append(request.mapNext).append("\n");
        }
        if (request != null && request.mapFullAvailable != null) {
            user.append("map_full_available: ").append(request.mapFullAvailable).append("\n");
        }
        user.append("show_reasons: ").append(showReasons).append("\n");
        user.append("max_recommendations: ").append(maxRecs).append("\n");
        if (ModConfig.useKnowledgeBase) {
            String notes = KnowledgeBase.getNotes(safeContext);
            if (notes != null && !notes.trim().isEmpty()) {
                user.append("knowledge_notes:\n").append(trimTo(notes, 1200)).append("\n");
                user.append("Use knowledge_notes as general guidance; if it conflicts with snapshot_json, follow snapshot_json.\n");
            }
        }
        user.append("snapshot_json: ").append(safeJson).append("\n");
        if (truncated) {
            user.append("snapshot_json_truncated: true\n");
        }
        user.append("Task guidance:\n");
        user.append(buildGuidance(safeContext));
        user.append("Output JSON schema:\n");
        user.append("{\n");
        user.append("  \"context_type\": \"NEOW|BOSS_RELIC|REST|MAP|COMBAT|CARD_REWARD|SHOP|EVENT|OTHER\",\n");
        if (isCombat) {
            user.append("  \"summary\": \"<=30 chars\",\n");
        } else {
            user.append("  \"summary\": \"<=40 chars\",\n");
        }
        user.append("  \"next_pick_index\": 0,\n");
        user.append("  \"route_plan\": [\"Step 1: ...\", \"Step 2: ...\"],\n");
        user.append("  \"recommendations\": [\n");
        user.append("    {\n");
        if (isCombat) {
            user.append("      \"action_type\": \"combat_line\",\n");
            user.append("      \"title\": \"<=12 chars\",\n");
            user.append("      \"action\": \"<=50 chars\",\n");
            user.append("      \"reason\": \"<=60 chars\",\n");
        } else {
            user.append("      \"action_type\": \"neow|boss_relic|rest|path|card_pick|potion|combat_line|shop|general\",\n");
            user.append("      \"title\": \"<=12 chars\",\n");
            user.append("      \"action\": \"<=60 chars\",\n");
            user.append("      \"reason\": \"<=80 chars\",\n");
        }
        user.append("      \"confidence\": 0.0\n");
        user.append("    }\n");
        user.append("  ]\n");
        user.append("}\n");
        if (isCombat) {
            user.append("Rules: recommendations max ").append(maxRecs)
                    .append("; action_type must be combat_line; all fields required; output JSON only.");
        } else {
            user.append("Rules: recommendations max ").append(maxRecs)
                    .append("; all fields required; output JSON only.");
        }
        user.append(" If show_reasons=false, set reason to empty string \"\".");
        user.append(" For non-MAP contexts set next_pick_index=0 and route_plan=[].");
        if ("MAP".equalsIgnoreCase(safeContext) || "MAP_PATH".equalsIgnoreCase(safeContext)) {
            user.append(" For MAP: next_pick_index is required (1-based index in map_next, left-to-right). ");
            user.append("route_plan should describe the full route plan for this act.");
        }

        return new Prompt(system, user.toString());
    }

    private static String buildGuidance(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Use snapshot_json to infer deck, relics, potions, hp, gold, ascension, seed.\n");
        if ("NEOW".equalsIgnoreCase(context)) {
            sb.append("- NEOW: choose the best Neow blessing from neow.options. Prioritize consistency at higher ascension. ")
              .append("Output which option to pick.\n");
        } else if ("BOSS_RELIC".equalsIgnoreCase(context)) {
            sb.append("- BOSS_RELIC: choose 1 relic from boss_relic.choices (or skip if truly bad). ")
              .append("Be decisive.\n");
        } else if ("REST".equalsIgnoreCase(context)) {
            sb.append("- REST: choose between rest/smith/other campfire options in rest.options. ")
              .append("If choosing smith, pick 1 card from rest.upgrade_options.\n");
        } else if ("CARD_REWARD".equalsIgnoreCase(context)) {
            sb.append("- CARD_REWARD: pick 1 card or SKIP. Consider deck balance, relic synergies, and curve. ")
              .append("If all weak, recommend skip.\n");
        } else if ("MAP".equalsIgnoreCase(context) || "MAP_PATH".equalsIgnoreCase(context)) {
            sb.append("- MAP: choose the next node from map.next_nodes based on hp/gold/relics/deck. ")
              .append("If low hp, prefer rest or safer path. Prefer describing nodes by room type, not coordinates.\n");
        } else if ("SHOP".equalsIgnoreCase(context)) {
            sb.append("- SHOP: recommend what to buy (cards/relics/potions) and whether to remove a card (purge). ")
              .append("If purging, name a card from shop.purge_candidates.\n");
        } else if ("COMBAT".equalsIgnoreCase(context)) {
            sb.append("- COMBAT: suggest the next 1-2 actions for this turn. Use hand, energy, monster intents.\n");
        } else if ("EVENT".equalsIgnoreCase(context)) {
            sb.append("- EVENT: choose the best option from event.options based on risk/reward and current state.\n");
        } else {
            sb.append("- GENERAL: give the most relevant decision for the current screen.\n");
        }
        return sb.toString();
    }

    public static Map<String, Object> buildRequestBody(LLMRequest request) {
        Prompt prompt = build(request);
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model);
        body.put("temperature", request.temperature);
        body.put("max_tokens", request.maxTokens);
        body.put("messages", prompt.toMessages());
        return body;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimTo(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
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
