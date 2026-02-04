package whispers.thespire.llm;

import com.google.gson.Gson;
import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;
import whispers.thespire.util.JsonUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class OpenAICompatClient {
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
        String url = baseUrl + "/chat/completions";

        try {
            return execute(request, url, false);
        } catch (SSLHandshakeException ssl) {
            try {
                return execute(request, url, true);
            } catch (Exception e) {
                LLMResult curlResult = tryCurlFallback(request, url);
                if (curlResult != null) {
                    return curlResult;
                }
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
                LLMResult curlResult = tryCurlFallback(request, url);
                if (curlResult != null) {
                    return curlResult;
                }
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
            conn.setRequestProperty("Authorization", "Bearer " + request.apiKey);
            applySslSettings(conn, insecure);

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
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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
        // Protocol properties are applied globally in ensureSslProperties().
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
        // Force TLSv1.2 for Java 8 and allow server-initiated renegotiation (some gateways require it).
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

    private static LLMResult tryCurlFallback(LLMRequest request, String url) {
        try {
            return executeWithCurl(request, url);
        } catch (Exception e) {
            return null;
        }
    }

    private static LLMResult executeWithCurl(LLMRequest request, String url) throws Exception {
        int timeoutSec = Math.max(1, request.timeoutMs / 1000);
        int retryTimeoutSec = Math.max(timeoutSec * 2, 20);

        CurlOutcome first = executeCurlOnce(request, url, timeoutSec, null);
        if (first.exit == 0) {
            return ResponseParser.parseChatCompletion(first.response);
        }
        if (first.exit == 28) {
            return executeWithCurlRetry(request, url, retryTimeoutSec, null);
        }
        if (first.exit == 35) {
            // Retry with Windows-specific SSL options.
            CurlOutcome noRevoke = executeCurlOnce(request, url, timeoutSec, extraArgs("--ssl-no-revoke"));
            if (noRevoke.exit == 0) {
                return ResponseParser.parseChatCompletion(noRevoke.response);
            }
            if (noRevoke.exit == 28) {
                return executeWithCurlRetry(request, url, retryTimeoutSec, extraArgs("--ssl-no-revoke"));
            }

            CurlOutcome tls12 = executeCurlOnce(request, url, timeoutSec, extraArgs("--tlsv1.2"));
            if (tls12.exit == 0) {
                return ResponseParser.parseChatCompletion(tls12.response);
            }
            if (tls12.exit == 28) {
                return executeWithCurlRetry(request, url, retryTimeoutSec, extraArgs("--tlsv1.2"));
            }

            CurlOutcome tls13 = executeCurlOnce(request, url, timeoutSec, extraArgs("--tlsv1.3"));
            if (tls13.exit == 0) {
                return ResponseParser.parseChatCompletion(tls13.response);
            }
            if (tls13.exit == 28) {
                return executeWithCurlRetry(request, url, retryTimeoutSec, extraArgs("--tlsv1.3"));
            }
            // Fall through to error below.
        }

        String raw = JsonUtil.truncate(first.response, 4000);
        return LLMResult.failure("curl_error:" + first.exit, raw);
    }

    private static LLMResult executeWithCurlRetry(LLMRequest request, String url, int timeoutSec, List<String> extraArgs) throws Exception {
        CurlOutcome outcome = executeCurlOnce(request, url, timeoutSec, extraArgs);
        if (outcome.exit != 0) {
            String raw = JsonUtil.truncate(outcome.response, 4000);
            return LLMResult.failure("curl_error:" + outcome.exit, raw);
        }
        return ResponseParser.parseChatCompletion(outcome.response);
    }

    private static CurlOutcome executeCurlOnce(LLMRequest request, String url, int timeoutSec, List<String> extraArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("curl.exe");
        cmd.add("-sS");
        cmd.add("--http1.1");
        cmd.add("-X");
        cmd.add("POST");
        cmd.add("--connect-timeout");
        cmd.add(String.valueOf(timeoutSec));
        cmd.add("--max-time");
        cmd.add(String.valueOf(timeoutSec));
        cmd.add("-H");
        cmd.add("Content-Type: application/json");
        cmd.add("-H");
        cmd.add("Authorization: Bearer " + request.apiKey);
        if (extraArgs != null) {
            cmd.addAll(extraArgs);
        }
        cmd.add("--data-binary");
        cmd.add("@-");
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        byte[] body = GSON.toJson(PromptBuilder.buildRequestBody(request)).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = proc.getOutputStream()) {
            out.write(body);
        }

        String response = readAll(proc.getInputStream());
        int exit = proc.waitFor();
        return new CurlOutcome(exit, response);
    }

    private static List<String> extraArgs(String arg) {
        List<String> args = new ArrayList<>();
        args.add(arg);
        return args;
    }

    private static class CurlOutcome {
        private final int exit;
        private final String response;

        private CurlOutcome(int exit, String response) {
            this.exit = exit;
            this.response = response == null ? "" : response;
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
                // Enable all supported cipher suites to avoid handshake failures with strict servers.
                ssl.setEnabledCipherSuites(ssl.getSupportedCipherSuites());
            }
        }
    }
}
