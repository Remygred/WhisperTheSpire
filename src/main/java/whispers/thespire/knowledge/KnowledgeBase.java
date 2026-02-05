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

    public static String getCharacterNotes(String character) {
        if (character == null || character.trim().isEmpty()) {
            return "";
        }
        boolean zh = I18n.isZh();
        String key = character.trim().toUpperCase();
        if (zh) {
            return getCharacterNotesZh(key);
        }
        return getCharacterNotesEn(key);
    }

    private static String getNotesEn(String context) {
        switch (context) {
            case "NEOW":
                return "- Neow bonuses are tradeoffs; prefer options that increase immediate power or remove weak basics.\n"
                        + "- If you can path into an early elite, early power bonuses gain value.\n"
                        + "- Avoid high-risk drawbacks when HP is low or the deck is weak.\n";
            case "MAP":
            case "MAP_PATH":
                return "- Elites start appearing from floor 6.\n"
                        + "- Elites give relic + gold + card reward; higher ascensions increase elite danger.\n"
                        + "- Elite/Rest/Merchant rooms cannot be consecutive on the map.\n"
                        + "- Use map_current/map_next to compare node types; if HP is low, favor safer paths.\n"
                        + "- Early Act 1 often wants 1-2 elites if the deck can handle them.\n"
                        + "- If map_full is present, plan the whole act route (elite count, shops, rest stops) before choosing the next node.\n"
                        + "- Prefer paths that solve your current weakness (damage, block, scaling, or gold needs).\n";
            case "CARD_REWARD":
                return "- Card rewards can be skipped; skip when none improve your current plan.\n"
                        + "- Adding cards slows deck cycling, so prefer synergy and solving current weaknesses.\n"
                        + "- Early Act 1 favors damage and reliable block; later acts favor scaling and consistency.\n";
            case "SHOP":
                return "- Card removal costs 75 gold initially and increases by 25 each time.\n"
                        + "- Smiling Mask fixes removal cost at 50; some relics affect shop prices.\n"
                        + "- If purging, prefer curses first, then weak basic cards (context dependent).\n"
                        + "- Use shop.purge_candidates when recommending a removal target.\n"
                        + "- Prioritize high-impact relics or key cards; avoid overbuying filler.\n"
                        + "- Potions can be worth buying if an upcoming elite/boss is planned.\n";
            case "BOSS_RELIC":
                return "- Boss relic choices heavily shape the run; pick based on your deck and ability to handle the drawback.\n"
                        + "- Energy relics are strong but only if the downside does not break your plan.\n";
            case "REST":
                return "- Rest if survival is at risk; Smith if HP is safe and you have a high-impact upgrade.\n"
                        + "- If Smithing, choose a card from rest.upgrade_options that improves damage, scaling, or block consistency.\n"
                        + "- Upgrades that reduce cost or improve draw/energy are often high value.\n";
            case "COMBAT":
                return "- In combat, prioritize lethal when safe; otherwise block for large telegraphed hits.\n"
                        + "- Use current hand, energy, and enemy intents to decide play order.\n"
                        + "- Potions are resources; use them to survive or secure elite/boss wins.\n";
            default:
                return "- Prefer cohesive decks; skip options that do not improve the current plan.\n";
        }
    }

    private static String getCharacterNotesEn(String character) {
        switch (character) {
            case "IRONCLAD":
                return "- Ironclad: Strength scaling (Inflame/Spot Weakness) + multi-hit is strong.\n"
                        + "- Exhaust synergies (Corruption/Feel No Pain/Dark Embrace) are high value.\n"
                        + "- Burning Blood allows riskier elite paths if HP is stable.\n";
            case "THE_SILENT":
                return "- Silent: Poison scales vs high HP; Catalyst is a finisher when poison is online.\n"
                        + "- Shiv packages need Accuracy/Kunai/Shuriken or scaling.\n"
                        + "- Discard synergy (Tactician/Reflex/Prepared) improves consistency.\n";
            case "DEFECT":
                return "- Defect: Focus is key; Frost for defense, Dark for scaling damage.\n"
                        + "- Orb generation + evoke order matters; powers scale well (Defragment/Biased Cog).\n";
            case "WATCHER":
                return "- Watcher: Stance dancing is core; Wrath doubles damage, Calm gives energy.\n"
                        + "- Prioritize safe exits from Wrath and retain/scry to smooth hands.\n";
            default:
                return "";
        }
    }

    private static String getNotesZh(String context) {
        switch (context) {
            case "NEOW":
                return "- \u6D85\u5965\u795D\u798F\u5E38\u662F\u5956\u52B1+\u4EE3\u4EF7\u7684\u7EC4\u5408\uFF0C\u4F18\u5148\u63D0\u5347\u65E9\u671F\u6218\u529B\u6216\u5220\u5F31\u57FA\u7840\u724C\u3002\n"
                        + "- \u82E5\u80FD\u6253\u65E9\u671F\u7CBE\u82F1\uFF0C\u65E9\u671F\u6218\u529B\u5956\u52B1\u4EF7\u503C\u66F4\u9AD8\u3002\n"
                        + "- \u8840\u91CF\u4F4E/\u5361\u7EC4\u5F31\u65F6\u907F\u514D\u9AD8\u98CE\u9669\u4EE3\u4EF7\u3002\n";
            case "MAP":
            case "MAP_PATH":
                return "- \u7CBE\u82F1\u4ECE\u7B2C\u0036\u5C42\u5F00\u59CB\u51FA\u73B0\u3002\n"
                        + "- \u7CBE\u82F1\u6389\u843D\u9057\u7269+\u91D1\u5E01+\u5361\u724C\uFF0C\u9AD8\u5347\u9636\u7CBE\u82F1\u66F4\u5E38\u89C1/\u66F4\u5F3A\u3002\n"
                        + "- \u7CBE\u82F1/\u5546\u5E97/\u4F11\u606F\u4E0D\u53EF\u8FDE\u7EED\u76F8\u8FDE\u3002\n"
                        + "- \u7528 map_current / map_next \u9009\u8282\u70B9\uFF0C\u4F4E\u8840\u4F18\u5148\u5B89\u5168\u7EBF\u3002\n"
                        + "- Act1 \u65E9\u671F\u5E38\u8981 1-2 \u4E2A\u7CBE\u82F1\uFF08\u524D\u63D0\u5361\u7EC4\u80FD\u625B\u4F4F\uFF09\u3002\n"
                        + "- \u5982\u6709 map_full \uFF0C\u8BF7\u5148\u89C4\u5212\u6574\u5C42\u8DEF\u7EBF\uFF08\u7CBE\u82F1\u6570/\u5546\u5E97/\u4F11\u606F\u8282\u70B9\uFF09\u518D\u9009\u4E0B\u4E00\u4E2A\u8282\u70B9\u3002\n"
                        + "- \u9009\u8DEF\u8981\u89E3\u51B3\u5F53\u524D\u5F31\u70B9\uFF08\u8F93\u51FA/\u9632\u5FA1/\u7D2F\u79EF/\u91D1\u5E01\uFF09\u3002\n";
            case "CARD_REWARD":
                return "- \u5361\u724C\u5956\u52B1\u53EF\u4EE5\u8DF3\u8FC7\uFF0C\u6CA1\u6709\u63D0\u5347\u5C31\u8DF3\u8FC7\u3002\n"
                        + "- \u52A0\u5361\u4F1A\u964D\u4F4E\u62BD\u5230\u5173\u952E\u5361\u7684\u9891\u7387\uFF0C\u4F18\u5148\u5361\u7EC4\u534F\u540C\u4E0E\u89E3\u51B3\u5F53\u524D\u5F31\u70B9\u3002\n"
                        + "- Act1 \u4F18\u5148\u8865\u8F93\u51FA/\u7A33\u5B9A\u9632\u5FA1\uFF0C\u540E\u671F\u66F4\u5173\u6CE8\u7D2F\u79EF\u4E0E\u6301\u7EED\u80FD\u529B\u3002\n";
            case "SHOP":
                return "- \u5546\u5E97\u5220\u724C\u521D\u59CB\u0037\u0035\u91D1\u5E01\uFF0C\u6BCF\u6B21\u002B\u0032\u0035\u3002\n"
                        + "- Smiling Mask \u53EF\u5C06\u5220\u724C\u56FA\u5B9A\u4E3A\u0035\u0030\uFF0C\u90E8\u5206\u9057\u7269\u4F1A\u5F71\u54CD\u5546\u5E97\u4EF7\u683C\u3002\n"
                        + "- \u5220\u724C\u4F18\u5148\u8BC5\u5492\uFF0C\u7136\u540E\u8003\u8651\u5F31\u57FA\u7840\u724C\uFF08\u770B\u5361\u7EC4\u60C5\u51B5\uFF09\u3002\n"
                        + "- \u82E5\u5220\u724C\uFF0C\u5FC5\u987B\u4ECE shop.purge_candidates \u4E2D\u9009\u62E9\u3002\n"
                        + "- \u4F18\u5148\u9AD8\u4EF7\u503C\u9057\u7269\u6216\u5173\u952E\u5361\uFF0C\u907F\u514D\u4E70\u5165\u65E0\u7528\u5361\u587E\u5361\u7EC4\u3002\n"
                        + "- \u4E0B\u4E2A\u7CBE\u82F1/\u5934\u76EE\u5728\u5373\u65F6\u836F\u6C34\u53EF\u80FD\u503C\u5F97\u8D2D\u4E70\u3002\n";
            case "BOSS_RELIC":
                return "- Boss \u9057\u7269\u5F71\u54CD\u6784\u7B51\u65B9\u5F0F\uFF0C\u9700\u7ED3\u5408\u5361\u7EC4\u4E0E\u4EE3\u4EF7\u51B3\u7B56\u3002\n"
                        + "- \u80FD\u91CF\u9057\u7269\u5F88\u5F3A\uFF0C\u4F46\u5982\u679C\u4EE3\u4EF7\u4F1A\u7834\u574F\u5361\u7EC4\u8282\u594F\uFF0C\u9700\u8C28\u614E\u3002\n";
            case "REST":
                return "- \u751F\u5B58\u98CE\u9669\u9AD8\u5219\u4F11\u606F\uFF0C\u8840\u91CF\u5B89\u5168\u5219\u4F18\u5148\u953B\u9020\u5173\u952E\u5361\u3002\n"
                        + "- \u82E5\u9009\u62E9\u953B\u9020\uFF0C\u4ECE rest.upgrade_options \u4E2D\u9009\u4E00\u5F20\u63D0\u5347\u6700\u5927\u7684\u5361\u3002\n"
                        + "- \u964D\u8D39\u6216\u5F3A\u5316\u62BD\u724C/\u80FD\u91CF\u7684\u5347\u7EA7\u5F88\u9AD8\u4EF7\u503C\u3002\n";
            case "COMBAT":
                return "- \u6218\u6597\u4E2D\uFF0C\u5B89\u5168\u65F6\u4F18\u5148\u6253\u51FA\u81F4\u6B7B\uFF0C\u5426\u5219\u63D0\u9AD8\u9632\u5FA1\u5E94\u5BF9\u5927\u4F24\u5BB3\u3002\n"
                        + "- \u6839\u636E\u5F53\u524D\u624B\u724C/\u80FD\u91CF/\u654C\u4EBA\u610F\u56FE\u8BBE\u8BA1\u51FA\u724C\u987A\u5E8F\u3002\n"
                        + "- \u836F\u6C34\u662F\u8D44\u6E90\uFF0C\u9700\u7528\u6765\u4FDD\u547D\u6216\u786E\u4FDD\u7CBE\u82F1/\u5934\u76EE\u80DC\u5229\u3002\n";
            default:
                return "- \u4F18\u5148\u7EF4\u6301\u5361\u7EC4\u534F\u540C\uFF0C\u4E0D\u63D0\u5347\u5219\u8DF3\u8FC7\u3002\n";
        }
    }

    
    private static String getCharacterNotesZh(String character) {
        switch (character) {
            case "IRONCLAD":
                return "- \u94c1\u7537\uff1a\u529b\u91cf\u6210\u957f\uff08Inflame/Spot Weakness\uff09+ \u591a\u6bb5\u653b\u51fb\u5f88\u5f3a\u3002\n"
                        + "- \u6d88\u8017\u6d41\uff08Corruption/Feel No Pain/Dark Embrace\uff09\u4ef7\u503c\u5f88\u9ad8\u3002\n"
                        + "- \u7834\u788e\u4e4b\u5fc3\u56de\u8840\u8ba9\u4f60\u80fd\u627f\u62c5\u66f4\u6fc0\u8fdb\u7684\u7cbe\u82f1\u8def\u7ebf\u3002\n";
            case "THE_SILENT":
                return "- \u5bc2\u9759\uff1a\u6bd2\u5bf9\u9ad8\u8840\u91cf\u5f88\u5f3a\uff0c\u6210\u578b\u540e Catalyst \u53ef\u7ec8\u7ed3\u3002\n"
                        + "- \u98de\u5200\u6d41\u9700\u8981 Accuracy/Kunai/Shuriken \u6216\u6210\u957f\u652f\u6491\u3002\n"
                        + "- \u5f03\u724c\u4f53\u7cfb\uff08Tactician/Reflex/Prepared\uff09\u63d0\u9ad8\u7a33\u5b9a\u6027\u3002\n";
            case "DEFECT":
                return "- \u673a\u5668\u4eba\uff1a\u805a\u7126\u662f\u6838\u5fc3\uff0c\u971c\u7403\u4fdd\u547d\u3001\u6697\u7403\u6210\u957f\u8f93\u51fa\u3002\n"
                        + "- \u9020\u7403+\u5f15\u7206\u987a\u5e8f\u91cd\u8981\uff0c\u529b\u91cf\u7c7b\u80fd\u529b\uff08Defragment/Biased Cog\uff09\u6536\u76ca\u9ad8\u3002\n";
            case "WATCHER":
                return "- \u89c2\u8005\uff1a\u59ff\u6001\u5207\u6362\u662f\u6838\u5fc3\uff0c\u6124\u6012\u9ad8\u4f24\u5bb3\uff0c\u5e73\u9759\u7ed9\u80fd\u91cf\u3002\n"
                        + "- \u4f18\u5148\u4fdd\u8bc1\u5b89\u5168\u51fa\u6124\u6012\uff0c\u5e76\u7528\u4fdd\u7559/\u5360\u535c\u7a33\u5b9a\u624b\u724c\u3002\n";
            default:
                return "";
        }
    }

}
