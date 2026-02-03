package whispers.thespire.util;

import java.util.HashMap;
import java.util.Map;

public class Cache {
    private final Map<String, String> lastHash = new HashMap<>();

    public boolean isSame(String key, String hash) {
        if (hash == null) {
            return false;
        }
        String prev = lastHash.get(key);
        return hash.equals(prev);
    }

    public void update(String key, String hash) {
        if (hash == null) {
            return;
        }
        lastHash.put(key, hash);
    }
}
