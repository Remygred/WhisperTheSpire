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
}
