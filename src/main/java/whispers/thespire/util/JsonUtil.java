package whispers.thespire.util;

public class JsonUtil {
    private JsonUtil() {}

    public static String stripCodeFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return content;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline > -1 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return content;
    }

    public static String extractFirstJsonObject(String content) {
        if (content == null) {
            return null;
        }
        int len = content.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    public static String truncate(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
