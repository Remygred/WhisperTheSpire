package whispers.thespire.state;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.potions.AbstractPotion;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class StateExtractor {
    private StateExtractor() {}

    public static GameStateSnapshot extract(boolean includeCombat) {
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
            snapshot.run = run;

            snapshot.deck_summary = extractDeck(player);
            snapshot.relics = extractRelics(player);
            snapshot.potions = extractPotions(player);
        }

        if ("MAP".equals(context)) {
            snapshot.map = extractMap();
        }

        if ("CARD_REWARD".equals(context)) {
            snapshot.reward = extractReward();
        }

        if (includeCombat && "COMBAT".equals(context)) {
            snapshot.combat = extractCombat();
        }

        return snapshot;
    }

    public static GameStateSnapshot extract() {
        return extract(false);
    }

    public static String buildLightweightKey() {
        String context = getScreenContext();
        int floor = AbstractDungeon.floorNum;
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        String roomName = room == null ? "null" : room.getClass().getSimpleName();
        int hp = AbstractDungeon.player == null ? -1 : AbstractDungeon.player.currentHealth;
        int gold = AbstractDungeon.player == null ? -1 : AbstractDungeon.player.gold;
        MapRoomNode node = AbstractDungeon.getCurrMapNode();
        String nodeKey = node == null ? "" : (node.x + "," + node.y);
        return context + "|" + floor + "|" + roomName + "|" + nodeKey + "|" + hp + "|" + gold;
    }

    public static String getScreenContext() {
        try {
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room != null && room.phase == AbstractRoom.RoomPhase.COMBAT) {
                return "COMBAT";
            }
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
                return "MAP";
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
        map.next_nodes = new ArrayList<>();

        if (curr.hasEdges()) {
            for (MapEdge edge : curr.getEdges()) {
                map.next_nodes.add(new GameStateSnapshot.NodeInfo(edge.dstX, edge.dstY));
            }
        } else if (AbstractDungeon.map != null && curr.y + 1 < AbstractDungeon.map.size()) {
            List<MapRoomNode> nextRow = AbstractDungeon.map.get(curr.y + 1);
            if (nextRow != null) {
                for (MapRoomNode node : nextRow) {
                    if (node != null) {
                        map.next_nodes.add(new GameStateSnapshot.NodeInfo(node.x, node.y));
                    }
                }
            }
        }
        return map;
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
                info.intent = monster.intent == null ? "unknown" : monster.intent.toString();
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
