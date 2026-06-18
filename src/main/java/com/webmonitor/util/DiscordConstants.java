package com.webmonitor.util;

import com.webmonitor.domain.Product;

import java.time.format.DateTimeFormatter;

public class DiscordConstants {

    private DiscordConstants() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final int EMBED_COLOR_BLUE = 0x3477F3;
    public static final int EMBED_COLOR_GREEN = 0x4CBB17;
    public static final int EMBED_COLOR_RED = 0xFF0000;
    public static final int EMBED_COLOR_YELLOW = 0xFFFF00;
    public static final int EMBED_COLOR_ORANGE = 0xFFA500;

    public static String formatStockStatus(Product.StockStatus status) {
        return switch (status) {
            case IN_STOCK -> "💚 재고 있음";
            case OUT_OF_STOCK -> "❤️ 품절";
            case UNKNOWN -> "❓ 알 수 없음";
        };
    }
}
