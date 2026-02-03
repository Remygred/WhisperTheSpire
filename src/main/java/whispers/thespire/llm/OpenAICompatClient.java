package whispers.thespire.llm;

import com.google.gson.Gson;
import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;
import whispers.thespire.util.JsonUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenAICompatClient {
    private static final Gson GSON = new Gson();

    public LLMResult complete(LLMRequest request) {
        if (request == null) {
            return LLMResult.failure("request_null", null);
        }
        if (request.apiKey == null || request.apiKey.trim().isEmpty()) {
            return LLMResult.failure("API key missing", null);
        }
        if (request.model == null || request.model.trim().isEmpty()) {
            return LLMResult.failure("model missing", null);
        }

        String baseUrl = normalizeBaseUrl(request.baseUrl);
        String url = baseUrl + "/chat/completions";

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(request.timeoutMs);
            conn.setReadTimeout(request.timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + request.apiKey);

            byte[] body = GSON.toJson(PromptBuilder.buildRequestBody(request)).getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
                out.write(body);
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = readAll(stream);
            if (code < 200 || code >= 300) {
                String raw = JsonUtil.truncate(response, 4000);
                return LLMResult.failure("http_error:" + code, raw);
            }

            return ResponseParser.parseChatCompletion(response);
        } catch (Exception e) {
            return LLMResult.failure("request_failed:" + e.getClass().getSimpleName(), null);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) {
            value = "https://api.openai.com/v1";
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.endsWith("/v1")) {
            value = value + "/v1";
        }
        return value;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedInputStream in = new BufferedInputStream(stream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
