package whispers.thespire.llm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import whispers.thespire.config.ModConfig;
import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;

public class LLMClient {
    private final ExecutorService executor;
    private final OpenAICompatClient openaiClient;
    private final GeminiClient geminiClient;

    public LLMClient(OpenAICompatClient openaiClient, GeminiClient geminiClient) {
        this.openaiClient = openaiClient;
        this.geminiClient = geminiClient;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WhispersTheSpire-LLM");
            t.setDaemon(true);
            return t;
        });
    }

    public Future<LLMResult> submit(LLMRequest request) {
        return executor.submit(() -> {
            String provider = ModConfig.provider == null ? "" : ModConfig.provider.trim().toLowerCase();
            if ("gemini".equals(provider)) {
                return geminiClient.complete(request);
            }
            return openaiClient.complete(request);
        });
    }
}
