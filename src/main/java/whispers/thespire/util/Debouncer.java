package whispers.thespire.util;

import java.util.HashMap;
import java.util.Map;

public class Debouncer {
    private final Map<String, Long> lastFire = new HashMap<>();

    public boolean allow(String key, long windowMs) {
        long now = System.currentTimeMillis();
        Long last = lastFire.get(key);
        if (last == null || now - last >= windowMs) {
            lastFire.put(key, now);
            return true;
        }
        return false;
    }
}
