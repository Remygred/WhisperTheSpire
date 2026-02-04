package whispers.thespire.logic;

import whispers.thespire.util.Cache;
import whispers.thespire.util.Debouncer;
import whispers.thespire.logic.TriggerManager.TriggerEvent;

public class AutoRequestController {
    private static final long MAP_DEBOUNCE_MS = 5000L;
    private static final long REWARD_DEBOUNCE_MS = 2000L;
    private static final long POTION_DEBOUNCE_MS = 8000L;
    private static final long COMBAT_DEBOUNCE_MS = 1000L;
    private static final long NEOW_DEBOUNCE_MS = 5000L;
    private static final long SHOP_DEBOUNCE_MS = 3000L;
    private static final long BOSS_RELIC_DEBOUNCE_MS = 3000L;
    private static final long REST_DEBOUNCE_MS = 3000L;

    private final Debouncer debouncer = new Debouncer();
    private final Cache cache = new Cache();

    public Decision shouldRequest(TriggerEvent event, String snapshotHash, boolean busy) {
        if (event == null) {
            return Decision.no("auto skipped: no event");
        }
        if (busy) {
            return Decision.no("auto skipped: busy");
        }
        String context = event.contextType;
        if (cache.isSame(context, snapshotHash)) {
            return Decision.no("auto skipped: same hash");
        }
        long debounceMs = debounceFor(context);
        if (!debouncer.allow(context, debounceMs)) {
            return Decision.no("auto skipped: debounce");
        }
        return Decision.yes();
    }

    public void recordRequested(String context, String snapshotHash) {
        cache.update(context, snapshotHash);
    }

    public void reset() {
        debouncer.reset();
        cache.reset();
    }

    private long debounceFor(String context) {
        if ("MAP_PATH".equals(context)) {
            return MAP_DEBOUNCE_MS;
        }
        if ("CARD_REWARD".equals(context)) {
            return REWARD_DEBOUNCE_MS;
        }
        if ("POTION_OVERFLOW".equals(context)) {
            return POTION_DEBOUNCE_MS;
        }
        if ("COMBAT_TURN".equals(context)) {
            return COMBAT_DEBOUNCE_MS;
        }
        if ("NEOW".equals(context)) {
            return NEOW_DEBOUNCE_MS;
        }
        if ("SHOP".equals(context)) {
            return SHOP_DEBOUNCE_MS;
        }
        if ("BOSS_RELIC".equals(context)) {
            return BOSS_RELIC_DEBOUNCE_MS;
        }
        if ("REST".equals(context)) {
            return REST_DEBOUNCE_MS;
        }
        return 3000L;
    }

    public static class Decision {
        public final boolean allow;
        public final String reason;

        private Decision(boolean allow, String reason) {
            this.allow = allow;
            this.reason = reason;
        }

        public static Decision yes() {
            return new Decision(true, "");
        }

        public static Decision no(String reason) {
            return new Decision(false, reason);
        }
    }
}
