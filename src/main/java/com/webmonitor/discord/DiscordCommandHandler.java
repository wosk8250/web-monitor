package com.webmonitor.discord;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Site;
import com.webmonitor.util.DiscordConstants;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.service.KeywordService;
import com.webmonitor.service.ProductMonitorService;
import com.webmonitor.service.ProductService;
import com.webmonitor.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandHandler extends ListenerAdapter {

    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final ProductRepository productRepository;
    private final KeywordService keywordService;
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

    private void handleAddSite(SlashCommandInteractionEvent event) {
        String url = event.getOption("url", OptionMapping::getAsString);
        String name = event.getOption("name", OptionMapping::getAsString);
        String keywordText = event.getOption("keyword", OptionMapping::getAsString);

        if (url == null || name == null) {
            replyError(event, "❌ URL과 이름을 모두 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        if (siteRepository.existsByDiscordUserIdAndUrl(userId, url)) {
            hook.editOriginal("❌ 이미 등록한 URL입니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = Site.builder()
                .name(name)
                .url(url)
                .active(true)
                .detectContentChange(true)
                .discordUserId(userId)
                .build();

        try {
            siteRepository.save(site);
        } catch (DataIntegrityViolationException e) {
            log.warn("사이트 저장 중 중복 URL 감지 (TOCTOU): {} - 사용자: {}", url, userId);
            hook.editOriginal("❌ 이미 등록한 URL입니다.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }

        String monitoringMode;
        if (keywordText != null && !keywordText.isBlank()) {
            String trimmedKeyword = keywordText.strip();
            try {
                keywordService.addKeywordToSite(site, trimmedKeyword);
            } catch (Exception e) {
                log.error("키워드 저장 실패 — 사이트 롤백 시도: {} ({}) 키워드: {} - 사용자: {}", name, url, trimmedKeyword, userId, e);
                try {
                    siteRepository.delete(site);
                } catch (Exception deleteEx) {
                    log.error("사이트 롤백 실패 (고아 사이트 발생 가능): {} - 사용자: {}", name, userId, deleteEx);
                }
                hook.editOriginal("❌ 키워드 저장 중 오류가 발생했습니다. 다시 시도해주세요.")
                        .queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
                return;
            }
            monitoringMode = "키워드: " + trimmedKeyword;
            log.info("사이트 추가됨 (키워드 모드): {} ({}) 키워드: {} - 사용자: {}", name, url, trimmedKeyword, userId);
        } else {
            monitoringMode = "전체글 알림";
            log.info("사이트 추가됨 (전체글 모드): {} ({}) - 사용자: {}", name, url, userId);
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ 사이트가 추가되었습니다")
                .setColor(Color.GREEN)
                .addField("이름", name, true)
                .addField("상태", "활성", true)
                .addField("알림 모드", monitoringMode, true)
                .addField("URL", url, false)
                .setFooter("사이트 ID: " + site.getId());

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleRemoveSite(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        if (name == null) {
            replyError(event, "❌ 사이트 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);
        try {
            siteService.deleteSite(site.getId());
        } catch (Exception e) {
            log.error("사이트 삭제 실패: {} - 사용자: {}", site.getName(), userId, e);
            hook.editOriginal("❌ 사이트 삭제 중 오류가 발생했습니다. 다시 시도해주세요.")
                    .queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }
        log.info("사이트 삭제됨: {} ({}) - 사용자: {}", site.getName(), site.getUrl(), userId);

        hook.editOriginal("✅ 사이트가 삭제되었습니다: **" + site.getName() + "**").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleListSites(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();

        String userId = event.getUser().getId();
        List<Site> sites = siteRepository.findByDiscordUserId(userId);

        if (sites.isEmpty()) {
            hook.editOriginal("📋 등록된 사이트가 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Map<Long, Long> keywordCountBySite = keywordRepository.findBySiteIn(sites).stream()
                .collect(Collectors.groupingBy(k -> k.getSite().getId(), Collectors.counting()));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 등록된 사이트 목록")
                .setColor(Color.BLUE)
                .setDescription("총 " + sites.size() + "개의 사이트");

        for (Site site : sites) {
            String status = site.getActive() ? "✅ 활성" : "⏸️ 비활성";
            long keywordCount = keywordCountBySite.getOrDefault(site.getId(), 0L);
            String keywordInfo = keywordCount == 0 ? "전체 페이지 감지" : keywordCount + "개 키워드";

            embed.addField(
                site.getName(),
                "**URL:** " + site.getUrl() + "\n" +
                "**상태:** " + status + "\n" +
                "**키워드:** " + keywordInfo,
                false
            );
        }

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleToggleSite(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        if (name == null) {
            replyError(event, "❌ 사이트 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);
        site.setActive(!site.getActive());
        try {
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("사이트 상태 변경 실패: {} - 사용자: {}", site.getName(), userId, e);
            hook.editOriginal("❌ 사이트 상태 변경 중 오류가 발생했습니다. 다시 시도해주세요.")
                    .queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }
        String status = site.getActive() ? "✅ 활성화" : "⏸️ 비활성화";
        hook.editOriginal(status + "되었습니다: **" + site.getName() + "**").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleAddKeyword(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);
        String keywordText = event.getOption("keyword", OptionMapping::getAsString);

        if (siteName == null || keywordText == null) {
            replyError(event, "❌ 사이트 이름과 키워드를 모두 입력해주세요.");
            return;
        }

        String trimmedKeyword = keywordText.strip();
        if (trimmedKeyword.isEmpty()) {
            replyError(event, "❌ 키워드를 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);

        try {
            keywordService.addKeywordToSite(site, trimmedKeyword);
        } catch (IllegalArgumentException e) {
            hook.editOriginal("❌ " + e.getMessage()).queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        } catch (Exception e) {
            log.error("키워드 추가 실패: {} 사이트: {} - 사용자: {}", trimmedKeyword, siteName, userId, e);
            hook.editOriginal("❌ 키워드 추가 중 오류가 발생했습니다. 다시 시도해주세요.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }

        log.info("키워드 추가됨: {} 사이트: {} - 사용자: {}", trimmedKeyword, siteName, userId);
        hook.editOriginal("✅ 키워드가 추가되었습니다: **" + trimmedKeyword + "** → 사이트: **" + site.getName() + "**")
                .queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleRemoveKeyword(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);
        String keywordText = event.getOption("keyword", OptionMapping::getAsString);

        if (siteName == null || keywordText == null) {
            replyError(event, "❌ 사이트 이름과 키워드를 모두 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);
        List<Keyword> keywords = keywordRepository.findBySite(site);

        Optional<Keyword> keywordOpt = keywords.stream()
                .filter(k -> k.getKeyword().equals(keywordText.strip()))
                .findFirst();

        if (keywordOpt.isEmpty()) {
            hook.editOriginal("❌ 해당 키워드를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        try {
            keywordService.removeKeywordFromSite(site, keywordOpt.get());
        } catch (Exception e) {
            log.error("키워드 삭제 실패: {} 사이트: {} - 사용자: {}", keywordText, siteName, userId, e);
            hook.editOriginal("❌ 키워드 삭제 중 오류가 발생했습니다. 다시 시도해주세요.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }

        hook.editOriginal("✅ 키워드가 삭제되었습니다: **" + keywordText.strip() + "**").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleListKeywords(SlashCommandInteractionEvent event) {
        String siteName = event.getOption("site", OptionMapping::getAsString);

        if (siteName == null) {
            replyError(event, "❌ 사이트 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, siteName);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);
        List<Keyword> keywords = keywordRepository.findBySite(site);

        if (keywords.isEmpty()) {
            hook.editOriginal("📋 등록된 키워드가 없습니다. (전체 페이지 감지 모드)").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
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

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();

        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserId(userId);
        long totalSites = sites.size();
        long activeSites = sites.stream().filter(Site::getActive).count();
        long totalKeywords = sites.isEmpty() ? 0L : keywordRepository.countBySiteIn(sites);

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
                .addField("", "", true)
                .setFooter("웹 모니터링 시스템 v1.0");

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleAddProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String url = event.getOption("url", OptionMapping::getAsString);
        String priorityStr = event.getOption("priority", "NORMAL", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", 3, OptionMapping::getAsInt);
        String selector = event.getOption("selector", OptionMapping::getAsString);

        if (name == null || url == null) {
            replyError(event, "❌ 제품 이름과 URL을 모두 입력해주세요.");
            return;
        }

        Product.Priority priority;
        try {
            priority = Product.Priority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            replyError(event, "❌ 우선순위는 URGENT 또는 NORMAL이어야 합니다.");
            return;
        }

        if (interval < 1) {
            replyError(event, "❌ 체크 주기는 최소 1분이어야 합니다.");
            return;
        }

        if (selector != null && !selector.strip().isEmpty()) {
            if (!isValidCssSelector(selector)) {
                replyError(event, "❌ 잘못된 CSS 셀렉터 형식입니다.\n예시: `.price`, `#stock-status`, `div.product-info`, `span[class='stock']`");
                return;
            }
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        if (productRepository.existsByDiscordUserIdAndUrl(userId, url)) {
            hook.editOriginal("❌ 이미 등록한 제품 URL입니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = Product.builder()
                .name(name)
                .url(url)
                .priority(priority)
                .checkIntervalMinutes(interval)
                .contentSelector(selector)
                .discordUserId(userId)
                .build();

        Product saved;
        try {
            saved = productService.createProduct(product);
        } catch (DataIntegrityViolationException e) {
            log.warn("제품 저장 중 중복 URL 감지 (TOCTOU): {} - 사용자: {}", url, userId);
            hook.editOriginal("❌ 이미 등록한 제품 URL입니다.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }
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

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleRemoveProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        if (name == null) {
            replyError(event, "❌ 제품 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 제품을 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = products.get(0);
        try {
            productService.deleteProduct(product.getId());
        } catch (Exception e) {
            log.error("제품 삭제 실패: {} - 사용자: {}", product.getName(), userId, e);
            hook.editOriginal("❌ 제품 삭제 중 오류가 발생했습니다. 다시 시도해주세요.")
                    .queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }
        log.info("제품 삭제됨: {} ({}) - 사용자: {}", product.getName(), product.getUrl(), userId);

        hook.editOriginal("✅ 제품이 삭제되었습니다: **" + product.getName() + "**").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleListProducts(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();

        String userId = event.getUser().getId();
        List<Product> products = productRepository.findByDiscordUserId(userId);

        if (products.isEmpty()) {
            hook.editOriginal("📦 등록된 제품이 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📦 등록된 제품 목록")
                .setColor(Color.MAGENTA)
                .setDescription("총 " + products.size() + "개의 제품");

        for (Product product : products) {
            String status = product.getActive() ? "✅ 활성" : "⏸️ 비활성";
            String priority = product.getPriority() == Product.Priority.URGENT ? "🔴 긴급" : "🟢 일반";
            String stockStatus = DiscordConstants.formatStockStatus(product.getCurrentStatus());

            embed.addField(
                product.getName(),
                "**상태:** " + status + "\n" +
                "**우선순위:** " + priority + "\n" +
                "**재고:** " + stockStatus + "\n" +
                "**체크 주기:** " + product.getCheckIntervalMinutes() + "분",
                false
            );
        }

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleToggleProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        if (name == null) {
            replyError(event, "❌ 제품 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 제품을 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = products.get(0);

        Product toggled;
        try {
            toggled = productService.toggleProductActive(product.getId());
        } catch (Exception e) {
            log.error("제품 토글 실패: {}", product.getName(), e);
            hook.editOriginal("❌ 제품을 찾을 수 없습니다.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }

        String status = toggled.getActive() ? "✅ 활성화" : "⏸️ 비활성화";
        hook.editOriginal(status + "되었습니다: **" + product.getName() + "**").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleCheckProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        if (name == null) {
            replyError(event, "❌ 제품 이름을 입력해주세요.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 제품을 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = products.get(0);

        try {
            productMonitorService.checkProductNow(product.getId());
        } catch (Exception e) {
            log.error("제품 즉시 체크 실패: {}", product.getName(), e);
            hook.editOriginal("❌ 제품 확인 중 오류가 발생했습니다.").queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }

        // checkProductNow는 @Async라 즉시 반환. 재조회로 핸들러 시작 시점 스냅샷 대신 최신 커밋 값 표시
        Product fresh = productRepository.findById(product.getId()).orElse(product);
        String stockStatus = DiscordConstants.formatStockStatus(fresh.getCurrentStatus());

        String lastChecked = fresh.getLastCheckedAt() != null
                ? fresh.getLastCheckedAt().format(DiscordConstants.DISPLAY_FORMATTER)
                : "확인 기록 없음";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔍 체크 요청됨")
                .setColor(Color.YELLOW)
                .setDescription("백그라운드에서 재고 확인 요청이 접수되었습니다.")
                .addField("제품명", fresh.getName(), false)
                .addField("현재 상태 (이전 확인 기준)", stockStatus, true)
                .addField("가격", fresh.getCurrentPrice() != null ? fresh.getCurrentPrice() + "원" : "정보 없음", true)
                .setFooter("마지막 확인: " + lastChecked);

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleSetProductPriority(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String priorityStr = event.getOption("priority", OptionMapping::getAsString);

        if (name == null || priorityStr == null) {
            replyError(event, "❌ 제품 이름과 우선순위를 모두 입력해주세요.");
            return;
        }

        Product.Priority priority;
        try {
            priority = Product.Priority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            replyError(event, "❌ 우선순위는 URGENT 또는 NORMAL이어야 합니다.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 제품을 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = products.get(0);
        productService.setProductPriority(product.getId(), priority);

        String priorityText = priority == Product.Priority.URGENT ? "🔴 긴급 (10초 주기)" : "🟢 일반 (60초 주기)";
        hook.editOriginal("✅ 우선순위가 변경되었습니다: **" + product.getName() + "** → " + priorityText).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleSetProductInterval(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", OptionMapping::getAsInt);

        if (name == null || interval == null) {
            replyError(event, "❌ 제품 이름과 체크 주기를 모두 입력해주세요.");
            return;
        }

        if (interval < 1) {
            replyError(event, "❌ 체크 주기는 최소 1분이어야 합니다.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Product> products = productRepository.findByDiscordUserIdAndName(userId, name);

        if (products.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 제품을 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Product product = products.get(0);
        productService.setProductCheckInterval(product.getId(), interval);

        hook.editOriginal("✅ 체크 주기가 변경되었습니다: **" + product.getName() + "** → " + interval + "분").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleSetSiteInterval(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        Integer interval = event.getOption("interval", OptionMapping::getAsInt);

        if (name == null || interval == null) {
            replyError(event, "❌ 사이트 이름과 체크 주기를 모두 입력해주세요.");
            return;
        }

        if (interval < 1) {
            replyError(event, "❌ 체크 주기는 최소 1분이어야 합니다.");
            return;
        }

        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();
        String userId = event.getUser().getId();

        List<Site> sites = siteRepository.findByDiscordUserIdAndName(userId, name);

        if (sites.isEmpty()) {
            hook.editOriginal("❌ 해당 이름의 사이트를 찾을 수 없습니다.").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
            return;
        }

        Site site = sites.get(0);
        site.setCheckIntervalMinutes(interval);
        try {
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("사이트 체크 주기 변경 실패: {} - 사용자: {}", site.getName(), userId, e);
            hook.editOriginal("❌ 체크 주기 변경 중 오류가 발생했습니다. 다시 시도해주세요.")
                    .queue(null, ex -> log.error("Discord 응답 실패: {}", ex.getMessage()));
            return;
        }
        hook.editOriginal("✅ 체크 주기가 변경되었습니다: **" + site.getName() + "** → " + interval + "분").queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(null, e -> log.error("Discord Defer 실패: {}", e.getMessage()));
        InteractionHook hook = event.getHook();

        String category = event.getOption("category", "all", OptionMapping::getAsString);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 명령어 도움말")
                .setColor(Color.CYAN);

        if (category.equals("all") || category.equals("site")) {
            embed.addField("🌐 사이트 관리",
                "• `/add <url> <name> [keyword]` - 사이트 추가 (keyword 없으면 전체글 알림)\n" +
                "• `/remove <name>` - 사이트 삭제\n" +
                "• `/list` - 사이트 목록\n" +
                "• `/toggle <name>` - 사이트 활성화/비활성화\n" +
                "• `/set-interval <name> <분>` - 사이트 체크 주기 설정",
                false
            );
        }

        if (category.equals("all") || category.equals("keyword")) {
            embed.addField("🔍 키워드 관리",
                "• `/keyword-add <site> <keyword>` - 기존 사이트에 키워드 추가\n" +
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

        embed.setFooter("우선순위: URGENT (10초 주기) / NORMAL (60초 주기)");

        hook.editOriginalEmbeds(embed.build()).queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private void replyError(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true)
                .queue(null, e -> log.error("Discord 응답 실패: {}", e.getMessage()));
    }

    private boolean isValidCssSelector(String selector) {
        if (selector == null || selector.strip().isEmpty()) {
            return false;
        }

        try {
            org.jsoup.nodes.Document testDoc = org.jsoup.Jsoup.parse("<html><body></body></html>");
            testDoc.select(selector);
            return true;
        } catch (Exception e) {
            log.debug("셀렉터 검증 실패: {}", selector, e);
            return false;
        }
    }
}
