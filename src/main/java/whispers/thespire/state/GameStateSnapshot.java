package whispers.thespire.state;

import java.util.List;

public class GameStateSnapshot {
    public Run run;
    public String screen_context;
    public List<CardInfo> deck_summary;
    public List<RelicInfo> relics;
    public List<PotionInfo> potions;
    public MapInfo map;
    public MapFullInfo map_full;
    public RewardInfo reward;
    public NeowInfo neow;
    public ShopInfo shop;
    public BossRelicInfo boss_relic;
    public RestInfo rest;
    public EventInfo event;
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
        public String character;
        public Long seed;
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
        public String curr_type;
        public List<NodeInfo> next_nodes;
    }

    public static class MapFullInfo {
        public List<MapRow> rows;
    }

    public static class MapRow {
        public Integer y;
        public List<MapNode> nodes;
    }

    public static class MapNode {
        public Integer x;
        public Integer y;
        public String room_type;
        public List<NodeInfo> next;
    }

    public static class NodeInfo {
        public Integer x;
        public Integer y;
        public String room_type;

        public NodeInfo() {}

        public NodeInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public NodeInfo(int x, int y, String roomType) {
            this.x = x;
            this.y = y;
            this.room_type = roomType;
        }
    }

    public static class RewardInfo {
        public List<CardInfo> choices;
        public Boolean canSkip;
    }

    public static class NeowInfo {
        public List<NeowOption> options;
    }

    public static class NeowOption {
        public String label;
        public String reward_type;
        public String drawback;
    }

    public static class ShopInfo {
        public List<ShopItem> cards;
        public List<ShopItem> relics;
        public List<ShopItem> potions;
        public Boolean purge_available;
        public Integer purge_cost;
        public List<CardInfo> purge_candidates;
    }

    public static class ShopItem {
        public String item_type;
        public String id;
        public String name;
        public Integer price;
    }

    public static class BossRelicInfo {
        public List<RelicInfo> choices;
        public Boolean canSkip;
    }

    public static class RestInfo {
        public List<String> options;
        public List<CardInfo> upgrade_options;
    }

    public static class EventInfo {
        public String event_id;
        public String event_name;
        public List<String> options;
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
        public String name;
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
        public Integer intent_dmg;
        public Integer intent_base_dmg;
        public Integer intent_hits;
        public Boolean intent_multi;
        public String move_name;
        public List<PowerInfo> powers;
    }
}
