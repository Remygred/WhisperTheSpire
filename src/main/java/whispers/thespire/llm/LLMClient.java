package whispers.thespire.llm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;

public class LLMClient {
    private final ExecutorService executor;
    private final OpenAICompatClient client;

    public LLMClient(OpenAICompatClient client) {
        this.client = client;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WhispersTheSpire-LLM");
            t.setDaemon(true);
            return t;
        });
    }

    public Future<LLMResult> submit(LLMRequest request) {
        return executor.submit(() -> client.complete(request));
    }
}
