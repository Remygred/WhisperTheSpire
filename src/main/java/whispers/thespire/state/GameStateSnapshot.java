package whispers.thespire.state;

import java.util.List;

public class GameStateSnapshot {
    public Run run;
    public String screen_context;
    public List<CardInfo> deck_summary;
    public List<RelicInfo> relics;
    public List<PotionInfo> potions;
    public MapInfo map;
    public RewardInfo reward;
    public CombatInfo combat;

    public boolean trimmed;
    public List<String> dropped_fields;
    public int json_size;
    public String snapshot_hash;

    public static class Run {
        public Integer act;
        public Integer floor;
        public Integer ascension;
        public Integer gold;
        public Integer hp;
        public Integer maxHp;
    }

    public static class CardInfo {
        public String card_id;
        public String name;
        public Boolean upgraded;
        public Integer cost;
        public String type;
        public String rarity;
    }

    public static class RelicInfo {
        public String relic_id;
        public String name;
    }

    public static class PotionInfo {
        public String potion_id;
        public String name;
    }

    public static class MapInfo {
        public Integer curr_x;
        public Integer curr_y;
        public List<NodeInfo> next_nodes;
    }

    public static class NodeInfo {
        public Integer x;
        public Integer y;

        public NodeInfo() {}

        public NodeInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class RewardInfo {
        public List<CardInfo> choices;
        public Boolean canSkip;
    }

    public static class CombatInfo {
        public Integer turn;
        public Integer energy;
        public Integer player_block;
        public List<PowerInfo> player_powers;
        public List<CombatCardInfo> hand;
        public Integer draw_pile_size;
        public Integer discard_pile_size;
        public Integer exhaust_pile_size;
        public List<MonsterInfo> monsters;
    }

    public static class PowerInfo {
        public String id;
        public Integer amount;
    }

    public static class CombatCardInfo {
        public String card_id;
        public Integer cost;
        public Boolean upgraded;
        public String type;
    }

    public static class MonsterInfo {
        public String id;
        public String name;
        public Integer hp;
        public Integer maxHp;
        public Integer block;
        public String intent;
        public List<PowerInfo> powers;
    }
}
