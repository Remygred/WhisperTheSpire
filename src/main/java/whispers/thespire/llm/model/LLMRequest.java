package whispers.thespire.llm.model;

public class LLMRequest {
    public String baseUrl;
    public String model;
    public String apiKey;
    public float temperature;
    public int maxTokens;
    public int timeoutMs;
    public String contextType;
    public String snapshotJson;
    public String snapshotHash;
    public Integer ascension;
    public String character;
    public Long seed;
    public Integer floor;
    public Integer act;
    public Integer hp;
    public Integer maxHp;
    public Integer gold;
    public String mapCurrent;
    public String mapNext;
    public Boolean mapFullAvailable;
    public String essentialFacts;
    public String skillHints;
    public Integer combatHandCount;
    public String combatPlayableCards;
    public Integer combatEnergy;
    public String eventId;
    public String eventName;
    public String eventOptions;
}
