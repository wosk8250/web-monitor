package com.webmonitor.service;

import com.webmonitor.config.DiscordBotConfig;
import com.webmonitor.discord.DiscordCommandHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.stereotype.Service;

/**
 * 디스코드 봇 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordBotService {

    private final DiscordBotConfig botConfig;
    private final DiscordCommandHandler commandHandler;
    private JDA jda;

    @PostConstruct
    public void init() {
        if (!botConfig.isEnabled()) {
            log.info("디스코드 봇이 비활성화되어 있습니다.");
            return;
        }

        if (botConfig.getToken() == null || botConfig.getToken().isEmpty()) {
            log.warn("디스코드 봇 토큰이 설정되지 않았습니다.");
            return;
        }

        try {
            log.info("디스코드 봇 초기화 중...");

            // JDA 빌더 생성 및 설정
            jda = JDABuilder.createDefault(botConfig.getToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(commandHandler)
                    .build();

            // 봇이 준비될 때까지 대기
            jda.awaitReady();

            // Slash Commands 등록
            registerCommands();

            log.info("디스코드 봇이 성공적으로 시작되었습니다.");
        } catch (Exception e) {
            log.error("디스코드 봇 초기화 실패", e);
        }
    }

    /**
     * Slash Commands 등록
     */
    private void registerCommands() {
        try {
            jda.updateCommands().addCommands(
                    // 사이트 추가
                    Commands.slash("add", "모니터링할 사이트를 추가합니다")
                            .addOption(OptionType.STRING, "url", "사이트 URL", true)
                            .addOption(OptionType.STRING, "name", "사이트 이름", true),

                    // 사이트 삭제
                    Commands.slash("remove", "모니터링 사이트를 삭제합니다")
                            .addOption(OptionType.STRING, "name", "사이트 이름", true),

                    // 사이트 목록
                    Commands.slash("list", "등록된 모든 사이트를 조회합니다"),

                    // 사이트 활성화/비활성화 토글
                    Commands.slash("toggle", "사이트 모니터링을 활성화/비활성화합니다")
                            .addOption(OptionType.STRING, "name", "사이트 이름", true),

                    // 키워드 추가
                    Commands.slash("keyword-add", "사이트에 모니터링할 키워드를 추가합니다")
                            .addOption(OptionType.STRING, "site", "사이트 이름", true)
                            .addOption(OptionType.STRING, "keyword", "키워드", true),

                    // 키워드 삭제
                    Commands.slash("keyword-remove", "사이트의 키워드를 삭제합니다")
                            .addOption(OptionType.STRING, "site", "사이트 이름", true)
                            .addOption(OptionType.STRING, "keyword", "키워드", true),

                    // 키워드 목록
                    Commands.slash("keyword-list", "사이트의 키워드 목록을 조회합니다")
                            .addOption(OptionType.STRING, "site", "사이트 이름", true),

                    // 시스템 상태
                    Commands.slash("status", "모니터링 시스템 상태를 조회합니다"),

                    // 제품 추가
                    Commands.slash("product-add", "제품 재고 모니터링을 추가합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true)
                            .addOption(OptionType.STRING, "url", "제품 URL", true)
                            .addOption(OptionType.STRING, "selector", "CSS 셀렉터 (선택, 없으면 전체 페이지 모니터링)", false)
                            .addOption(OptionType.STRING, "priority", "우선순위 (URGENT/NORMAL, 기본값: NORMAL)", false)
                            .addOption(OptionType.INTEGER, "interval", "체크 주기(분, 기본값: 3분)", false),

                    // 제품 삭제
                    Commands.slash("product-remove", "제품 모니터링을 삭제합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true),

                    // 제품 목록
                    Commands.slash("product-list", "등록된 모든 제품을 조회합니다"),

                    // 제품 활성화/비활성화 토글
                    Commands.slash("product-toggle", "제품 모니터링을 활성화/비활성화합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true),

                    // 제품 즉시 체크
                    Commands.slash("product-check", "제품 재고를 즉시 확인합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true),

                    // 제품 우선순위 설정
                    Commands.slash("product-set-priority", "제품 우선순위를 설정합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true)
                            .addOption(OptionType.STRING, "priority", "우선순위 (URGENT/NORMAL)", true),

                    // 제품 체크 주기 설정
                    Commands.slash("product-set-interval", "제품 체크 주기를 설정합니다")
                            .addOption(OptionType.STRING, "name", "제품 이름", true)
                            .addOption(OptionType.INTEGER, "interval", "체크 주기(분)", true),

                    // 사이트 체크 주기 설정
                    Commands.slash("set-interval", "사이트 체크 주기를 설정합니다")
                            .addOption(OptionType.STRING, "name", "사이트 이름", true)
                            .addOption(OptionType.INTEGER, "interval", "체크 주기(분)", true),

                    // 도움말
                    Commands.slash("help", "사용 가능한 모든 명령어를 보여줍니다")
                            .addOption(OptionType.STRING, "category", "카테고리 (site/keyword/product/system)", false)
            ).queue(
                    success -> log.info("Slash Commands 등록 완료"),
                    error -> log.error("Slash Commands 등록 실패", error)
            );
        } catch (Exception e) {
            log.error("Slash Commands 등록 중 오류 발생", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            log.info("디스코드 봇 종료 중...");
            jda.shutdown();
        }
    }

    /**
     * JDA 인스턴스 반환
     */
    public JDA getJda() {
        return jda;
    }
}
