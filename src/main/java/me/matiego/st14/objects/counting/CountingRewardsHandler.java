package me.matiego.st14.objects.counting;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.CountingRewardsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public class CountingRewardsHandler implements WebSocket.Listener {
    public CountingRewardsHandler(@NotNull Main plugin) {
        this.plugin = plugin;
        deposit = new DepositRoute(plugin);
    }

    private final static int UNAUTHORIZED_CODE = 3000;
    private final Main plugin;
    private final DepositRoute deposit;

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (statusCode == UNAUTHORIZED_CODE) {
            Logs.error("[CountingRewards] WebSocket is closed. (Code: " + statusCode + "; Reason: " + reason + ")");
            return null;
        }

        CountingRewardsManager manager = plugin.getCountingRewardsManager();

        if (manager.isClosed()) {
            Logs.info("[CountingRewards] WebSocket is closed. (Code: " + statusCode + "; Reason: " + reason + ")");
            return null;
        }

        Logs.warning("[CountingRewards] WebSocket is closed. (Code: " + statusCode + "; Reason: " + reason + ") Reconnecting...");
        manager.scheduleReconnect(statusCode == WebSocket.NORMAL_CLOSURE ? 15 : 0);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        Logs.error("[CountingRewards] WebSocket error occurred", error);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            Response response = getResponse(String.valueOf(data));
            webSocket.sendText(response.getAsJSON(), true);
        } catch (Exception e) {
            Logs.error("[CountingRewards] Failed to handle a WebSocket message", e);
        }

        webSocket.request(1);
        return null;
    }

    private @NotNull Response getResponse(@Nullable String data) {
        try {
            if (data == null) return new Response(400, "Empty text data");

            JSONObject json = new JSONObject(data);
            String id = json.getString("id");

            Response response;
            if (json.getString("path").equals("deposit")) {
                response = deposit.handle(json.getJSONObject("params"));
            } else {
                response = new Response(400, "Unknown request path");
            }

            response.setId(id);
            return response;
        } catch (Exception e) {
            Logs.error("[CountingRewards] Failed to get a response to a WebSocket message", e);
            return new Response(500, e.getMessage());
        }
    }

    @Getter
    public static class Response {
        public Response(int statusCode, @NotNull String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        @Setter
        private String id = null;
        private final int statusCode;
        private final String message;

        public @NotNull String getAsJSON() {
            JSONObject json = new JSONObject();
            json.put("id", String.valueOf(id)); // properly handle null
            json.put("status-code", statusCode);
            json.put("message", message);
            return json.toString();
        }
    }
}
