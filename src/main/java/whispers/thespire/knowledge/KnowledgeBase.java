package whispers.thespire.knowledge;

import whispers.thespire.i18n.I18n;

public class KnowledgeBase {
    private KnowledgeBase() {}

    public static String getNotes(String context) {
        boolean zh = I18n.isZh();
        if (context == null) {
            context = "OTHER";
        }
        if (zh) {
            return getNotesZh(context);
        }
        return getNotesEn(context);
    }

    private static String getNotesEn(String context) {
        switch (context) {
            case "NEOW":
                return "- Neow bonuses vary and often trade reward for drawback.\n"
                        + "- The \"first 3 enemies have 1 HP\" option can enable an early elite if the map allows.\n";
            case "MAP":
            case "MAP_PATH":
                return "- Elites start appearing from floor 6.\n"
                        + "- Elite fights give a relic + gold + card reward; higher ascensions make elites more common/stronger.\n"
                        + "- Elite/Rest/Merchant rooms cannot be consecutive on the map.\n"
                        + "- Use map_current and map_next to compare node types; prioritize safety if HP is low.\n"
                        + "- Early Act 1 often wants 1-2 elites if your deck can handle them.\n"
                        + "- If map_full is present, plan a full-act route (elite count, shops, rest stops) before choosing the next node.\n";
            case "CARD_REWARD":
                return "- Card rewards can be skipped; skip when none improve your current deck.\n"
                        + "- Adding cards slows deck cycling, so prefer synergy and solving current weaknesses.\n";
            case "SHOP":
                return "- Card removal costs 75 gold initially and increases by 25 each time.\n"
                        + "- Smiling Mask fixes removal cost at 50; some relics affect shop prices.\n"
                        + "- If purging, prefer curses first, then weak basic cards (context dependent).\n"
                        + "- Use shop.purge_candidates when recommending a removal target.\n";
            case "BOSS_RELIC":
                return "- Boss relic choices heavily shape the run; pick based on your deck and ability to handle the drawback.\n";
            case "REST":
                return "- Rest if survival is at risk; Smith if HP is safe and you have a high-impact upgrade.\n"
                        + "- If Smithing, choose a card from rest.upgrade_options that improves damage, scaling, or block consistency.\n";
            default:
                return "- Prefer cohesive decks; skip options that do not improve the current plan.\n";
        }
    }

    private static String getNotesZh(String context) {
        switch (context) {
            case "NEOW":
                return "- \u6D85\u5965\u795D\u798F\u5E38\u5E38\u662F\u5956\u52B1+\u4EE3\u4EF7\u7684\u7EC4\u5408\u3002\n"
                        + "- \u201C\u524D\u4E09\u6218\u0031\u8840\u201D\u9009\u9879\u5728\u9AD8\u5347\u9636\u4E5F\u5F88\u5F3A\uFF0C\u82E5\u5730\u56FE\u53EF\u6253\u65E9\u671F\u7CBE\u82F1\u53EF\u8003\u8651\u3002\n";
            case "MAP":
            case "MAP_PATH":
                return "- \u7CBE\u82F1\u4ECE\u7B2C\u0036\u5C42\u5F00\u59CB\u51FA\u73B0\u3002\n"
                        + "- \u7CBE\u82F1\u6389\u843D\u9057\u7269+\u91D1\u5E01+\u5361\u724C\uFF0C\u9AD8\u5347\u9636\u7CBE\u82F1\u66F4\u5E38\u89C1/\u66F4\u5F3A\u3002\n"
                        + "- \u7CBE\u82F1/\u5546\u5E97/\u4F11\u606F\u4E0D\u53EF\u8FDE\u7EED\u76F8\u8FDE\u3002\n"
                        + "- \u7528 map_current / map_next \u8C03\u6574\u8DEF\u7EBF\u9009\u62E9\uFF0C\u4F4E\u8840\u4F18\u5148\u5B89\u5168\u70B9\u3002\n"
                        + "- Act1 \u65E9\u671F\u5E38\u8981 1-2 \u4E2A\u7CBE\u82F1\uFF08\u524D\u63D0\u5361\u7EC4\u80FD\u625B\u4F4F\uFF09\u3002\n"
                        + "- \u5982\u679C\u6709 map_full \uFF0C\u8BF7\u5148\u89C4\u5212\u6574\u5C42\u8DEF\u7EBF\uFF08\u7CBE\u82F1\u6570/\u5546\u5E97/\u4F11\u606F\u8282\u70B9\uFF09\u518D\u9009\u4E0B\u4E00\u4E2A\u8282\u70B9\u3002\n";
            case "CARD_REWARD":
                return "- \u5361\u724C\u5956\u52B1\u53EF\u4EE5\u8DF3\u8FC7\uFF0C\u6CA1\u6709\u63D0\u5347\u5C31\u8DF3\u8FC7\u3002\n"
                        + "- \u52A0\u5361\u4F1A\u964D\u4F4E\u62BD\u5230\u5173\u952E\u5361\u7684\u9891\u7387\uFF0C\u4F18\u5148\u5361\u7EC4\u534F\u540C\u4E0E\u89E3\u51B3\u5F53\u524D\u5F31\u70B9\u3002\n";
            case "SHOP":
                return "- \u5546\u5E97\u5220\u724C\u521D\u59CB\u0037\u0035\u91D1\u5E01\uFF0C\u6BCF\u6B21\u002B\u0032\u0035\u3002\n"
                        + "- Smiling Mask \u53EF\u5C06\u5220\u724C\u56FA\u5B9A\u4E3A\u0035\u0030\uFF0C\u90E8\u5206\u9057\u7269\u4F1A\u5F71\u54CD\u5546\u5E97\u4EF7\u683C\u3002\n"
                        + "- \u5220\u724C\u4F18\u5148\u8BC5\u5492\uFF0C\u7136\u540E\u8003\u8651\u5F31\u57FA\u7840\u724C\uFF08\u770B\u5361\u7EC4\u60C5\u51B5\uFF09\u3002\n"
                        + "- \u82E5\u5220\u724C\uFF0C\u5FC5\u987B\u4ECE shop.purge_candidates \u4E2D\u9009\u62E9\u3002\n";
            case "BOSS_RELIC":
                return "- Boss \u9057\u7269\u5F71\u54CD\u6784\u7B51\u65B9\u5F0F\uFF0C\u9700\u7ED3\u5408\u5361\u7EC4\u4E0E\u4EE3\u4EF7\u51B3\u7B56\u3002\n";
            case "REST":
                return "- \u751F\u5B58\u98CE\u9669\u9AD8\u5219\u4F11\u606F\uFF0C\u8840\u91CF\u5B89\u5168\u5219\u4F18\u5148\u953B\u9020\u5173\u952E\u5361\u3002\n"
                        + "- \u82E5\u9009\u62E9\u953B\u9020\uFF0C\u4ECE rest.upgrade_options \u4E2D\u9009\u4E00\u5F20\u63D0\u5347\u6700\u5927\u7684\u5361\u3002\n";
            default:
                return "- \u4F18\u5148\u7EF4\u6301\u5361\u7EC4\u534F\u540C\uFF0C\u4E0D\u63D0\u5347\u5219\u8DF3\u8FC7\u3002\n";
        }
    }
}
