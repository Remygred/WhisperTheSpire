package whispers.thespire.i18n;

import whispers.thespire.config.ModConfig;

public class I18n {
    private I18n() {}

    public static boolean isZh() {
        return "zh".equalsIgnoreCase(ModConfig.language);
    }

    public static String t(String key) {
        boolean zh = isZh();
        switch (key) {
            case "settings_title":
                return zh ? "\u0057\u0068\u0069\u0073\u0070\u0065\u0072\u0073\u0054\u0068\u0065\u0053\u0070\u0069\u0072\u0065\u0020\u8BBE\u7F6E" : "WhispersTheSpire Settings";
            case "language":
                return zh ? "\u8BED\u8A00\u003A\u0020\u4E2D\u6587" : "Language: English";
            case "overlay_enabled":
                return zh ? "\u663E\u793A\u5C0F\u7A97" : "Overlay enabled";
            case "auto_triggers_enabled":
                return zh ? "\u81EA\u52A8\u89E6\u53D1" : "Auto triggers enabled";
            case "combat_advice_enabled":
                return zh ? "\u6218\u6597\u5EFA\u8BAE" : "Combat advice enabled";
            case "debug_show_snapshot":
                return zh ? "\u8C03\u8BD5\u003A\u0020\u663E\u793A\u5FEB\u7167" : "Debug: show snapshot";
            case "debug_show_raw_response":
                return zh ? "\u8C03\u8BD5\u003A\u0020\u663E\u793A\u539F\u59CB\u54CD\u5E94" : "Debug: show raw response";
            case "show_reasons":
                return zh ? "\u663E\u793A\u539F\u56E0" : "Show reasons";
            case "multi_recommendations":
                return zh ? "\u591A\u6761\u5EFA\u8BAE" : "Multiple recommendations";
            case "use_knowledge":
                return zh ? "\u4F7F\u7528\u77E5\u8BC6\u5E93" : "Use knowledge base";
            case "show_combat_hand":
                return zh ? "\u663E\u793A\u624B\u724C\u5217\u8868" : "Show hand list";
            case "show_combat_enemies":
                return zh ? "\u663E\u793A\u654C\u4EBA\u5217\u8868" : "Show enemy list";
            case "provider":
                return zh ? "\u670D\u52A1\u5546" : "provider";
            case "baseUrl":
                return zh ? "Base URL" : "baseUrl";
            case "model":
                return zh ? "\u6A21\u578B" : "model";
            case "apiKey":
                return zh ? "API Key" : "apiKey";
            case "temperature":
                return zh ? "\u6E29\u5EA6" : "temperature";
            case "maxTokens":
                return zh ? "\u6700\u5927\u0020\u0074\u006F\u006B\u0065\u006E\u0073" : "maxTokens";
            case "timeoutMs":
                return zh ? "\u8D85\u65F6\u0028\u006D\u0073\u0029" : "timeoutMs";
            case "refresh_hotkey":
                return zh ? "\u5237\u65B0\u70ED\u952E\u003A\u0020\u0046\u0038" : "Refresh hotkey: F8";
            case "toggle_hotkey":
                return zh ? "\u663E\u793A\u002F\u9690\u85CF\u70ED\u952E\u003A\u0020\u0046\u0039" : "Toggle hotkey: F9";
            case "refresh":
                return zh ? "\u5237\u65B0" : "Refresh";
            case "hide":
                return zh ? "\u9690\u85CF" : "Hide";
            case "show":
                return zh ? "\u663E\u793A" : "Show";
            case "auto":
                return zh ? "\u81EA\u52A8" : "AUTO";
            case "last":
                return zh ? "\u4E0A\u6B21" : "last";
            case "context":
                return zh ? "\u4E0A\u4E0B\u6587" : "context";
            case "summary":
                return zh ? "\u6458\u8981" : "Summary";
            case "reason":
                return zh ? "\u539F\u56E0" : "reason";
            case "screen":
                return zh ? "\u754C\u9762" : "screen";
            case "floor":
                return zh ? "\u5C42\u6570" : "floor";
            case "hp":
                return zh ? "\u751F\u547D" : "hp";
            case "asc":
                return zh ? "\u5347\u9636" : "asc";
            case "gold":
                return zh ? "\u91D1\u5E01" : "gold";
            case "snapshot_ok":
                return zh ? "\u5FEB\u7167\u6B63\u5E38" : "snapshot ok";
            case "snapshot_trimmed":
                return zh ? "\u5FEB\u7167\u88C1\u526A" : "trimmed";
            case "size":
                return zh ? "\u5927\u5C0F" : "size";
            case "hash":
                return zh ? "\u54C8\u5E0C" : "hash";
            case "dropped":
                return zh ? "\u5220\u9664" : "dropped";
            case "llm":
                return zh ? "LLM" : "LLM";
            case "analyzing":
                return zh ? "\u5206\u6790\u4E2D\u002E\u002E\u002E" : "Analyzing...";
            case "next_pick":
                return zh ? "\u9009\u62E9" : "Next pick";
            case "route_plan":
                return zh ? "\u8DEF\u7EBF\u89C4\u5212" : "Route plan";
            case "turn_suggestion":
                return zh ? "\u7B2C\u0025\u0064\u56DE\u5408\u5EFA\u8BAE" : "Turn %d suggestion";
            case "combat_hand":
                return zh ? "\u624B\u724C" : "Hand";
            case "combat_enemies":
                return zh ? "\u654C\u4EBA" : "Enemies";
            case "combat_debug":
                return zh ? "\u6218\u6597\u003A\u0020\u624B\u724C\u003D\u0025\u0064\u002C\u0020\u654C\u4EBA\u003D\u0025\u0064" : "Combat: hand=%d, monsters=%d";
            case "na":
                return zh ? "\u65E0" : "N/A";
            default:
                return key;
        }
    }

    public static String localizeContext(String context) {
        if (context == null) {
            return t("na");
        }
        if (!isZh()) {
            return context;
        }
        switch (context) {
            case "MANUAL":
                return "\u624B\u52A8";
            case "COMBAT":
            case "COMBAT_TURN":
                return "\u6218\u6597";
            case "MAP":
            case "MAP_PATH":
                return "\u5730\u56FE";
            case "CARD_REWARD":
                return "\u9009\u724C";
            case "POTION_OVERFLOW":
                return "\u836F\u6C34\u6EA2\u51FA";
            case "SHOP":
                return "\u5546\u5E97";
            case "EVENT":
                return "\u4E8B\u4EF6";
            case "NEOW":
                return "\u6D85\u5965";
            case "BOSS_RELIC":
                return "\u9996\u9886\u9057\u7269";
            case "REST":
                return "\u4F11\u606F";
            case "OTHER":
                return "\u5176\u5B83";
            default:
                return context;
        }
    }

    public static String localizeRoomType(String roomType) {
        if (roomType == null || roomType.isEmpty()) {
            return t("na");
        }
        String type = roomType.toUpperCase();
        if (!isZh()) {
            switch (type) {
                case "MONSTER":
                    return "Monster";
                case "ELITE":
                    return "Elite";
                case "REST":
                    return "Rest";
                case "SHOP":
                    return "Shop";
                case "EVENT":
                    return "Event";
                case "TREASURE":
                    return "Treasure";
                case "BOSS":
                    return "Boss";
                case "UNKNOWN":
                    return "Unknown";
                default:
                    return roomType;
            }
        }
        switch (type) {
            case "MONSTER":
                return "\u666E\u901A";
            case "ELITE":
                return "\u7CBE\u82F1";
            case "REST":
                return "\u4F11\u606F\u5904";
            case "SHOP":
                return "\u5546\u5E97";
            case "EVENT":
                return "\u4E8B\u4EF6";
            case "TREASURE":
                return "\u5B9D\u7BB1";
            case "BOSS":
                return "\u5934\u76EE";
            case "UNKNOWN":
                return "\u672A\u77E5";
            default:
                return roomType;
        }
    }
}
