package whispers.thespire.skills;

import com.evacipated.cardcrawl.modthespire.lib.ConfigUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import whispers.thespire.state.GameStateSnapshot;
import whispers.thespire.llm.model.LLMRecommendation;
import whispers.thespire.llm.model.LLMResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SkillLibrary {
    private static final Object LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_COOLDOWN_MS = 1500L;

    private static boolean loaded = false;
    private static long lastSaveMs = 0L;
    private static SkillStore store = new SkillStore();
    private static final List<String> pendingCombatSkillIds = new ArrayList<>();

    private SkillLibrary() {}

    public static void recordRecommendations(LLMResult result, GameStateSnapshot snapshot) {
        if (result == null || snapshot == null || result.recommendations == null) {
            return;
        }
        ensureLoaded();
        String context = snapshot.screen_context == null ? "OTHER" : snapshot.screen_context;
        String character = snapshot.run == null ? null : snapshot.run.character;
        int asc = snapshot.run == null || snapshot.run.ascension == null ? -1 : snapshot.run.ascension;
        String sourceHash = snapshot.snapshot_hash;
        List<String> tags = extractTags(snapshot);
        long now = System.currentTimeMillis();

        for (LLMRecommendation rec : result.recommendations) {
            if (rec == null) {
                continue;
            }
            SkillRecord record = new SkillRecord();
            record.id = UUID.randomUUID().toString();
            record.contextType = context;
            record.character = character;
            record.actionType = rec.action_type == null ? "general" : rec.action_type;
            record.title = safe(rec.title);
            record.action = safe(rec.action);
            record.reason = safe(rec.reason);
            record.summary = safe(result.summary);
            record.createdAt = now;
            record.lastUsedAt = now;
            record.uses = 1;
            record.wins = 0;
            record.losses = 0;
            record.avgHpDelta = 0f;
            record.sourceHash = sourceHash == null ? "" : sourceHash;
            record.tags = tags;
            record.ascensionBracket = bracketAscension(asc);

            SkillRecord merged = upsert(record);
            if ("COMBAT".equalsIgnoreCase(context)) {
                pendingCombatSkillIds.add(merged.id);
            }
        }

        prune();
        save();
    }

    public static void recordCombatOutcome(boolean win, int hpDelta) {
        ensureLoaded();
        if (pendingCombatSkillIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(pendingCombatSkillIds)) {
            SkillRecord record = findById(id);
            if (record == null) {
                continue;
            }
            if (win) {
                record.wins += 1;
            } else {
                record.losses += 1;
            }
            record.lastUsedAt = now;
            int total = record.wins + record.losses;
            if (total <= 0) {
                record.avgHpDelta = hpDelta;
            } else {
                record.avgHpDelta = (record.avgHpDelta * (total - 1) + hpDelta) / (float) total;
            }
        }
        pendingCombatSkillIds.clear();
        prune();
        save();
    }

    public static String buildSkillHints(GameStateSnapshot snapshot, int limit) {
        ensureLoaded();
        if (snapshot == null || store.skills.isEmpty()) {
            return "";
        }
        String context = snapshot.screen_context == null ? "OTHER" : snapshot.screen_context;
        String character = snapshot.run == null ? null : snapshot.run.character;
        int asc = snapshot.run == null || snapshot.run.ascension == null ? -1 : snapshot.run.ascension;
        List<String> currentTags = extractTags(snapshot);

        List<SkillScore> scores = new ArrayList<>();
        for (SkillRecord record : store.skills) {
            if (record == null) {
                continue;
            }
            if (!context.equalsIgnoreCase(record.contextType)) {
                continue;
            }
            float score = baseScore(record);
            if (character != null && character.equalsIgnoreCase(record.character)) {
                score += 0.12f;
            } else if (record.character == null || record.character.isEmpty()) {
                score += 0.02f;
            }
            if (asc >= 0 && record.ascensionBracket >= 0 && record.ascensionBracket == bracketAscension(asc)) {
                score += 0.04f;
            }
            int overlap = countOverlap(currentTags, record.tags);
            score += Math.min(0.12f, overlap * 0.03f);
            scores.add(new SkillScore(record, score));
        }

        if (scores.isEmpty()) {
            return "";
        }
        scores.sort(Comparator.comparingDouble((SkillScore s) -> s.score).reversed());
        int take = Math.min(limit, scores.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < take; i++) {
            SkillRecord record = scores.get(i).record;
            sb.append(i + 1).append(") ");
            sb.append(trim(record.title, 24)).append(" - ");
            sb.append(trim(record.action, 80));
            if (record.reason != null && !record.reason.isEmpty()) {
                sb.append(" | ").append(trim(record.reason, 80));
            }
            sb.append(" (uses=").append(record.uses);
            sb.append(", winrate=").append(String.format("%.2f", winRate(record))).append(")");
            if (i < take - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static SkillRecord upsert(SkillRecord incoming) {
        if (incoming == null) {
            return null;
        }
        SkillRecord best = null;
        float bestSim = 0f;
        for (SkillRecord record : store.skills) {
            if (record == null) {
                continue;
            }
            if (!safe(record.contextType).equalsIgnoreCase(safe(incoming.contextType))) {
                continue;
            }
            if (!safe(record.actionType).equalsIgnoreCase(safe(incoming.actionType))) {
                continue;
            }
            float sim = similarity(record.title + " " + record.action, incoming.title + " " + incoming.action);
            if (sim > bestSim) {
                bestSim = sim;
                best = record;
            }
        }

        if (best != null && bestSim >= 0.82f) {
            best.uses += 1;
            best.lastUsedAt = incoming.lastUsedAt;
            if (incoming.tags != null && !incoming.tags.isEmpty()) {
                best.tags = mergeTags(best.tags, incoming.tags, 12);
            }
            // Prefer the higher-scoring skill text
            if (baseScore(incoming) > baseScore(best)) {
                best.title = incoming.title;
                best.action = incoming.action;
                best.reason = incoming.reason;
                best.summary = incoming.summary;
            }
            if (incoming.sourceHash != null && !incoming.sourceHash.isEmpty()) {
                best.sourceHash = incoming.sourceHash;
            }
            if (incoming.character != null && !incoming.character.isEmpty()) {
                best.character = incoming.character;
            }
            best.ascensionBracket = incoming.ascensionBracket;
            return best;
        }

        store.skills.add(incoming);
        return incoming;
    }

    private static void prune() {
        if (store.skills.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<SkillRecord> kept = new ArrayList<>();
        for (SkillRecord record : store.skills) {
            if (record == null) {
                continue;
            }
            int uses = record.uses;
            float rate = winRate(record);
            long ageDays = (now - record.createdAt) / (1000L * 60 * 60 * 24);
            if (uses >= 5 && rate < 0.25f) {
                continue;
            }
            if (uses <= 1 && ageDays > 60) {
                continue;
            }
            kept.add(record);
        }
        store.skills = kept;
    }

    private static float baseScore(SkillRecord record) {
        if (record == null) {
            return 0f;
        }
        float rate = winRate(record);
        float usageBoost = Math.min(0.3f, record.uses * 0.02f);
        float recency = 0f;
        long ageMs = System.currentTimeMillis() - record.lastUsedAt;
        if (ageMs < 1000L * 60 * 60 * 24 * 7) {
            recency = 0.08f;
        } else if (ageMs < 1000L * 60 * 60 * 24 * 30) {
            recency = 0.04f;
        }
        return rate + usageBoost + recency;
    }

    private static float winRate(SkillRecord record) {
        int total = record.wins + record.losses;
        if (total == 0) {
            return 0.5f;
        }
        return record.wins / (float) total;
    }

    private static SkillRecord findById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (SkillRecord record : store.skills) {
            if (record != null && id.equals(record.id)) {
                return record;
            }
        }
        return null;
    }

    private static List<String> extractTags(GameStateSnapshot snapshot) {
        List<String> tags = new ArrayList<>();
        if (snapshot == null) {
            return tags;
        }
        addTags(tags, snapshot.relics, 6);
        addTags(tags, snapshot.potions, 4);
        if (snapshot.combat != null && snapshot.combat.hand != null) {
            int count = 0;
            for (GameStateSnapshot.CombatCardInfo card : snapshot.combat.hand) {
                if (card == null) {
                    continue;
                }
                String name = card.name == null || card.name.isEmpty() ? card.card_id : card.name;
                if (name != null && !name.isEmpty()) {
                    tags.add(name);
                    count++;
                    if (count >= 6) {
                        break;
                    }
                }
            }
        }
        if (tags.size() > 12) {
            return new ArrayList<>(tags.subList(0, 12));
        }
        return tags;
    }

    private static void addTags(List<String> tags, List<? extends Object> list, int limit) {
        if (list == null || tags == null) {
            return;
        }
        int count = 0;
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String name = null;
            if (item instanceof GameStateSnapshot.RelicInfo) {
                GameStateSnapshot.RelicInfo relic = (GameStateSnapshot.RelicInfo) item;
                name = relic.name == null || relic.name.isEmpty() ? relic.relic_id : relic.name;
            } else if (item instanceof GameStateSnapshot.PotionInfo) {
                GameStateSnapshot.PotionInfo potion = (GameStateSnapshot.PotionInfo) item;
                name = potion.name == null || potion.name.isEmpty() ? potion.potion_id : potion.name;
            }
            if (name != null && !name.isEmpty()) {
                tags.add(name);
                count++;
                if (count >= limit) {
                    break;
                }
            }
        }
    }

    private static int countOverlap(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> set = new HashSet<>();
        for (String v : a) {
            if (v != null && !v.isEmpty()) {
                set.add(v.toLowerCase());
            }
        }
        int overlap = 0;
        for (String v : b) {
            if (v != null && !v.isEmpty() && set.contains(v.toLowerCase())) {
                overlap++;
            }
        }
        return overlap;
    }

    private static float similarity(String a, String b) {
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0f;
        }
        int inter = 0;
        for (String t : ta) {
            if (tb.contains(t)) {
                inter++;
            }
        }
        int union = ta.size() + tb.size() - inter;
        return union == 0 ? 0f : (float) inter / (float) union;
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHan(c)) {
                flush(word, tokens);
                tokens.add(String.valueOf(c));
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '+' || c == '-') {
                word.append(Character.toLowerCase(c));
            } else {
                flush(word, tokens);
            }
        }
        flush(word, tokens);
        return tokens;
    }

    private static boolean isHan(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN;
    }

    private static void flush(StringBuilder word, Set<String> tokens) {
        if (word.length() > 0) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private static List<String> mergeTags(List<String> a, List<String> b, int limit) {
        List<String> merged = new ArrayList<>();
        if (a != null) {
            merged.addAll(a);
        }
        if (b != null) {
            for (String v : b) {
                if (v == null || v.isEmpty()) {
                    continue;
                }
                if (!merged.contains(v)) {
                    merged.add(v);
                }
            }
        }
        if (merged.size() > limit) {
            return new ArrayList<>(merged.subList(0, limit));
        }
        return merged;
    }

    private static int bracketAscension(int asc) {
        if (asc < 0) {
            return -1;
        }
        if (asc <= 4) {
            return 0;
        }
        if (asc <= 9) {
            return 1;
        }
        if (asc <= 14) {
            return 2;
        }
        if (asc <= 19) {
            return 3;
        }
        return 4;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            load();
            loaded = true;
        }
    }

    private static void load() {
        File file = getFile();
        if (!file.exists()) {
            store = new SkillStore();
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            store = GSON.fromJson(reader, SkillStore.class);
            if (store == null || store.skills == null) {
                store = new SkillStore();
            }
        } catch (Exception ignored) {
            store = new SkillStore();
        }
    }

    private static void save() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_COOLDOWN_MS) {
            return;
        }
        lastSaveMs = now;
        File file = getFile();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(GSON.toJson(store));
        } catch (Exception ignored) {
            // ignore save errors
        }
    }

    private static File getFile() {
        String base = ConfigUtils.CONFIG_DIR;
        File dir = new File(base, "WhispersTheSpire");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "skill_library.json");
    }

    private static class SkillStore {
        int version = 1;
        List<SkillRecord> skills = new ArrayList<>();
    }

    private static class SkillScore {
        final SkillRecord record;
        final float score;

        SkillScore(SkillRecord record, float score) {
            this.record = record;
            this.score = score;
        }
    }
}
