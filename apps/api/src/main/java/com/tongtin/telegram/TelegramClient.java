package com.tongtin.telegram;

/**
 * Step 35: outbound Telegram transport seam. The production implementation
 * posts to the Bot API; tests swap in a capturing fake via @MockBean so the
 * hermetic suite never touches the network.
 */
public interface TelegramClient {

    /**
     * Sends a single message to the given chat. Blank/absent tokens are a
     * no-op (false). Any transport/parse failure returns false and is logged
     * by the implementation — it must NEVER throw into a business call.
     */
    boolean sendMessage(String token, long chatId, String text);
}