package whispers.thespire.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class SnapshotManager {
    private static final int MAX_JSON = 8000;
    private static final long MIN_INTERVAL_MS = 200L;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static long lastUpdateMs = 0L;
    private static String lastKey = "";
    private static GameStateSnapshot lastSnapshot;
    private static String lastJson;
    private static String lastSummary = "";
    private static String lastStatus = "";
    private static boolean forceRefresh = false;

    private SnapshotManager() {}

    public static void requestRefresh() {
        forceRefresh = true;
    }

    public static Result update(boolean wantJson) {
        return update(wantJson, false);
    }

    public static Result update(boolean wantJson, boolean includeCombat) {
        long now = System.currentTimeMillis();
        String key = StateExtractor.buildLightweightKey();
        boolean shouldRefresh = forceRefresh || lastSnapshot == null
                || (now - lastUpdateMs > MIN_INTERVAL_MS && !key.equals(lastKey));

        if (shouldRefresh) {
            forceRefresh = false;
            GameStateSnapshot snapshot = StateExtractor.extract(includeCombat);
            String hash = computeHash(snapshot);
            snapshot.snapshot_hash = hash;

            String json = buildJsonWithTrim(snapshot);
            snapshot.json_size = json.length();
            snapshot.trimmed = snapshot.dropped_fields != null && !snapshot.dropped_fields.isEmpty();

            lastSnapshot = snapshot;
            lastJson = wantJson ? json : null;
            lastSummary = buildSummary(snapshot);
            lastStatus = buildStatus(snapshot);
            lastKey = key;
            lastUpdateMs = now;
        } else if (wantJson && lastJson == null && lastSnapshot != null) {
            lastJson = GSON.toJson(lastSnapshot);
        } else if (!wantJson) {
            lastJson = null;
        }

        return new Result(lastSnapshot, wantJson ? lastJson : null, lastSummary, lastStatus);
    }

    private static String buildSummary(GameStateSnapshot snapshot) {
        String screen = snapshot == null || snapshot.screen_context == null ? "OTHER" : snapshot.screen_context;
        Integer floor = snapshot != null && snapshot.run != null ? snapshot.run.floor : null;
        Integer hp = snapshot != null && snapshot.run != null ? snapshot.run.hp : null;
        Integer maxHp = snapshot != null && snapshot.run != null ? snapshot.run.maxHp : null;
        Integer gold = snapshot != null && snapshot.run != null ? snapshot.run.gold : null;

        String floorText = floor == null ? "?" : floor.toString();
        String hpText = hp == null ? "?" : hp.toString();
        String maxHpText = maxHp == null ? "?" : maxHp.toString();
        String goldText = gold == null ? "?" : gold.toString();

        return "screen=" + screen + ", floor=" + floorText + ", hp=" + hpText + "/" + maxHpText + ", gold=" + goldText;
    }

    private static String buildStatus(GameStateSnapshot snapshot) {
        if (snapshot == null) {
            return "snapshot n/a";
        }
        StringBuilder sb = new StringBuilder();
        if (snapshot.trimmed) {
            sb.append("trimmed");
        } else {
            sb.append("snapshot ok");
        }
        sb.append(" size=").append(snapshot.json_size);
        if (snapshot.dropped_fields != null && !snapshot.dropped_fields.isEmpty()) {
            sb.append(" dropped=").append(String.join(",", snapshot.dropped_fields));
        }
        if (snapshot.snapshot_hash != null && snapshot.snapshot_hash.length() >= 8) {
            sb.append(" hash=").append(snapshot.snapshot_hash.substring(0, 8));
        }
        return sb.toString();
    }

    private static String buildJsonWithTrim(GameStateSnapshot snapshot) {
        if (snapshot.dropped_fields == null) {
            snapshot.dropped_fields = new ArrayList<>();
        } else {
            snapshot.dropped_fields.clear();
        }

        String json = GSON.toJson(snapshot);
        if (json.length() <= MAX_JSON) {
            return json;
        }

        String context = snapshot.screen_context == null ? "OTHER" : snapshot.screen_context;
        List<TrimStep> steps = buildTrimSteps(context);
        for (TrimStep step : steps) {
            if (step.apply(snapshot, snapshot.dropped_fields)) {
                json = GSON.toJson(snapshot);
                if (json.length() <= MAX_JSON) {
                    return json;
                }
            }
        }

        if (json.length() > MAX_JSON) {
            if (snapshot.deck_summary != null) {
                snapshot.deck_summary = null;
                addDropped(snapshot.dropped_fields, "deck_summary.dropped");
                json = GSON.toJson(snapshot);
                if (json.length() <= MAX_JSON) {
                    return json;
                }
            }
            if (snapshot.relics != null) {
                snapshot.relics = null;
                addDropped(snapshot.dropped_fields, "relics.dropped");
                json = GSON.toJson(snapshot);
                if (json.length() <= MAX_JSON) {
                    return json;
                }
            }
            if (snapshot.potions != null) {
                snapshot.potions = null;
                addDropped(snapshot.dropped_fields, "potions.dropped");
                json = GSON.toJson(snapshot);
                if (json.length() <= MAX_JSON) {
                    return json;
                }
            }
            if (snapshot.reward != null) {
                snapshot.reward = null;
                addDropped(snapshot.dropped_fields, "reward.dropped");
                json = GSON.toJson(snapshot);
            }
        }

        return json;
    }

    private static List<TrimStep> buildTrimSteps(String context) {
        List<TrimStep> steps = new ArrayList<>();
        TrimStep combatHandTrim = SnapshotManager::trimCombatHandTo10;
        TrimStep combatMonstersTrim = SnapshotManager::trimCombatMonstersTo3;
        TrimStep combatPlayerPowersTrim = SnapshotManager::trimCombatPlayerPowersTo10;
        TrimStep combatMonsterPowersTrim = SnapshotManager::trimCombatMonsterPowersTo10;
        TrimStep mapTrim = SnapshotManager::trimMapTo3;
        TrimStep mapDrop = SnapshotManager::dropMap;
        TrimStep deckTrim = SnapshotManager::trimDeckTo30;
        TrimStep relicTrim = SnapshotManager::trimRelicsTo20;
        TrimStep relicIdOnly = SnapshotManager::relicsIdOnly;
        TrimStep potionIdOnly = SnapshotManager::potionsIdOnly;
        TrimStep rewardTrim = SnapshotManager::trimRewardTo3;
        TrimStep rewardIdOnly = SnapshotManager::rewardIdOnly;

        if ("COMBAT".equals(context)) {
            steps.add(combatHandTrim);
            steps.add(combatMonstersTrim);
            steps.add(combatPlayerPowersTrim);
            steps.add(combatMonsterPowersTrim);
            steps.add(mapDrop);
            steps.add(rewardTrim);
            steps.add(rewardIdOnly);
            steps.add(deckTrim);
            steps.add(relicTrim);
            steps.add(relicIdOnly);
            steps.add(potionIdOnly);
            return steps;
        }

        if ("MAP".equals(context)) {
            steps.add(rewardTrim);
            steps.add(rewardIdOnly);
            steps.add(deckTrim);
            steps.add(relicTrim);
            steps.add(relicIdOnly);
            steps.add(potionIdOnly);
            steps.add(mapTrim);
            steps.add(mapDrop);
        } else if ("CARD_REWARD".equals(context)) {
            steps.add(mapTrim);
            steps.add(mapDrop);
            steps.add(deckTrim);
            steps.add(relicTrim);
            steps.add(relicIdOnly);
            steps.add(potionIdOnly);
            steps.add(rewardTrim);
            steps.add(rewardIdOnly);
        } else {
            steps.add(mapTrim);
            steps.add(mapDrop);
            steps.add(deckTrim);
            steps.add(relicTrim);
            steps.add(relicIdOnly);
            steps.add(potionIdOnly);
            steps.add(rewardTrim);
            steps.add(rewardIdOnly);
        }

        return steps;
    }

    private static boolean trimMapTo3(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.map == null || snapshot.map.next_nodes == null) {
            return false;
        }
        if (snapshot.map.next_nodes.size() > 3) {
            snapshot.map.next_nodes = new ArrayList<>(snapshot.map.next_nodes.subList(0, 3));
            dropped.add("map.next_nodes.truncated_to_3");
            return true;
        }
        return false;
    }

    private static boolean dropMap(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.map != null) {
            snapshot.map = null;
            dropped.add("map");
            return true;
        }
        return false;
    }

    private static boolean trimDeckTo30(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.deck_summary == null) {
            return false;
        }
        if (snapshot.deck_summary.size() > 30) {
            snapshot.deck_summary = new ArrayList<>(snapshot.deck_summary.subList(0, 30));
            dropped.add("deck_summary.truncated_to_30");
            return true;
        }
        return false;
    }

    private static boolean trimRelicsTo20(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.relics == null) {
            return false;
        }
        if (snapshot.relics.size() > 20) {
            snapshot.relics = new ArrayList<>(snapshot.relics.subList(0, 20));
            dropped.add("relics.truncated_to_20");
            return true;
        }
        return false;
    }

    private static boolean relicsIdOnly(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.relics == null) {
            return false;
        }
        boolean changed = false;
        for (GameStateSnapshot.RelicInfo relic : snapshot.relics) {
            if (relic != null && relic.name != null) {
                relic.name = null;
                changed = true;
            }
        }
        if (changed) {
            dropped.add("relics.id_only");
        }
        return changed;
    }

    private static boolean potionsIdOnly(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.potions == null) {
            return false;
        }
        boolean changed = false;
        for (GameStateSnapshot.PotionInfo potion : snapshot.potions) {
            if (potion != null && potion.name != null) {
                potion.name = null;
                changed = true;
            }
        }
        if (changed) {
            dropped.add("potions.id_only");
        }
        return changed;
    }

    private static boolean trimRewardTo3(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.reward == null || snapshot.reward.choices == null) {
            return false;
        }
        if (snapshot.reward.choices.size() > 3) {
            snapshot.reward.choices = new ArrayList<>(snapshot.reward.choices.subList(0, 3));
            dropped.add("reward.choices.truncated_to_3");
            return true;
        }
        return false;
    }

    private static boolean rewardIdOnly(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.reward == null || snapshot.reward.choices == null) {
            return false;
        }
        boolean changed = false;
        for (GameStateSnapshot.CardInfo card : snapshot.reward.choices) {
            if (card == null) {
                continue;
            }
            if (card.name != null || card.cost != null || card.type != null || card.rarity != null) {
                card.name = null;
                card.cost = null;
                card.type = null;
                card.rarity = null;
                changed = true;
            }
        }
        if (changed) {
            dropped.add("reward.choices.id_only");
        }
        return changed;
    }

    private static boolean trimCombatHandTo10(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.combat == null || snapshot.combat.hand == null) {
            return false;
        }
        if (snapshot.combat.hand.size() > 10) {
            snapshot.combat.hand = new ArrayList<>(snapshot.combat.hand.subList(0, 10));
            dropped.add("combat.hand.truncated_to_10");
            return true;
        }
        return false;
    }

    private static boolean trimCombatMonstersTo3(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.combat == null || snapshot.combat.monsters == null) {
            return false;
        }
        if (snapshot.combat.monsters.size() > 3) {
            snapshot.combat.monsters = new ArrayList<>(snapshot.combat.monsters.subList(0, 3));
            dropped.add("combat.monsters.truncated_to_3");
            return true;
        }
        return false;
    }

    private static boolean trimCombatPlayerPowersTo10(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.combat == null || snapshot.combat.player_powers == null) {
            return false;
        }
        if (snapshot.combat.player_powers.size() > 10) {
            snapshot.combat.player_powers = new ArrayList<>(snapshot.combat.player_powers.subList(0, 10));
            dropped.add("combat.player_powers.truncated_to_10");
            return true;
        }
        return false;
    }

    private static boolean trimCombatMonsterPowersTo10(GameStateSnapshot snapshot, List<String> dropped) {
        if (snapshot.combat == null || snapshot.combat.monsters == null) {
            return false;
        }
        boolean changed = false;
        for (GameStateSnapshot.MonsterInfo monster : snapshot.combat.monsters) {
            if (monster == null || monster.powers == null) {
                continue;
            }
            if (monster.powers.size() > 10) {
                monster.powers = new ArrayList<>(monster.powers.subList(0, 10));
                changed = true;
            }
        }
        if (changed) {
            dropped.add("combat.monster_powers.truncated_to_10");
        }
        return changed;
    }

    private static void addDropped(List<String> dropped, String value) {
        if (!dropped.contains(value)) {
            dropped.add(value);
        }
    }

    private static String computeHash(GameStateSnapshot snapshot) {
        try {
            String stable = buildStableString(snapshot);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(stable.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String buildStableString(GameStateSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        if (snapshot == null) {
            return "";
        }
        sb.append(safe(snapshot.screen_context)).append('|');
        if (snapshot.run != null) {
            sb.append(value(snapshot.run.act)).append('|');
            sb.append(value(snapshot.run.floor)).append('|');
            sb.append(value(snapshot.run.ascension)).append('|');
            sb.append(value(snapshot.run.gold)).append('|');
            sb.append(value(snapshot.run.hp)).append('/').append(value(snapshot.run.maxHp)).append('|');
        }
        appendList(sb, snapshot.deck_summary, card -> card.card_id);
        appendList(sb, snapshot.relics, relic -> relic.relic_id);
        appendList(sb, snapshot.potions, potion -> potion.potion_id);
        if (snapshot.map != null) {
            sb.append("map:").append(value(snapshot.map.curr_x)).append(',').append(value(snapshot.map.curr_y)).append('|');
            appendList(sb, snapshot.map.next_nodes, node -> node.x + "," + node.y);
        }
        if (snapshot.reward != null) {
            appendList(sb, snapshot.reward.choices, card -> card.card_id);
        }
        if (snapshot.combat != null) {
            sb.append("turn=").append(value(snapshot.combat.turn)).append('|');
            sb.append("energy=").append(value(snapshot.combat.energy)).append('|');
            sb.append("block=").append(value(snapshot.combat.player_block)).append('|');
            appendList(sb, snapshot.combat.player_powers, power -> power.id + ":" + value(power.amount));
            appendList(sb, snapshot.combat.hand, card -> card.card_id);
            if (snapshot.combat.monsters != null) {
                for (GameStateSnapshot.MonsterInfo monster : snapshot.combat.monsters) {
                    if (monster == null) {
                        continue;
                    }
                    sb.append("m=").append(monster.id).append(":")
                            .append(value(monster.hp)).append("/")
                            .append(value(monster.maxHp)).append(":")
                            .append(monster.intent == null ? "" : monster.intent).append('|');
                }
            }
        }
        return sb.toString();
    }

    private static String value(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private interface Extractor<T> {
        String get(T item);
    }

    private static <T> void appendList(StringBuilder sb, List<T> list, Extractor<T> extractor) {
        if (list == null || list.isEmpty()) {
            sb.append("[]|");
            return;
        }
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            T item = list.get(i);
            if (item == null) {
                continue;
            }
            sb.append(extractor.get(item));
            if (i < list.size() - 1) {
                sb.append(',');
            }
        }
        sb.append("]|");
    }

    private interface TrimStep {
        boolean apply(GameStateSnapshot snapshot, List<String> dropped);
    }

    public static class Result {
        public final GameStateSnapshot snapshot;
        public final String json;
        public final String summaryLine;
        public final String statusLine;

        private Result(GameStateSnapshot snapshot, String json, String summaryLine, String statusLine) {
            this.snapshot = snapshot;
            this.json = json;
            this.summaryLine = summaryLine;
            this.statusLine = statusLine;
        }
    }
}
