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
        boolean handPresent = request != null
                && request.combatHandCount != null
                && request.combatHandCount > 0;
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

        String system = "You are an expert Slay the Spire coach (A20+ level). "
                + "You MUST output only a single JSON object and nothing else. "
                + "Do NOT wrap in markdown. Follow the exact schema and field limits. "
                + "Be decisive, high-signal, and specific. Avoid generic or beginner advice. "
                + "Use at least 2 concrete facts from snapshot_json (cards/relics/potions/hp/gold/map/hand/intents) "
                + "to justify your recommendation. If key info is missing, say so explicitly. "
                + "Hard rule: if you mention ascension (A#), it MUST match facts.ascension; "
                + "if facts.ascension is missing, do NOT mention ascension.";
        if (isCombat) {
            system += " For COMBAT: recommendations max " + maxRecs + ", action_type must be combat_line, "
                    + "summary<=30, action<=50, reason<=60. "
                    + "You MUST reference current hand, energy, enemy intents (including damage/hits if present), player HP, and potions when available. "
                    + "Hard rule: mention at least one card from the current hand list (from essential_facts or snapshot_json.combat.hand). "
                    + "If hand_present=false, state 'hand data missing' and avoid specific play lines. "
                    + "If hand_present=true, you MUST NOT claim hand data is missing. "
                    + "If hand_count>=2, do NOT claim 'only one card' in hand. "
                    + "If energy>0 and playable_cards is not empty, you MUST recommend spending energy (play at least 2 cards or all available energy), "
                    + "and you MUST NOT recommend ending turn immediately.";
        } else if ("EVENT".equalsIgnoreCase(safeContext)) {
            system += " For EVENT: you MUST reference event_name and pick from event_options only. "
                    + "Do not invent options; if event_options missing, say so.";
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
        if (request != null && "EVENT".equalsIgnoreCase(safeContext)) {
            if (request.eventName != null && !request.eventName.trim().isEmpty()) {
                user.append("event_name: ").append(request.eventName).append("\n");
            }
            if (request.eventOptions != null && !request.eventOptions.trim().isEmpty()) {
                user.append("event_options: ").append(request.eventOptions).append("\n");
            }
        }
        user.append("show_reasons: ").append(showReasons).append("\n");
        if (isCombat) {
            user.append("hand_present: ").append(handPresent).append("\n");
            if (request != null) {
                user.append("hand_count: ").append(request.combatHandCount == null ? "?" : request.combatHandCount).append("\n");
                user.append("energy: ").append(request.combatEnergy == null ? "?" : request.combatEnergy).append("\n");
                if (request.combatPlayableCards != null && !request.combatPlayableCards.trim().isEmpty()) {
                    user.append("playable_cards: ").append(request.combatPlayableCards).append("\n");
                }
            }
        }
        user.append("max_recommendations: ").append(maxRecs).append("\n");
        if (ModConfig.useKnowledgeBase) {
            String notes = KnowledgeBase.getNotes(safeContext);
            if (notes != null && !notes.trim().isEmpty()) {
                user.append("knowledge_notes:\n").append(trimTo(notes, 1200)).append("\n");
                user.append("Use knowledge_notes as general guidance; if it conflicts with snapshot_json, follow snapshot_json.\n");
            }
            String charNotes = KnowledgeBase.getCharacterNotes(request == null ? null : request.character);
            if (charNotes != null && !charNotes.trim().isEmpty()) {
                user.append("character_notes:\n").append(trimTo(charNotes, 800)).append("\n");
                user.append("Use character_notes when relevant; still prioritize snapshot_json facts.\n");
            }
        }
        if (request != null && request.skillHints != null && !request.skillHints.trim().isEmpty()) {
            user.append("skill_hints:\n").append(trimTo(request.skillHints, 1200)).append("\n");
            user.append("Use skill_hints as high-quality prior strategies. If they conflict with snapshot_json, follow snapshot_json.\n");
        }
        user.append("snapshot_json: ").append(safeJson).append("\n");
        if (truncated) {
            user.append("snapshot_json_truncated: true\n");
        }
        user.append("Task guidance (be professional, not generic):\n");
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
            user.append("route_plan should describe the full route plan for this act (elite count, rest/shop timing).\n");
        }

        return new Prompt(system, user.toString());
    }

    private static String buildGuidance(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Use snapshot_json to infer deck, relics, potions, hp, gold, ascension, seed.\n");
        if ("NEOW".equalsIgnoreCase(context)) {
            sb.append("- NEOW: choose the best blessing from neow.options only. Do not invent options. ")
              .append("Prioritize early power or consistency. Output which option to pick.\n");
        } else if ("BOSS_RELIC".equalsIgnoreCase(context)) {
            sb.append("- BOSS_RELIC: choose 1 relic from boss_relic.choices (or skip if truly bad). ")
              .append("Be decisive.\n");
        } else if ("REST".equalsIgnoreCase(context)) {
            sb.append("- REST: choose between rest/smith/other campfire options in rest.options. ")
              .append("If choosing smith, pick 1 card from rest.upgrade_options.\n");
        } else if ("CARD_REWARD".equalsIgnoreCase(context)) {
            sb.append("- CARD_REWARD: pick 1 card or SKIP. Use deck_summary + relics + current weakness. ")
              .append("If all weak, recommend skip. Do not pick multiple cards.\n");
        } else if ("MAP".equalsIgnoreCase(context) || "MAP_PATH".equalsIgnoreCase(context)) {
            sb.append("- MAP: choose the next node from map.next_nodes based on hp/gold/relics/deck. ")
              .append("If low hp, prefer rest or safer path. Describe nodes by room type, not coordinates.\n")
              .append("If map_full is present, plan the whole act route.\n");
        } else if ("SHOP".equalsIgnoreCase(context)) {
            sb.append("- SHOP: recommend what to buy (cards/relics/potions) and whether to remove a card (purge). ")
              .append("If purging, name a card from shop.purge_candidates. ")
              .append("Give a priority order when multiple options exist.\n");
        } else if ("COMBAT".equalsIgnoreCase(context)) {
            sb.append("- COMBAT: suggest the next 1-2 actions for this turn. Use hand, energy, monster intents (and intent_dmg/intent_hits if present), player HP, and potions. ")
              .append("Specify targets and play order. Mention at least one card from the current hand list; if missing, say so. ")
              .append("If energy>0 and playable_cards is not empty, do NOT recommend ending turn without playing at least one. ")
              .append("Prefer using most available energy unless you explicitly cite a reason (e.g., Ice Cream energy carry).\n");
        } else if ("EVENT".equalsIgnoreCase(context)) {
            sb.append("- EVENT: choose the best option from event.options only. ")
              .append("You MUST name the event (event_name) and pick by index if options are numbered. ")
              .append("Do not invent options; if options missing, say 'event options missing'.\n");
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
