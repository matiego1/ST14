package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.counting.CountingRewardsHandler;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CountingRewardsManager {
    public CountingRewardsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    private static final String KEY_COOKIE = "x-counting-key";
    private static final long RECONNECT_DELAY = 5;
    private static final long RECONNECT_DELAY_MULTIPLIER = 2;
    private static final int MAX_RECONNECT_DELAY = 600;
    private static final int PING_DELAY = 5;
    private static final TrustManager SSL_TRUST_MANAGER = new X509ExtendedTrustManager() {
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) {}
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) {}
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {}
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {}
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private HttpClient client;
    private WebSocket webSocket;
    private ScheduledExecutorService scheduler;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private String apiKey;
    private URI apiUri;
    private boolean closed = false;

    public void start() throws Exception {
        close();

        scheduler = Executors.newSingleThreadScheduledExecutor();

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{SSL_TRUST_MANAGER}, new SecureRandom());

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(CookieHandler.getDefault())
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();

        apiKey = plugin.getConfig().getString("counting-rewards.key", "");
        apiUri = URI.create(plugin.getConfig().getString("counting-rewards.url", ""));

        connect(0).join();

        scheduler.scheduleWithFixedDelay(() -> {
            if (webSocket == null) return;
            if (webSocket.isOutputClosed()) return;
            webSocket.sendText("ping", true);
        }, PING_DELAY, PING_DELAY, TimeUnit.SECONDS);
    }

    public void scheduleReconnect() {
        scheduleReconnect(0);
    }
    public void scheduleReconnect(int additionalDelay) {
        int attempt = reconnectAttempts.getAndIncrement();

        long delay = RECONNECT_DELAY * Math.min(MAX_RECONNECT_DELAY, Utils.powExact(RECONNECT_DELAY_MULTIPLIER, attempt));
        if (attempt == 0) {
            delay += additionalDelay;
        }
        delay = Math.min(MAX_RECONNECT_DELAY, delay);

        connect(delay);
    }

    public void close() {
        closeWebSocket();
        if (client == null) return;
        client.close();
        client = null;
    }

    private @NotNull CompletableFuture<Void> connect(long delay) {
        synchronized (this) {
            closed = false;
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.schedule(() -> {
            client.newWebSocketBuilder()
                    .header("Cookie", KEY_COOKIE + "=" + apiKey)
                    .buildAsync(apiUri, new CountingRewardsHandler(plugin))
                    .orTimeout(7, TimeUnit.SECONDS)
                    .thenAccept(ws -> {
                        webSocket = ws;
                        reconnectAttempts.set(0);
                        Logs.info("[CountingRewards] Connected to WebSocket");
                        result.complete(null);
                    })
                    .exceptionally(e -> {
                        if (e instanceof CompletionException e1 && e1.getCause() != null) {
                            e = e1.getCause();
                        }

                        if (e instanceof IllegalArgumentException || e instanceof SSLException) {
                            Logs.error("[CountingRewards] Failed to connect to WebSocket", e);
                            close();
                            result.complete(null);
                            return null;
                        }

                        Logs.error("[CountingRewards] Failed to connect to WebSocket: " + e.getClass().getName() + ": " + e.getMessage() + ". Scheduling a reconnect...");
                        scheduleReconnect();
                        result.complete(null);
                        return null;
                    });
        }, delay, TimeUnit.SECONDS);
        return result;
    }

    private void closeWebSocket() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        reconnectAttempts.set(0);
        if (webSocket == null) return;
        if (webSocket.isOutputClosed()) return;

        synchronized (this) {
            if (closed) return;
            closed = true;
        }

        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Closing")
                    .orTimeout(7, TimeUnit.SECONDS)
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            Logs.error("[CountingRewards] Failed to gracefully close the WebSocket, aborting...", e);
                            webSocket.abort();
                        }
                    })
                    .join();
        } catch (Exception e) {
            Logs.error("[CountingRewards] Failed to close the WebSocket", e);
        }
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }
}
