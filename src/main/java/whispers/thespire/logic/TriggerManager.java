package whispers.thespire.logic;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import whispers.thespire.config.ModConfig;
import whispers.thespire.state.StateExtractor;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

public class TriggerManager {
    private static String lastScreenContext = "";
    private static Integer lastNodeX = null;
    private static Integer lastNodeY = null;
    private static String lastRewardSignature = "";
    private static String lastNeowSignature = "";
    private static String lastShopSignature = "";
    private static String lastBossRelicSignature = "";
    private static String lastRestSignature = "";
    private static int lastCombatTurn = -1;
    private static int pendingCombatTurn = -1;
    private static int pendingCombatFrames = 0;

    private static boolean pendingPotion = false;
    private static String pendingPotionId = "";
    private static String pendingPotionName = "";

    private TriggerManager() {}

    public static TriggerEvent pollEvent() {
        if (!isInRun()) {
            reset();
            return null;
        }

        String context = StateExtractor.getScreenContext();
        if (pendingPotion) {
            pendingPotion = false;
            String id = pendingPotionId == null ? "" : pendingPotionId;
            String name = pendingPotionName == null ? "" : pendingPotionName;
            lastScreenContext = context;
            return new TriggerEvent("POTION_OVERFLOW", "potion overflow: incoming=" + (id.isEmpty() ? name : id));
        }
        TriggerEvent event = null;

        if (ModConfig.autoTriggersEnabled && "NEOW".equals(context)) {
            String signature = neowSignature();
            if (!"NEOW".equals(lastScreenContext)) {
                event = new TriggerEvent("NEOW", "neow opened");
            } else if (signature != null && !signature.equals(lastNeowSignature)) {
                event = new TriggerEvent("NEOW", "neow options changed");
            }
            if (signature != null) {
                lastNeowSignature = signature;
            }
        }

        if (event == null && ModConfig.autoTriggersEnabled && "BOSS_RELIC".equals(context)) {
            String signature = bossRelicSignature();
            if (!"BOSS_RELIC".equals(lastScreenContext)) {
                event = new TriggerEvent("BOSS_RELIC", "boss relic opened");
            } else if (signature != null && !signature.equals(lastBossRelicSignature)) {
                event = new TriggerEvent("BOSS_RELIC", "boss relic choices changed");
            }
            if (signature != null) {
                lastBossRelicSignature = signature;
            }
        }

        if (event == null && ModConfig.autoTriggersEnabled && "REST".equals(context)) {
            String signature = restSignature();
            if (!"REST".equals(lastScreenContext)) {
                event = new TriggerEvent("REST", "rest site opened");
            } else if (signature != null && !signature.equals(lastRestSignature)) {
                event = new TriggerEvent("REST", "rest options changed");
            }
            if (signature != null) {
                lastRestSignature = signature;
            }
        }

        if (event == null && ModConfig.autoTriggersEnabled && "SHOP".equals(context)) {
            String signature = shopSignature();
            if (!"SHOP".equals(lastScreenContext)) {
                event = new TriggerEvent("SHOP", "shop opened");
            } else if (signature != null && !signature.equals(lastShopSignature)) {
                event = new TriggerEvent("SHOP", "shop items changed");
            }
            if (signature != null) {
                lastShopSignature = signature;
            }
        }

        if (ModConfig.autoTriggersEnabled && "MAP".equals(context)) {
            MapRoomNode node = AbstractDungeon.getCurrMapNode();
            if (!"MAP".equals(lastScreenContext)) {
                event = new TriggerEvent("MAP_PATH", "map entered");
            } else if (node != null && (lastNodeX == null || lastNodeY == null || node.x != lastNodeX || node.y != lastNodeY)) {
                event = new TriggerEvent("MAP_PATH", "map node changed");
            }
            if (node != null) {
                lastNodeX = node.x;
                lastNodeY = node.y;
            }
        }

        if (event == null && ModConfig.autoTriggersEnabled && "CARD_REWARD".equals(context)) {
            String signature = rewardSignature();
            if (!"CARD_REWARD".equals(lastScreenContext)) {
                event = new TriggerEvent("CARD_REWARD", "reward opened");
            } else if (signature != null && !signature.equals(lastRewardSignature)) {
                event = new TriggerEvent("CARD_REWARD", "reward choices changed");
            }
            if (signature != null) {
                lastRewardSignature = signature;
            }
        }

        if (event == null && ModConfig.combatAdviceEnabled && "COMBAT".equals(context)) {
            if (!AbstractDungeon.isScreenUp && isPlayerTurn()) {
                int turn = com.megacrit.cardcrawl.actions.GameActionManager.turn;
                if (turn != lastCombatTurn && pendingCombatTurn != turn) {
                    pendingCombatTurn = turn;
                    pendingCombatFrames = 0;
                }
                if (pendingCombatTurn == turn) {
                    pendingCombatFrames++;
                    boolean handReady = isHandReady(turn);
                    if (handReady || pendingCombatFrames >= 12) {
                        lastCombatTurn = turn;
                        pendingCombatTurn = -1;
                        pendingCombatFrames = 0;
                        event = new TriggerEvent("COMBAT_TURN", "combat turn start: " + turn);
                    }
                }
            } else {
                pendingCombatTurn = -1;
                pendingCombatFrames = 0;
            }
        }

        lastScreenContext = context;
        return event;
    }

    public static void notifyPotionOverflow(AbstractPotion potion) {
        if (!ModConfig.autoTriggersEnabled) {
            return;
        }
        if (!isInRun()) {
            return;
        }
        if (!isPotionsFull()) {
            return;
        }
        if (potion == null) {
            return;
        }
        String id = potion.ID == null ? "" : potion.ID;
        if (id.isEmpty()) {
            return;
        }
        pendingPotion = true;
        pendingPotionId = id;
        pendingPotionName = potion.name == null ? "" : potion.name;
    }

    private static boolean isPotionsFull() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.potions == null) {
            return false;
        }
        for (AbstractPotion potion : AbstractDungeon.player.potions) {
            if (potion == null || potion.ID == null) {
                return false;
            }
            if ("Potion Slot".equals(potion.ID)) {
                return false;
            }
        }
        return true;
    }

    private static String rewardSignature() {
        CardRewardScreen screen = AbstractDungeon.cardRewardScreen;
        if (screen == null || screen.rewardGroup == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < screen.rewardGroup.size(); i++) {
            if (screen.rewardGroup.get(i) == null) {
                continue;
            }
            sb.append(screen.rewardGroup.get(i).cardID);
            if (i < screen.rewardGroup.size() - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    private static boolean isInRun() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.player != null;
    }

    private static void reset() {
        lastScreenContext = "";
        lastNodeX = null;
        lastNodeY = null;
        lastRewardSignature = "";
        lastNeowSignature = "";
        lastShopSignature = "";
        lastBossRelicSignature = "";
        lastRestSignature = "";
        lastCombatTurn = -1;
        pendingCombatTurn = -1;
        pendingCombatFrames = 0;
        pendingPotion = false;
        pendingPotionId = "";
        pendingPotionName = "";
    }

    private static String neowSignature() {
        NeowEvent event = findNeowEvent();
        if (event == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try {
            Field rewardsField = NeowEvent.class.getDeclaredField("rewards");
            rewardsField.setAccessible(true);
            Object value = rewardsField.get(event);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<NeowReward> rewards = (List<NeowReward>) value;
                for (NeowReward reward : rewards) {
                    if (reward == null) {
                        continue;
                    }
                    sb.append(reward.optionLabel == null ? "" : reward.optionLabel);
                    sb.append('|');
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return sb.toString();
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
            if (room instanceof NeowRoom) {
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

    private static String shopSignature() {
        ShopScreen shop = AbstractDungeon.shopScreen;
        if (shop == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (shop.coloredCards != null) {
            for (int i = 0; i < shop.coloredCards.size(); i++) {
                if (shop.coloredCards.get(i) != null) {
                    sb.append(shop.coloredCards.get(i).cardID).append(',');
                }
            }
        }
        if (shop.colorlessCards != null) {
            for (int i = 0; i < shop.colorlessCards.size(); i++) {
                if (shop.colorlessCards.get(i) != null) {
                    sb.append(shop.colorlessCards.get(i).cardID).append(',');
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
                for (StoreRelic relic : relics) {
                    if (relic != null && relic.relic != null) {
                        sb.append(relic.relic.relicId).append('@').append(relic.price).append(',');
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            Field potionsField = ShopScreen.class.getDeclaredField("potions");
            potionsField.setAccessible(true);
            Object value = potionsField.get(shop);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<StorePotion> potions = (List<StorePotion>) value;
                for (StorePotion potion : potions) {
                    if (potion != null && potion.potion != null) {
                        sb.append(potion.potion.ID).append('@').append(potion.price).append(',');
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        sb.append("purge=").append(shop.purgeAvailable).append(':').append(ShopScreen.actualPurgeCost);
        return sb.toString();
    }

    private static String bossRelicSignature() {
        if (AbstractDungeon.bossRelicScreen == null || AbstractDungeon.bossRelicScreen.relics == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < AbstractDungeon.bossRelicScreen.relics.size(); i++) {
            if (AbstractDungeon.bossRelicScreen.relics.get(i) != null) {
                sb.append(AbstractDungeon.bossRelicScreen.relics.get(i).relicId).append(',');
            }
        }
        return sb.toString();
    }

    private static String restSignature() {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (!(room instanceof RestRoom)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
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
                        sb.append(option.getClass().getSimpleName()).append(',');
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            if (AbstractDungeon.player != null && AbstractDungeon.player.masterDeck != null) {
                com.megacrit.cardcrawl.cards.CardGroup upgradable = AbstractDungeon.player.masterDeck.getUpgradableCards();
                if (upgradable != null && upgradable.group != null) {
                    for (int i = 0; i < upgradable.group.size(); i++) {
                        if (upgradable.group.get(i) != null) {
                            sb.append(upgradable.group.get(i).cardID).append(',');
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return sb.toString();
    }

    private static boolean isPlayerTurn() {
        if (AbstractDungeon.actionManager == null) {
            return false;
        }
        if (AbstractDungeon.getCurrRoom() == null || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            return false;
        }
        return AbstractDungeon.actionManager.phase == com.megacrit.cardcrawl.actions.GameActionManager.Phase.WAITING_ON_USER
                && !AbstractDungeon.actionManager.turnHasEnded;
    }

    private static boolean isHandReady(int turn) {
        if (AbstractDungeon.player == null || AbstractDungeon.player.hand == null) {
            return false;
        }
        int handSize = AbstractDungeon.player.hand.size();
        if (handSize > 0) {
            return true;
        }
        // For the first turn, wait for a non-empty hand to avoid firing too early.
        if (turn <= 1) {
            return false;
        }
        // If the draw pile is empty, it is possible to have an empty hand by design.
        int drawSize = AbstractDungeon.player.drawPile == null ? 0 : AbstractDungeon.player.drawPile.size();
        int discardSize = AbstractDungeon.player.discardPile == null ? 0 : AbstractDungeon.player.discardPile.size();
        return drawSize == 0 && discardSize == 0;
    }

    public static class TriggerEvent {
        public final String contextType;
        public final String reason;

        public TriggerEvent(String contextType, String reason) {
            this.contextType = contextType;
            this.reason = reason;
        }
    }
}
