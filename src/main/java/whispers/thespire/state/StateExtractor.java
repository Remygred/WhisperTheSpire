package whispers.thespire.state;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoomBoss;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.rooms.TreasureRoom;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class StateExtractor {
    private StateExtractor() {}

    public static GameStateSnapshot extract(boolean includeCombat) {
        return extract(includeCombat, false);
    }

    public static GameStateSnapshot extract(boolean includeCombat, boolean includeFullMap) {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        String context = getScreenContext();
        snapshot.screen_context = context;

        AbstractPlayer player = AbstractDungeon.player;
        if (player != null) {
            GameStateSnapshot.Run run = new GameStateSnapshot.Run();
            run.act = AbstractDungeon.actNum;
            run.floor = AbstractDungeon.floorNum;
            run.ascension = AbstractDungeon.ascensionLevel;
            run.gold = player.gold;
            run.hp = player.currentHealth;
            run.maxHp = player.maxHealth;
            run.character = player.chosenClass == null ? null : player.chosenClass.name();
            run.seed = Settings.seed;
            snapshot.run = run;

            snapshot.deck_summary = extractDeck(player);
            snapshot.relics = extractRelics(player);
            snapshot.potions = extractPotions(player);
        }

        if ("NEOW".equals(context)) {
            snapshot.neow = extractNeow();
        }

        if ("BOSS_RELIC".equals(context)) {
            snapshot.boss_relic = extractBossRelic();
        }

        if ("REST".equals(context)) {
            snapshot.rest = extractRest();
        }

        if ("EVENT".equals(context)) {
            snapshot.event = extractEvent();
        }

        if ("MAP".equals(context)) {
            snapshot.map = extractMap();
        }

        if (includeFullMap) {
            snapshot.map_full = extractFullMap();
        }

        if ("CARD_REWARD".equals(context)) {
            snapshot.reward = extractReward();
        }

        if ("SHOP".equals(context)) {
            snapshot.shop = extractShop();
        }

        if (includeCombat && "COMBAT".equals(context)) {
            snapshot.combat = extractCombat();
        }

        return snapshot;
    }

    public static GameStateSnapshot extract() {
        return extract(false);
    }

    public static String computeMapHash() {
        if (AbstractDungeon.map == null || AbstractDungeon.map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < AbstractDungeon.map.size(); y++) {
            List<MapRoomNode> row = AbstractDungeon.map.get(y);
            if (row == null) {
                continue;
            }
            for (MapRoomNode node : row) {
                if (node == null) {
                    continue;
                }
                sb.append(y).append(',').append(node.x).append(',');
                sb.append(roomType(node.getRoom())).append('|');
                if (node.hasEdges()) {
                    for (MapEdge edge : node.getEdges()) {
                        sb.append(edge.dstX).append(',').append(edge.dstY).append(';');
                    }
                }
                sb.append('#');
            }
        }
        return sb.toString();
    }

    public static boolean hasMap() {
        return AbstractDungeon.map != null && !AbstractDungeon.map.isEmpty();
    }

    public static boolean isMapStart() {
        try {
            return AbstractDungeon.getCurrMapNode() == null || !AbstractDungeon.firstRoomChosen;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String buildLightweightKey() {
        String context = getScreenContext();
        int floor = AbstractDungeon.floorNum;
        String roomName = "null";
        try {
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            roomName = room == null ? "null" : room.getClass().getSimpleName();
        } catch (Exception ignored) {
            roomName = "null";
        }
        int hp = AbstractDungeon.player == null ? -1 : AbstractDungeon.player.currentHealth;
        int gold = AbstractDungeon.player == null ? -1 : AbstractDungeon.player.gold;
        MapRoomNode node = AbstractDungeon.getCurrMapNode();
        String nodeKey = node == null ? "" : (node.x + "," + node.y);
        String base = context + "|" + floor + "|" + roomName + "|" + nodeKey + "|" + hp + "|" + gold;
        if ("COMBAT".equals(context)) {
            int turn = com.megacrit.cardcrawl.actions.GameActionManager.turn;
            int handSize = (AbstractDungeon.player != null && AbstractDungeon.player.hand != null)
                    ? AbstractDungeon.player.hand.size() : -1;
            int energy = (AbstractDungeon.player != null && AbstractDungeon.player.energy != null)
                    ? AbstractDungeon.player.energy.energy : -1;
            String intentSig = buildIntentSignature();
            return base + "|turn=" + turn + "|hand=" + handSize + "|energy=" + energy + "|intent=" + intentSig;
        }
        if ("EVENT".equals(context)) {
            String eventSig = buildEventSignature();
            return base + "|event=" + eventSig;
        }
        return base;
    }

    private static String buildIntentSignature() {
        try {
            if (AbstractDungeon.getMonsters() == null || AbstractDungeon.getMonsters().monsters == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (com.megacrit.cardcrawl.monsters.AbstractMonster monster : AbstractDungeon.getMonsters().monsters) {
                if (monster == null) {
                    continue;
                }
                sb.append(monster.id == null ? "" : monster.id);
                sb.append(":");
                sb.append(monster.intent == null ? "none" : monster.intent.name());
                int intentDmg = monster.getIntentDmg();
                if (intentDmg >= 0) {
                    sb.append("#").append(intentDmg);
                }
                Integer multiAmt = readMonsterInt(monster, "intentMultiAmt");
                if (multiAmt != null && multiAmt > 1) {
                    sb.append("x").append(multiAmt);
                }
                sb.append("|");
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String buildEventSignature() {
        try {
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (!(room instanceof EventRoom)) {
                return "";
            }
            AbstractEvent event = readEvent(room);
            if (event == null) {
                return "event_null";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(event.getClass().getSimpleName()).append("|");
            List<String> options = readEventOptions(event);
            if (options != null) {
                for (String opt : options) {
                    if (opt == null) {
                        continue;
                    }
                    sb.append(opt).append("#");
                }
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String getScreenContext() {
        try {
            if (isMapScreenActive()) {
                return "MAP";
            }
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room instanceof NeowRoom) {
                return "NEOW";
            }
            if (room instanceof TreasureRoomBoss) {
                if (AbstractDungeon.bossRelicScreen != null
                        && AbstractDungeon.bossRelicScreen.relics != null
                        && !AbstractDungeon.bossRelicScreen.relics.isEmpty()) {
                    return "BOSS_RELIC";
                }
            }
            if (room instanceof RestRoom) {
                return "REST";
            }
            if (room != null && room.phase == AbstractRoom.RoomPhase.COMBAT) {
                return "COMBAT";
            }
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
                return "CARD_REWARD";
            }
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.SHOP) {
                return "SHOP";
            }
            if (room instanceof EventRoom) {
                return "EVENT";
            }
        } catch (Exception ignored) {
            // Fallback to OTHER
        }
        return "OTHER";
    }

    private static boolean isMapScreenActive() {
        try {
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
                return true;
            }
            if (AbstractDungeon.dungeonMapScreen == null) {
                return false;
            }
            // When returning to the map, screen may be NONE while the map UI is already fading in.
            if (AbstractDungeon.isScreenUp
                    && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.NONE
                    && AbstractDungeon.dungeonMapScreen.map != null
                    && AbstractDungeon.dungeonMapScreen.map.targetAlpha > 0.01f) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static List<GameStateSnapshot.CardInfo> extractDeck(AbstractPlayer player) {
        List<GameStateSnapshot.CardInfo> deck = new ArrayList<>();
        if (player.masterDeck == null || player.masterDeck.group == null) {
            return deck;
        }
        for (AbstractCard card : player.masterDeck.group) {
            if (card == null) {
                continue;
            }
            GameStateSnapshot.CardInfo info = new GameStateSnapshot.CardInfo();
            info.card_id = card.cardID;
            info.name = card.name;
            info.upgraded = card.timesUpgraded > 0;
            info.cost = card.cost;
            info.type = card.type == null ? null : card.type.toString();
            info.rarity = card.rarity == null ? null : card.rarity.toString();
            deck.add(info);
        }
        return deck;
    }

    private static List<GameStateSnapshot.RelicInfo> extractRelics(AbstractPlayer player) {
        List<GameStateSnapshot.RelicInfo> relics = new ArrayList<>();
        if (player.relics == null) {
            return relics;
        }
        for (AbstractRelic relic : player.relics) {
            if (relic == null) {
                continue;
            }
            GameStateSnapshot.RelicInfo info = new GameStateSnapshot.RelicInfo();
            info.relic_id = relic.relicId;
            info.name = relic.name;
            relics.add(info);
        }
        return relics;
    }

    private static List<GameStateSnapshot.PotionInfo> extractPotions(AbstractPlayer player) {
        List<GameStateSnapshot.PotionInfo> potions = new ArrayList<>();
        if (player.potions == null) {
            return potions;
        }
        for (AbstractPotion potion : player.potions) {
            if (potion == null || potion.ID == null) {
                continue;
            }
            if ("Potion Slot".equals(potion.ID)) {
                continue;
            }
            GameStateSnapshot.PotionInfo info = new GameStateSnapshot.PotionInfo();
            info.potion_id = potion.ID;
            info.name = potion.name;
            potions.add(info);
        }
        return potions;
    }

    private static GameStateSnapshot.MapInfo extractMap() {
        MapRoomNode curr = AbstractDungeon.getCurrMapNode();
        if (curr == null) {
            return null;
        }
        GameStateSnapshot.MapInfo map = new GameStateSnapshot.MapInfo();
        map.curr_x = curr.x;
        map.curr_y = curr.y;
        map.curr_type = roomType(curr.getRoom());
        map.next_nodes = new ArrayList<>();

        if (curr.hasEdges()) {
            for (MapEdge edge : curr.getEdges()) {
                MapRoomNode node = findNode(edge.dstX, edge.dstY);
                String type = node == null ? null : roomType(node.getRoom());
                map.next_nodes.add(new GameStateSnapshot.NodeInfo(edge.dstX, edge.dstY, type));
            }
        } else if (AbstractDungeon.map != null && curr.y + 1 < AbstractDungeon.map.size()) {
            List<MapRoomNode> nextRow = AbstractDungeon.map.get(curr.y + 1);
            if (nextRow != null) {
                for (MapRoomNode node : nextRow) {
                    if (node != null) {
                        map.next_nodes.add(new GameStateSnapshot.NodeInfo(node.x, node.y, roomType(node.getRoom())));
                    }
                }
            }
        }
        return map;
    }

    private static GameStateSnapshot.MapFullInfo extractFullMap() {
        if (AbstractDungeon.map == null || AbstractDungeon.map.isEmpty()) {
            return null;
        }
        GameStateSnapshot.MapFullInfo full = new GameStateSnapshot.MapFullInfo();
        full.rows = new ArrayList<>();
        for (int y = 0; y < AbstractDungeon.map.size(); y++) {
            List<MapRoomNode> row = AbstractDungeon.map.get(y);
            if (row == null) {
                continue;
            }
            GameStateSnapshot.MapRow mapRow = new GameStateSnapshot.MapRow();
            mapRow.y = y;
            mapRow.nodes = new ArrayList<>();
            for (MapRoomNode node : row) {
                if (node == null) {
                    continue;
                }
                GameStateSnapshot.MapNode mapNode = new GameStateSnapshot.MapNode();
                mapNode.x = node.x;
                mapNode.y = node.y;
                mapNode.room_type = roomType(node.getRoom());
                mapNode.next = new ArrayList<>();
                if (node.hasEdges()) {
                    for (MapEdge edge : node.getEdges()) {
                        mapNode.next.add(new GameStateSnapshot.NodeInfo(edge.dstX, edge.dstY));
                    }
                }
                mapRow.nodes.add(mapNode);
            }
            full.rows.add(mapRow);
        }
        return full;
    }

    private static MapRoomNode findNode(int x, int y) {
        if (AbstractDungeon.map == null || y < 0 || y >= AbstractDungeon.map.size()) {
            return null;
        }
        List<MapRoomNode> row = AbstractDungeon.map.get(y);
        if (row == null) {
            return null;
        }
        for (MapRoomNode node : row) {
            if (node != null && node.x == x && node.y == y) {
                return node;
            }
        }
        return null;
    }

    private static String roomType(AbstractRoom room) {
        if (room == null) {
            return null;
        }
        if (room instanceof MonsterRoomElite) {
            return "ELITE";
        }
        if (room instanceof MonsterRoomBoss) {
            return "BOSS";
        }
        if (room instanceof MonsterRoom) {
            return "MONSTER";
        }
        if (room instanceof RestRoom) {
            return "REST";
        }
        if (room instanceof ShopRoom) {
            return "SHOP";
        }
        if (room instanceof TreasureRoomBoss) {
            return "BOSS_TREASURE";
        }
        if (room instanceof TreasureRoom) {
            return "TREASURE";
        }
        if (room instanceof EventRoom) {
            return "EVENT";
        }
        return room.getClass().getSimpleName();
    }

    private static GameStateSnapshot.RewardInfo extractReward() {
        CardRewardScreen screen = AbstractDungeon.cardRewardScreen;
        if (screen == null) {
            return null;
        }
        GameStateSnapshot.RewardInfo reward = new GameStateSnapshot.RewardInfo();
        reward.choices = new ArrayList<>();
        if (screen.rewardGroup != null) {
            for (AbstractCard card : screen.rewardGroup) {
                if (card == null) {
                    continue;
                }
                GameStateSnapshot.CardInfo info = new GameStateSnapshot.CardInfo();
                info.card_id = card.cardID;
                info.name = card.name;
                info.upgraded = card.timesUpgraded > 0;
                info.cost = card.cost;
                info.type = card.type == null ? null : card.type.toString();
                info.rarity = card.rarity == null ? null : card.rarity.toString();
                reward.choices.add(info);
            }
        }

        reward.canSkip = readSkippable(screen);
        return reward;
    }

    private static GameStateSnapshot.BossRelicInfo extractBossRelic() {
        if (AbstractDungeon.bossRelicScreen == null || AbstractDungeon.bossRelicScreen.relics == null) {
            return null;
        }
        GameStateSnapshot.BossRelicInfo info = new GameStateSnapshot.BossRelicInfo();
        info.choices = new ArrayList<>();
        for (AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            if (relic == null) {
                continue;
            }
            GameStateSnapshot.RelicInfo rinfo = new GameStateSnapshot.RelicInfo();
            rinfo.relic_id = relic.relicId;
            rinfo.name = relic.name;
            info.choices.add(rinfo);
        }
        info.canSkip = true;
        return info;
    }

    private static GameStateSnapshot.RestInfo extractRest() {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (!(room instanceof RestRoom)) {
            return null;
        }
        GameStateSnapshot.RestInfo info = new GameStateSnapshot.RestInfo();
        info.options = new ArrayList<>();
        info.upgrade_options = new ArrayList<>();
        try {
            Field campfireField = RestRoom.class.getDeclaredField("campfireUI");
            campfireField.setAccessible(true);
            Object campfire = campfireField.get(room);
            if (campfire != null) {
                Field buttonsField = campfire.getClass().getDeclaredField("buttons");
                buttonsField.setAccessible(true);
                Object value = buttonsField.get(campfire);
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<AbstractCampfireOption> buttons = (List<AbstractCampfireOption>) value;
                    for (AbstractCampfireOption option : buttons) {
                        if (option == null) {
                            continue;
                        }
                        String label = readCampfireLabel(option);
                        if (label == null || label.isEmpty()) {
                            label = option.getClass().getSimpleName();
                        }
                        info.options.add(label);
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }

        try {
            AbstractPlayer player = AbstractDungeon.player;
            if (player != null && player.masterDeck != null) {
                com.megacrit.cardcrawl.cards.CardGroup upgradable = player.masterDeck.getUpgradableCards();
                if (upgradable != null && upgradable.group != null) {
                    for (AbstractCard card : upgradable.group) {
                        if (card == null) {
                            continue;
                        }
                        GameStateSnapshot.CardInfo infoCard = new GameStateSnapshot.CardInfo();
                        infoCard.card_id = card.cardID;
                        infoCard.name = card.name;
                        infoCard.upgraded = card.timesUpgraded > 0;
                        infoCard.cost = card.cost;
                        infoCard.type = card.type == null ? null : card.type.toString();
                        infoCard.rarity = card.rarity == null ? null : card.rarity.toString();
                        info.upgrade_options.add(infoCard);
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return info;
    }

    private static GameStateSnapshot.EventInfo extractEvent() {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (!(room instanceof EventRoom)) {
            return null;
        }
        AbstractEvent event = readEvent(room);
        GameStateSnapshot.EventInfo info = new GameStateSnapshot.EventInfo();
        if (event == null) {
            info.event_id = "UnknownEvent";
            info.event_name = "Unknown Event";
            info.options = new ArrayList<>();
            return info;
        }
        info.event_id = event.getClass().getSimpleName();
        try {
            Field nameField = AbstractEvent.class.getDeclaredField("name");
            nameField.setAccessible(true);
            Object value = nameField.get(event);
            info.event_name = value == null ? null : value.toString();
        } catch (Exception ignored) {
            info.event_name = null;
        }
        if (info.event_name == null || info.event_name.trim().isEmpty()) {
            info.event_name = info.event_id;
        }
        info.options = readEventOptions(event);
        return info;
    }

    private static AbstractEvent readEvent(AbstractRoom room) {
        if (room == null) {
            return null;
        }
        try {
            Field eventField = findField(room.getClass(), "event");
            if (eventField != null) {
                eventField.setAccessible(true);
                Object value = eventField.get(room);
                if (value instanceof AbstractEvent) {
                    return (AbstractEvent) value;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            Field eventField = EventRoom.class.getDeclaredField("event");
            eventField.setAccessible(true);
            Object value = eventField.get(room);
            if (value instanceof AbstractEvent) {
                return (AbstractEvent) value;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static List<String> readEventOptions(AbstractEvent event) {
        List<String> options = new ArrayList<>();
        if (event == null) {
            return options;
        }
        Object text = null;
        try {
            Field imageField = event.getClass().getDeclaredField("imageEventText");
            imageField.setAccessible(true);
            text = imageField.get(event);
        } catch (Exception ignored) {
            text = null;
        }
        if (text == null) {
            try {
                Field imageField = AbstractEvent.class.getDeclaredField("imageEventText");
                imageField.setAccessible(true);
                text = imageField.get(event);
            } catch (Exception ignored) {
                text = null;
            }
        }
        if (text == null) {
            try {
                Field roomField = event.getClass().getDeclaredField("roomEventText");
                roomField.setAccessible(true);
                text = roomField.get(event);
            } catch (Exception ignored) {
                text = null;
            }
        }
        if (text == null) {
            try {
                Field roomField = AbstractEvent.class.getDeclaredField("roomEventText");
                roomField.setAccessible(true);
                text = roomField.get(event);
            } catch (Exception ignored) {
                text = null;
            }
        }
        if (text == null) {
            return options;
        }
        Object list = null;
        try {
            Field optionsField = text.getClass().getDeclaredField("optionList");
            optionsField.setAccessible(true);
            list = optionsField.get(text);
        } catch (Exception ignored) {
            list = null;
        }
        if (list == null) {
            try {
                Field optionsField = text.getClass().getDeclaredField("options");
                optionsField.setAccessible(true);
                list = optionsField.get(text);
            } catch (Exception ignored) {
                list = null;
            }
        }
        if (!(list instanceof List)) {
            return fallbackEventOptions(event, options);
        }
        @SuppressWarnings("unchecked")
        List<Object> optionList = (List<Object>) list;
        for (Object opt : optionList) {
            if (opt == null) {
                continue;
            }
            String label = readOptionText(opt);
            if (label != null && !label.isEmpty()) {
                options.add(label);
            }
        }
        if (options.isEmpty()) {
            return fallbackEventOptions(event, options);
        }
        return options;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String readOptionText(Object option) {
        if (option == null) {
            return null;
        }
        String[] fields = new String[]{"msg", "text", "label"};
        for (String fieldName : fields) {
            try {
                Field field = option.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(option);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return option.toString();
    }

    private static String readCampfireLabel(AbstractCampfireOption option) {
        try {
            Field labelField = AbstractCampfireOption.class.getDeclaredField("label");
            labelField.setAccessible(true);
            Object value = labelField.get(option);
            return value == null ? null : value.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static GameStateSnapshot.NeowInfo extractNeow() {
        NeowEvent event = findNeowEvent();
        if (event == null) {
            return null;
        }
        GameStateSnapshot.NeowInfo info = new GameStateSnapshot.NeowInfo();
        info.options = new ArrayList<>();
        List<String> visible = readEventOptions(event);
        List<NeowReward> rewards = readNeowRewards(event);
        if (visible != null && !visible.isEmpty()) {
            for (String label : visible) {
                if (label == null || label.trim().isEmpty()) {
                    continue;
                }
                GameStateSnapshot.NeowOption option = new GameStateSnapshot.NeowOption();
                option.label = label;
                NeowReward reward = matchReward(label, rewards);
                if (reward != null) {
                    option.reward_type = reward.type == null ? null : reward.type.toString();
                    option.drawback = reward.drawback == null ? null : reward.drawback.toString();
                }
                info.options.add(option);
            }
        } else if (rewards != null) {
            for (NeowReward reward : rewards) {
                if (reward == null) {
                    continue;
                }
                GameStateSnapshot.NeowOption option = new GameStateSnapshot.NeowOption();
                option.label = reward.optionLabel;
                option.reward_type = reward.type == null ? null : reward.type.toString();
                option.drawback = reward.drawback == null ? null : reward.drawback.toString();
                info.options.add(option);
            }
        }
        return info;
    }

    private static List<NeowReward> readNeowRewards(NeowEvent event) {
        if (event == null) {
            return null;
        }
        try {
            Field rewardsField = NeowEvent.class.getDeclaredField("rewards");
            rewardsField.setAccessible(true);
            Object value = rewardsField.get(event);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<NeowReward> rewards = (List<NeowReward>) value;
                return rewards;
            }
        } catch (Exception ignored) {
            // best-effort only
        }
        return null;
    }

    private static List<String> fallbackEventOptions(AbstractEvent event, List<String> options) {
        if (event == null) {
            return options;
        }
        try {
            java.util.ArrayList<?> btns = event.imageEventText != null ? event.imageEventText.optionList : null;
            if (btns != null) {
                for (Object btn : btns) {
                    String label = readOptionText(btn);
                    if (label != null && !label.isEmpty()) {
                        options.add(label);
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (!options.isEmpty()) {
            return options;
        }
        try {
            java.util.ArrayList<?> btns = event.roomEventText != null ? event.roomEventText.optionList : null;
            if (btns != null) {
                for (Object btn : btns) {
                    String label = readOptionText(btn);
                    if (label != null && !label.isEmpty()) {
                        options.add(label);
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return options;
    }

    private static NeowReward matchReward(String label, List<NeowReward> rewards) {
        if (label == null || rewards == null) {
            return null;
        }
        String normalizedLabel = normalizeOption(label);
        for (NeowReward reward : rewards) {
            if (reward == null || reward.optionLabel == null) {
                continue;
            }
            String normalizedReward = normalizeOption(reward.optionLabel);
            if (normalizedLabel.equals(normalizedReward)
                    || normalizedLabel.contains(normalizedReward)
                    || normalizedReward.contains(normalizedLabel)) {
                return reward;
            }
        }
        return null;
    }

    private static String normalizeOption(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("#.", "");
        normalized = normalized.replaceAll("\\s+", "");
        return normalized.toLowerCase();
    }

    private static NeowEvent findNeowEvent() {
        try {
            Field field = AbstractDungeon.class.getDeclaredField("neowEvent");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof NeowEvent) {
                return (NeowEvent) value;
            }
        } catch (Exception ignored) {
            // fall through
        }
        try {
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room != null) {
                Field[] fields = room.getClass().getDeclaredFields();
                for (Field f : fields) {
                    if (NeowEvent.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object value = f.get(room);
                        if (value instanceof NeowEvent) {
                            return (NeowEvent) value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static GameStateSnapshot.ShopInfo extractShop() {
        ShopScreen shop = AbstractDungeon.shopScreen;
        if (shop == null) {
            return null;
        }
        GameStateSnapshot.ShopInfo info = new GameStateSnapshot.ShopInfo();
        info.cards = new ArrayList<>();
        info.relics = new ArrayList<>();
        info.potions = new ArrayList<>();

        if (shop.coloredCards != null) {
            for (AbstractCard card : shop.coloredCards) {
                GameStateSnapshot.ShopItem item = toShopCard(card);
                if (item != null) {
                    info.cards.add(item);
                }
            }
        }
        if (shop.colorlessCards != null) {
            for (AbstractCard card : shop.colorlessCards) {
                GameStateSnapshot.ShopItem item = toShopCard(card);
                if (item != null) {
                    info.cards.add(item);
                }
            }
        }

        try {
            Field relicsField = ShopScreen.class.getDeclaredField("relics");
            relicsField.setAccessible(true);
            Object value = relicsField.get(shop);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<StoreRelic> relics = (List<StoreRelic>) value;
                for (StoreRelic store : relics) {
                    if (store == null || store.relic == null) {
                        continue;
                    }
                    GameStateSnapshot.ShopItem item = new GameStateSnapshot.ShopItem();
                    item.item_type = "relic";
                    item.id = store.relic.relicId;
                    item.name = store.relic.name;
                    item.price = store.price;
                    info.relics.add(item);
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }

        try {
            Field potionsField = ShopScreen.class.getDeclaredField("potions");
            potionsField.setAccessible(true);
            Object value = potionsField.get(shop);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<StorePotion> potions = (List<StorePotion>) value;
                for (StorePotion store : potions) {
                    if (store == null || store.potion == null) {
                        continue;
                    }
                    GameStateSnapshot.ShopItem item = new GameStateSnapshot.ShopItem();
                    item.item_type = "potion";
                    item.id = store.potion.ID;
                    item.name = store.potion.name;
                    item.price = store.price;
                    info.potions.add(item);
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }

        info.purge_available = shop.purgeAvailable;
        int purgeCost = ShopScreen.actualPurgeCost > 0 ? ShopScreen.actualPurgeCost : ShopScreen.purgeCost;
        info.purge_cost = purgeCost;
        info.purge_candidates = extractPurgeCandidates();
        return info;
    }

    private static List<GameStateSnapshot.CardInfo> extractPurgeCandidates() {
        List<GameStateSnapshot.CardInfo> cards = new ArrayList<>();
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null || player.masterDeck == null) {
            return cards;
        }
        try {
            com.megacrit.cardcrawl.cards.CardGroup purgeable = player.masterDeck.getPurgeableCards();
            if (purgeable != null && purgeable.group != null) {
                for (AbstractCard card : purgeable.group) {
                    if (card == null) {
                        continue;
                    }
                    GameStateSnapshot.CardInfo info = new GameStateSnapshot.CardInfo();
                    info.card_id = card.cardID;
                    info.name = card.name;
                    info.upgraded = card.timesUpgraded > 0;
                    info.cost = card.cost;
                    info.type = card.type == null ? null : card.type.toString();
                    info.rarity = card.rarity == null ? null : card.rarity.toString();
                    cards.add(info);
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return cards;
    }

    private static GameStateSnapshot.ShopItem toShopCard(AbstractCard card) {
        if (card == null) {
            return null;
        }
        GameStateSnapshot.ShopItem item = new GameStateSnapshot.ShopItem();
        item.item_type = "card";
        item.id = card.cardID;
        item.name = card.name;
        item.price = card.price;
        return item;
    }

    private static GameStateSnapshot.CombatInfo extractCombat() {
        GameStateSnapshot.CombatInfo combat = new GameStateSnapshot.CombatInfo();
        if (AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
            return combat;
        }

        combat.turn = com.megacrit.cardcrawl.actions.GameActionManager.turn;
        combat.energy = AbstractDungeon.player.energy == null ? null : AbstractDungeon.player.energy.energy;
        combat.player_block = AbstractDungeon.player.currentBlock;

        combat.player_powers = new ArrayList<>();
        if (AbstractDungeon.player.powers != null) {
            for (int i = 0; i < AbstractDungeon.player.powers.size(); i++) {
                if (AbstractDungeon.player.powers.get(i) == null) {
                    continue;
                }
                GameStateSnapshot.PowerInfo power = new GameStateSnapshot.PowerInfo();
                power.id = AbstractDungeon.player.powers.get(i).ID;
                power.amount = AbstractDungeon.player.powers.get(i).amount;
                combat.player_powers.add(power);
            }
        }

        combat.hand = new ArrayList<>();
        if (AbstractDungeon.player.hand != null && AbstractDungeon.player.hand.group != null) {
            for (AbstractCard card : AbstractDungeon.player.hand.group) {
                if (card == null) {
                    continue;
                }
                GameStateSnapshot.CombatCardInfo info = new GameStateSnapshot.CombatCardInfo();
                info.card_id = card.cardID;
                info.name = card.name;
                info.cost = card.costForTurn;
                info.upgraded = card.timesUpgraded > 0;
                info.type = card.type == null ? null : card.type.toString();
                combat.hand.add(info);
            }
        }

        combat.draw_pile_size = AbstractDungeon.player.drawPile == null ? null : AbstractDungeon.player.drawPile.size();
        combat.discard_pile_size = AbstractDungeon.player.discardPile == null ? null : AbstractDungeon.player.discardPile.size();
        combat.exhaust_pile_size = AbstractDungeon.player.exhaustPile == null ? null : AbstractDungeon.player.exhaustPile.size();

        combat.monsters = new ArrayList<>();
        if (AbstractDungeon.getMonsters() != null && AbstractDungeon.getMonsters().monsters != null) {
            for (int i = 0; i < AbstractDungeon.getMonsters().monsters.size(); i++) {
                com.megacrit.cardcrawl.monsters.AbstractMonster monster = AbstractDungeon.getMonsters().monsters.get(i);
                if (monster == null) {
                    continue;
                }
                GameStateSnapshot.MonsterInfo info = new GameStateSnapshot.MonsterInfo();
                info.id = monster.id;
                info.name = monster.name;
                info.hp = monster.currentHealth;
                info.maxHp = monster.maxHealth;
                info.block = monster.currentBlock;
                String intentName = monster.intent == null ? "" : monster.intent.name();
                if ("DEBUG".equals(intentName)) {
                    intentName = "unknown";
                }
                info.intent = intentName.isEmpty() ? "unknown" : intentName;
                info.move_name = monster.moveName;
                int intentDmg = monster.getIntentDmg();
                int intentBase = monster.getIntentBaseDmg();
                info.intent_dmg = intentDmg >= 0 ? intentDmg : null;
                info.intent_base_dmg = intentBase >= 0 ? intentBase : null;
                Boolean isMulti = readMonsterBool(monster, "isMultiDmg");
                Integer multiAmt = readMonsterInt(monster, "intentMultiAmt");
                if (Boolean.TRUE.equals(isMulti) && multiAmt != null && multiAmt > 1) {
                    info.intent_hits = multiAmt;
                } else if (intentDmg >= 0) {
                    info.intent_hits = 1;
                }
                info.intent_multi = isMulti;
                info.powers = new ArrayList<>();
                if (monster.powers != null) {
                    for (int p = 0; p < monster.powers.size(); p++) {
                        if (monster.powers.get(p) == null) {
                            continue;
                        }
                        GameStateSnapshot.PowerInfo pinfo = new GameStateSnapshot.PowerInfo();
                        pinfo.id = monster.powers.get(p).ID;
                        pinfo.amount = monster.powers.get(p).amount;
                        info.powers.add(pinfo);
                    }
                }
                combat.monsters.add(info);
            }
        }
        return combat;
    }

    private static Integer readMonsterInt(com.megacrit.cardcrawl.monsters.AbstractMonster monster, String fieldName) {
        if (monster == null || fieldName == null) {
            return null;
        }
        try {
            Field field = com.megacrit.cardcrawl.monsters.AbstractMonster.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(monster);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    private static Boolean readMonsterBool(com.megacrit.cardcrawl.monsters.AbstractMonster monster, String fieldName) {
        if (monster == null || fieldName == null) {
            return null;
        }
        try {
            Field field = com.megacrit.cardcrawl.monsters.AbstractMonster.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(monster);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    private static Boolean readSkippable(CardRewardScreen screen) {
        try {
            Field field = CardRewardScreen.class.getDeclaredField("skippable");
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Exception ignored) {
            // return null when not accessible
        }
        return null;
    }
}
