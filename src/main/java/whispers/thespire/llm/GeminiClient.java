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
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class GeminiClient {
    private static final Gson GSON = new Gson();
    private static boolean sslPropsApplied = false;

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

        ensureSslProperties();
        String baseUrl = normalizeBaseUrl(request.baseUrl);
        String model = normalizeModel(request.model);
        String url = baseUrl + "/" + model + ":generateContent";

        try {
            return execute(request, url, false);
        } catch (SSLHandshakeException ssl) {
            try {
                return execute(request, url, true);
            } catch (Exception e) {
                String msg = ssl.getMessage();
                if (msg != null && !msg.isEmpty()) {
                    return LLMResult.failure("request_failed:SSLHandshakeException:" + msg, null);
                }
                return LLMResult.failure("request_failed:SSLHandshakeException", null);
            }
        } catch (javax.net.ssl.SSLException ssl) {
            try {
                return execute(request, url, true);
            } catch (Exception e) {
                String msg = ssl.getMessage();
                if (msg != null && !msg.isEmpty()) {
                    return LLMResult.failure("request_failed:SSLException:" + msg, null);
                }
                return LLMResult.failure("request_failed:SSLException", null);
            }
        } catch (Exception e) {
            return LLMResult.failure("request_failed:" + e.getClass().getSimpleName(), null);
        }
    }

    private static LLMResult execute(LLMRequest request, String url, boolean insecure) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(request.timeoutMs);
            conn.setReadTimeout(request.timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", request.apiKey);
            applySslSettings(conn, insecure);

            byte[] body = GSON.toJson(buildRequestBody(request)).getBytes(StandardCharsets.UTF_8);
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

            return ResponseParser.parseGeminiGenerateContent(response);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static Map<String, Object> buildRequestBody(LLMRequest request) {
        PromptBuilder.Prompt prompt = PromptBuilder.build(request);
        Map<String, Object> body = new HashMap<>();
        List<Object> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        List<Object> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt.system + "\n\n" + prompt.user);
        parts.add(part);
        content.put("parts", parts);
        contents.add(content);
        body.put("contents", contents);

        Map<String, Object> config = new HashMap<>();
        config.put("temperature", request.temperature);
        config.put("maxOutputTokens", request.maxTokens);
        body.put("generationConfig", config);
        return body;
    }

    private static void applySslSettings(HttpURLConnection conn, boolean insecure) throws Exception {
        if (!(conn instanceof HttpsURLConnection)) {
            return;
        }
        HttpsURLConnection https = (HttpsURLConnection) conn;
        SSLSocketFactory factory = insecure ? buildInsecureFactory() : buildWindowsTrustFactory();
        if (factory != null) {
            https.setSSLSocketFactory(factory);
        }
        if (insecure) {
            https.setHostnameVerifier(buildInsecureVerifier());
        }
    }

    private static SSLSocketFactory buildWindowsTrustFactory() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            return new TlsSocketFactory(ctx.getSocketFactory(), new String[] {"TLSv1.2", "TLSv1.1", "TLSv1"});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SSLSocketFactory buildInsecureFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(null, trustAll, new SecureRandom());
            return new TlsSocketFactory(ctx.getSocketFactory(), new String[] {"TLSv1.2", "TLSv1.1", "TLSv1"});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HostnameVerifier buildInsecureVerifier() {
        return (hostname, session) -> true;
    }

    private static void ensureSslProperties() {
        if (sslPropsApplied) {
            return;
        }
        System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        System.setProperty("jdk.tls.allowUnsafeServerCertChange", "true");
        System.setProperty("jsse.enableSNIExtension", "true");
        sslPropsApplied = true;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) {
            value = "https://generativelanguage.googleapis.com/v1beta";
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.endsWith("/v1beta")) {
            value = value + "/v1beta";
        }
        return value;
    }

    private static String normalizeModel(String model) {
        String value = model == null ? "" : model.trim();
        if (value.startsWith("models/")) {
            return value;
        }
        return "models/" + value;
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

    private static class TlsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String[] protocols;

        private TlsSocketFactory(SSLSocketFactory delegate, String[] protocols) {
            this.delegate = delegate;
            this.protocols = protocols;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(s, host, port, autoClose);
            enableProtocols(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(host, port);
            enableProtocols(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(host, port, localHost, localPort);
            enableProtocols(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(host, port);
            enableProtocols(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
            java.net.Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            enableProtocols(socket);
            return socket;
        }

        private void enableProtocols(java.net.Socket socket) {
            if (protocols == null || protocols.length == 0) {
                return;
            }
            if (socket instanceof javax.net.ssl.SSLSocket) {
                javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) socket;
                ssl.setEnabledProtocols(protocols);
                ssl.setEnabledCipherSuites(ssl.getSupportedCipherSuites());
            }
        }
    }
}
