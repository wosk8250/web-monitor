package com.webmonitor.util;

/**
 * Discord 관련 상수 정의
 * Embed 색상 코드 및 Discord API 관련 상수
 */
public class DiscordConstants {

    private DiscordConstants() {
        // 유틸리티 클래스이므로 인스턴스화 방지
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Discord Embed 색상 코드
     */
    public static final int EMBED_COLOR_BLUE = 0x3477F3;    // 3447003 - 일반 알림 (파란색)
    public static final int EMBED_COLOR_GREEN = 0x4CBB17;   // 5025616 - 재입고 알림 (녹색)
    public static final int EMBED_COLOR_RED = 0xFF0000;     // 16711680 - 에러 알림 (빨간색)
    public static final int EMBED_COLOR_YELLOW = 0xFFFF00;  // 16776960 - 경고 알림 (노란색)
    public static final int EMBED_COLOR_ORANGE = 0xFFA500;  // 16753920 - 중요 알림 (주황색)
}
