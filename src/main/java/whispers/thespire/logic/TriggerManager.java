package whispers.thespire.logic;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import whispers.thespire.config.ModConfig;
import whispers.thespire.state.StateExtractor;

import java.util.ArrayList;
import java.util.List;

public class TriggerManager {
    private static String lastScreenContext = "";
    private static Integer lastNodeX = null;
    private static Integer lastNodeY = null;
    private static String lastRewardSignature = "";
    private static int lastCombatTurn = -1;
    private static boolean lastCombatWaiting = false;

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
                if (!lastCombatWaiting || turn != lastCombatTurn) {
                    lastCombatTurn = turn;
                    lastCombatWaiting = true;
                    event = new TriggerEvent("COMBAT_TURN", "combat turn start: " + turn);
                }
            } else {
                lastCombatWaiting = false;
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
        lastCombatTurn = -1;
        lastCombatWaiting = false;
        pendingPotion = false;
        pendingPotionId = "";
        pendingPotionName = "";
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

    public static class TriggerEvent {
        public final String contextType;
        public final String reason;

        public TriggerEvent(String contextType, String reason) {
            this.contextType = contextType;
            this.reason = reason;
        }
    }
}
