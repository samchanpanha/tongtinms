package com.tongtin.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 35: real Bot API caller. POST /sendMessage with {chat_id, text} to
 * https://api.telegram.org/bot<token>/sendMessage via java.net.http.HttpClient
 * (10 s timeout) — no new dependency. Best-effort: any failure returns false
 * and is logged; the token is a READ-ONLY argument, so a caller with a blank
 * token (unconfigured bot) short-circuits before the network.
 */
@Component
public class BotApiTelegramClient implements TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(BotApiTelegramClient.class);
    private static final String SEND_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BotApiTelegramClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean sendMessage(String token, long chatId, String text) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEND_URL.formatted(token)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Telegram sendMessage returned HTTP {} for chat {}", response.statusCode(), chatId);
                return false;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("ok").asBoolean(false);
        } catch (Exception ex) {
            log.warn("Telegram sendMessage failed for chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }
}