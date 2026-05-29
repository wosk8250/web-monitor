package com.webmonitor.discord;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.service.ProductMonitorService;
import com.webmonitor.service.ProductService;
import com.webmonitor.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 디스코드 Slash Command 핸들러
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandHandler extends ListenerAdapter {

    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductMonitorService productMonitorService;
    private final SiteService siteService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        log.info("디스코드 명령어 수신: {}", event.getName());

        switch (event.getName()) {
            case "add" -> handleAddSite(event);
            case "remove" -> handleRemoveSite(event);
            case "list" -> handleListSites(event);
            case "toggle" -> handleToggleSite(event);
            case "keyword-add" -> handleAddKeyword(event);
            case "keyword-remove" -> handleRemoveKeyword(event);
            case "keyword-list" -> handleListKeywords(event);
            case "status" -> handleStatus(event);
            case "product-add" -> handleAddProduct(event);
            case "product-remove" -> handleRemoveProduct(event);
            case "product-list" -> handleListProducts(event);
            case "product-toggle" -> handleToggleProduct(event);
            case "product-check" -> handleCheckProduct(event);
            case "product-set-priority" -> handleSetProductPriority(event);
            case "product-set-interval" -> handleSetProductInterval(event);
            case "set-interval" -> handleSetSiteInterval(event);
            case "help" -> handleHelp(event);
            default -> event.reply("알 수 없는 명령어입니다.").setEphemeral(true).queue();
        }
    }

    /**
     * 사이트 추가 명령어 처리
     */
    private void handleAddSite(SlashCommandInteractionEvent event) {
        String url = event.getOption("url", OptionMapping::getAsString);
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (url == null || name == null) {
            event.reply("❌ URL과 이름을 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자별 중복 체크 (성능 최적화: exists 메서드 사용)
        if (siteRepository.existsByDiscordUserIdAndUrl(userId, url)) {
            event.reply("❌ 이미 등록한 URL입니다.").setEphemeral(true).queue();
            return;
        }

        Site site = Site.builder()
                .name(name)
                .url(url)
                .active(true)
                .detectContentChange(true)
                .discordUserId(userId)  // Discord User ID 저장
                .build();

        siteRepository.save(site);
        log.info("사이트 추가됨: {} ({}) - 사용자: {}", name, url, userId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ 사이트가 추가되었습니다")
                .setColor(Color.GREEN)
                .addField("이름", name, true)
                .addField("URL", url, true)
                .addField("상태", "활성", true)
                .setFooter("사이트 ID: " + site.getId());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 사이트 삭제 명령어 처리
     */
    private void handleRemoveSite(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null) {
            event.reply("❌ 사이트 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);
        siteRepository.delete(site);
        log.info("사이트 삭제됨: {} ({}) - 사용자: {}", site.getName(), site.getUrl(), userId);

        event.reply("✅ 사이트가 삭제되었습니다: **" + site.getName() + "**").queue();
    }

    /**
     * 사이트 목록 조회 명령어 처리
     */
    private void handleListSites(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();  // Discord User ID 추출

        // 사용자의 사이트만 조회
        List<Site> sites = siteRepository.findByDiscordUserId(userId);

        if (sites.isEmpty()) {
            event.reply("📋 등록된 사이트가 없습니다.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 등록된 사이트 목록")
                .setColor(Color.BLUE)
                .setDescription("총 " + sites.size() + "개의 사이트");

        for (Site site : sites) {
            String status = site.getActive() ? "✅ 활성" : "⏸️ 비활성";
            List<Keyword> keywords = keywordRepository.findBySite(site);
            String keywordInfo = keywords.isEmpty()
                ? "전체 페이지 감지"
                : keywords.size() + "개 키워드";

            embed.addField(
                site.getName(),
                "**URL:** " + site.getUrl() + "\n" +
                "**상태:** " + status + "\n" +
                "**키워드:** " + keywordInfo,
                false
            );
        }

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 사이트 활성화/비활성화 토글
     */
    private void handleToggleSite(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null) {
            event.reply("❌ 사이트 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);
        site.setActive(!site.getActive());
        siteRepository.save(site);

        String status = site.getActive() ? "✅ 활성화" : "⏸️ 비활성화";
        event.reply(status + "되었습니다: **" + site.getName() + "**").queue();
    }

    /**
     * 키워드 추가 명령어 처리
     */
    private void handleAddKeyword(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);
        String keywordText = event.getOption("keyword", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (siteName == null || keywordText == null) {
            event.reply("❌ 사이트 이름과 키워드를 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);

        // 사이트의 detectContentChange를 false로 변경 (키워드 모드로 전환)
        if (site.getDetectContentChange()) {
            site.setDetectContentChange(false);
            siteRepository.save(site);
        }

        Keyword keyword = Keyword.builder()
                .keyword(keywordText)
                .site(site)
                .active(true)
                .build();

        keywordRepository.save(keyword);
        log.info("키워드 추가됨: {} -> {} - 사용자: {}", site.getName(), keywordText, userId);

        event.reply("✅ 키워드가 추가되었습니다: **" + keywordText + "** (사이트: " + site.getName() + ")").queue();
    }

    /**
     * 키워드 삭제 명령어 처리
     */
    private void handleRemoveKeyword(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);
        String keywordText = event.getOption("keyword", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (siteName == null || keywordText == null) {
            event.reply("❌ 사이트 이름과 키워드를 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);
        List<Keyword> keywords = keywordRepository.findBySite(site);

        Optional<Keyword> keywordOpt = keywords.stream()
                .filter(k -> k.getKeyword().equals(keywordText))
                .findFirst();

        if (keywordOpt.isEmpty()) {
            event.reply("❌ 해당 키워드를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        keywordRepository.delete(keywordOpt.get());

        // 키워드가 모두 삭제되면 전체 페이지 감지 모드로 전환
        List<Keyword> remainingKeywords = keywordRepository.findBySite(site);
        if (remainingKeywords.isEmpty()) {
            site.setDetectContentChange(true);
            siteRepository.save(site);
        }

        event.reply("✅ 키워드가 삭제되었습니다: **" + keywordText + "**").queue();
    }

    /**
     * 키워드 목록 조회 명령어 처리
     */
    private void handleListKeywords(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (siteName == null) {
            event.reply("❌ 사이트 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);
        List<Keyword> keywords = keywordRepository.findBySite(site);

        if (keywords.isEmpty()) {
            event.reply("📋 등록된 키워드가 없습니다. (전체 페이지 감지 모드)").queue();
            return;
        }

        String keywordList = keywords.stream()
                .map(k -> "• " + k.getKeyword())
                .collect(Collectors.joining("\n"));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔍 키워드 목록: " + site.getName())
                .setColor(Color.CYAN)
                .setDescription(keywordList)
                .setFooter("총 " + keywords.size() + "개");

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 시스템 상태 조회 명령어 처리
     */
    private void handleStatus(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();  // Discord User ID 추출

        // 사용자의 데이터만 조회
        List<Site> sites = siteRepository.findByDiscordUserId(userId);
        long totalSites = sites.size();
        long activeSites = sites.stream().filter(Site::getActive).count();
        long totalKeywords = sites.stream()
                .mapToLong(site -> keywordRepository.findBySite(site).size())
                .sum();

        List<Product> products = productRepository.findByDiscordUserId(userId);
        long totalProducts = products.size();
        long activeProducts = products.stream().filter(Product::getActive).count();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 내 모니터링 상태")
                .setColor(Color.ORANGE)
                .addField("전체 사이트", String.valueOf(totalSites), true)
                .addField("활성 사이트", String.valueOf(activeSites), true)
                .addField("전체 키워드", String.valueOf(totalKeywords), true)
                .addField("전체 제품", String.valueOf(totalProducts), true)
                .addField("활성 제품", String.valueOf(activeProducts), true)
                .addField("", "", true) // empty field for layout
                .setFooter("웹 모니터링 시스템 v1.0");

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 제품 추가 명령어 처리
     */
    private void handleAddProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String url = event.getOption("url", OptionMapping::getAsString);
        String priorityStr = event.getOption("priority", "NORMAL", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", 3, OptionMapping::getAsInt);
        String selector = event.getOption("selector", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null || url == null) {
            event.reply("❌ 제품 이름과 URL을 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 우선순위 유효성 검사
        Product.Priority priority;
        try {
            priority = Product.Priority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            event.reply("❌ 우선순위는 URGENT 또는 NORMAL이어야 합니다.").setEphemeral(true).queue();
            return;
        }

        // CSS 셀렉터 유효성 검사
        if (selector != null && !selector.trim().isEmpty()) {
            if (!isValidCssSelector(selector)) {
                event.reply("❌ 잘못된 CSS 셀렉터 형식입니다.\n" +
                        "예시: `.price`, `#stock-status`, `div.product-info`, `span[class='stock']`")
                        .setEphemeral(true)
                        .queue();
                return;
            }
        }

        // 사용자별 URL 중복 체크 (성능 최적화: exists 메서드 사용)
        if (productRepository.existsByDiscordUserIdAndUrl(userId, url)) {
            event.reply("❌ 이미 등록한 제품 URL입니다.").setEphemeral(true).queue();
            return;
        }

        Product product = Product.builder()
                .name(name)
                .url(url)
                .priority(priority)
                .checkIntervalMinutes(interval)
                .contentSelector(selector)
                .discordUserId(userId)  // Discord User ID 저장
                .build();

        Product saved = productService.createProduct(product);
        log.info("제품 추가됨: {} ({}), 셀렉터: {} - 사용자: {}", name, url, selector != null ? selector : "전체 페이지", userId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ 제품이 추가되었습니다")
                .setColor(Color.GREEN)
                .addField("이름", name, true)
                .addField("우선순위", priority.name(), true)
                .addField("체크 주기", interval + "분", true)
                .addField("모니터링 방식",
                    selector != null ? "셀렉터: " + selector : "전체 페이지",
                    false)
                .addField("URL", url, false)
                .setFooter("제품 ID: " + saved.getId());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 제품 삭제 명령어 처리
     */
    private void handleRemoveProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null) {
            event.reply("❌ 제품 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 제품만 검색
        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            event.reply("❌ 해당 이름의 제품을 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Product product = products.get(0);
        productService.deleteProduct(product.getId());
        log.info("제품 삭제됨: {} ({}) - 사용자: {}", product.getName(), product.getUrl(), userId);

        event.reply("✅ 제품이 삭제되었습니다: **" + product.getName() + "**").queue();
    }

    /**
     * 제품 목록 조회 명령어 처리
     */
    private void handleListProducts(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();  // Discord User ID 추출

        // 사용자의 제품만 조회
        List<Product> products = productRepository.findByDiscordUserId(userId);

        if (products.isEmpty()) {
            event.reply("📦 등록된 제품이 없습니다.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📦 등록된 제품 목록")
                .setColor(Color.MAGENTA)
                .setDescription("총 " + products.size() + "개의 제품");

        for (Product product : products) {
            String status = product.getActive() ? "✅ 활성" : "⏸️ 비활성";
            String priority = product.getPriority() == Product.Priority.URGENT ? "🔴 긴급" : "🟢 일반";
            String stockStatus = switch (product.getCurrentStatus()) {
                case IN_STOCK -> "💚 재고 있음";
                case OUT_OF_STOCK -> "❤️ 품절";
                case UNKNOWN -> "❓ 알 수 없음";
            };

            embed.addField(
                product.getName(),
                "**상태:** " + status + "\n" +
                "**우선순위:** " + priority + "\n" +
                "**재고:** " + stockStatus + "\n" +
                "**체크 주기:** " + product.getCheckIntervalMinutes() + "분",
                false
            );
        }

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * 제품 활성화/비활성화 토글
     */
    private void handleToggleProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null) {
            event.reply("❌ 제품 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 제품만 검색
        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            event.reply("❌ 해당 이름의 제품을 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Product product = products.get(0);
        Product toggled = productService.toggleProductActive(product.getId());

        String status = toggled.getActive() ? "✅ 활성화" : "⏸️ 비활성화";
        event.reply(status + "되었습니다: **" + product.getName() + "**").queue();
    }

    /**
     * 제품 즉시 체크
     */
    private void handleCheckProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null) {
            event.reply("❌ 제품 이름을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 제품만 검색
        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            event.reply("❌ 해당 이름의 제품을 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Product product = products.get(0);

        event.reply("🔍 제품을 확인 중입니다: **" + product.getName() + "**\n잠시만 기다려주세요...").queue();

        try {
            productMonitorService.checkProductNow(product.getId());

            // 업데이트된 제품 정보 다시 조회
            Product updated = productService.getProductById(product.getId()).orElse(product);
            String stockStatus = switch (updated.getCurrentStatus()) {
                case IN_STOCK -> "💚 재고 있음";
                case OUT_OF_STOCK -> "❤️ 품절";
                case UNKNOWN -> "❓ 알 수 없음";
            };

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ 제품 확인 완료")
                    .setColor(Color.GREEN)
                    .addField("제품명", updated.getName(), false)
                    .addField("재고 상태", stockStatus, true)
                    .addField("가격", updated.getCurrentPrice() != null ? updated.getCurrentPrice() + "원" : "정보 없음", true)
                    .setFooter("마지막 확인: " + updated.getLastCheckedAt());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            log.error("제품 즉시 체크 실패: {}", product.getName(), e);
            event.getHook().sendMessage("❌ 제품 확인 중 오류가 발생했습니다.").queue();
        }
    }

    /**
     * 제품 우선순위 설정
     */
    private void handleSetProductPriority(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String priorityStr = event.getOption("priority", OptionMapping::getAsString);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null || priorityStr == null) {
            event.reply("❌ 제품 이름과 우선순위를 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        // 우선순위 유효성 검사
        Product.Priority priority;
        try {
            priority = Product.Priority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            event.reply("❌ 우선순위는 URGENT 또는 NORMAL이어야 합니다.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 제품만 검색
        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            event.reply("❌ 해당 이름의 제품을 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Product product = products.get(0);
        productService.setProductPriority(product.getId(), priority);

        String priorityText = priority == Product.Priority.URGENT ? "🔴 긴급 (30초 주기)" : "🟢 일반 (60초 주기)";
        event.reply("✅ 우선순위가 변경되었습니다: **" + product.getName() + "** → " + priorityText).queue();
    }

    /**
     * 제품 체크 주기 설정
     */
    private void handleSetProductInterval(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", OptionMapping::getAsInt);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null || interval == null) {
            event.reply("❌ 제품 이름과 체크 주기를 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        if (interval < 1) {
            event.reply("❌ 체크 주기는 최소 1분이어야 합니다.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 제품만 검색
        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            event.reply("❌ 해당 이름의 제품을 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Product product = products.get(0);
        productService.setProductCheckInterval(product.getId(), interval);

        event.reply("✅ 체크 주기가 변경되었습니다: **" + product.getName() + "** → " + interval + "분").queue();
    }

    /**
     * 사이트 체크 주기 설정
     */
    private void handleSetSiteInterval(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", OptionMapping::getAsInt);
        String userId = event.getUser().getId();  // Discord User ID 추출

        if (name == null || interval == null) {
            event.reply("❌ 사이트 이름과 체크 주기를 모두 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        if (interval < 1) {
            event.reply("❌ 체크 주기는 최소 1분이어야 합니다.").setEphemeral(true).queue();
            return;
        }

        // 사용자의 사이트만 검색
        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            event.reply("❌ 해당 이름의 사이트를 찾을 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        Site site = sites.get(0);
        site.setCheckIntervalMinutes(interval);
        siteRepository.save(site);

        event.reply("✅ 체크 주기가 변경되었습니다: **" + site.getName() + "** → " + interval + "분").queue();
    }

    /**
     * 도움말 명령어 처리
     */
    private void handleHelp(SlashCommandInteractionEvent event) {
        String category = event.getOption("category", "all", OptionMapping::getAsString);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 명령어 도움말")
                .setColor(Color.CYAN);

        if (category.equals("all") || category.equals("site")) {
            embed.addField("🌐 사이트 관리",
                "• `/add <url> <name>` - 사이트 추가\n" +
                "• `/remove <name>` - 사이트 삭제\n" +
                "• `/list` - 사이트 목록\n" +
                "• `/toggle <name>` - 사이트 활성화/비활성화\n" +
                "• `/set-interval <name> <분>` - 사이트 체크 주기 설정",
                false
            );
        }

        if (category.equals("all") || category.equals("keyword")) {
            embed.addField("🔍 키워드 관리",
                "• `/keyword-add <site> <keyword>` - 키워드 추가\n" +
                "• `/keyword-remove <site> <keyword>` - 키워드 삭제\n" +
                "• `/keyword-list <site>` - 키워드 목록",
                false
            );
        }

        if (category.equals("all") || category.equals("product")) {
            embed.addField("📦 제품 재고 모니터링",
                "• `/product-add <name> <url> [priority] [interval]` - 제품 추가\n" +
                "• `/product-remove <name>` - 제품 삭제\n" +
                "• `/product-list` - 제품 목록\n" +
                "• `/product-toggle <name>` - 제품 활성화/비활성화\n" +
                "• `/product-check <name>` - 제품 즉시 확인\n" +
                "• `/product-set-priority <name> <URGENT/NORMAL>` - 우선순위 설정\n" +
                "• `/product-set-interval <name> <분>` - 체크 주기 설정",
                false
            );
        }

        if (category.equals("all") || category.equals("system")) {
            embed.addField("⚙️ 시스템",
                "• `/status` - 시스템 상태\n" +
                "• `/help [category]` - 도움말 (category: site/keyword/product/system)",
                false
            );
        }

        embed.setFooter("우선순위: URGENT (30초 주기) / NORMAL (60초 주기)");

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * CSS 셀렉터 유효성 검증
     */
    private boolean isValidCssSelector(String selector) {
        if (selector == null || selector.trim().isEmpty()) {
            return false;
        }

        try {
            // Jsoup의 셀렉터 파서로 검증
            // 임시 HTML로 테스트
            org.jsoup.nodes.Document testDoc = org.jsoup.Jsoup.parse("<html><body></body></html>");
            testDoc.select(selector);  // 잘못된 셀렉터면 Exception 발생
            return true;
        } catch (Exception e) {
            log.debug("셀렉터 검증 실패: {}", selector, e);
            return false;
        }
    }
}
