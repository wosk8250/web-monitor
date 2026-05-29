package com.webmonitor.discord;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.service.ProductMonitorService;
import com.webmonitor.service.ProductService;
import com.webmonitor.service.SiteService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DiscordCommandHandler 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class DiscordCommandHandlerTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ProductMonitorService productMonitorService;

    @Mock
    private SiteService siteService;

    @InjectMocks
    private DiscordCommandHandler commandHandler;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private ReplyCallbackAction replyAction;

    @Mock
    private ReplyCallbackAction replyEmbedsAction;

    @Mock
    private User user;

    private static final String TEST_USER_ID = "123456789012345678";

    @BeforeEach
    void setUp() {
        // 공통 모킹 설정: reply() 체이닝
        lenient().when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        lenient().when(replyEmbedsAction.setEphemeral(anyBoolean())).thenReturn(replyEmbedsAction);

        // Discord User ID 모킹
        lenient().when(event.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_USER_ID);
    }

    // ===============================
    // /add 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/add - 정상 케이스: 사이트 추가 성공")
    void handleAddSite_Success_ShouldCreateSiteAndReply() {
        // Given
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyEmbedsAction);

        Site savedSite = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(siteRepository).save(any(Site.class));
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyEmbedsAction).queue();
    }

    @Test
    @DisplayName("/add - URL이 null인 경우 에러 응답")
    void handleAddSite_WhenUrlIsNull_ShouldReplyError() {
        // Given
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn(null);
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("URL"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("/add - 중복 URL 등록 시도 시 에러 응답")
    void handleAddSite_WhenUrlAlreadyExists_ShouldReplyError() {
        // Given
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(true);
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("이미 등록"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(siteRepository, never()).save(any());
    }

    // ===============================
    // /remove 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/remove - 정상 케이스: 사이트 삭제 성공")
    void handleRemoveSite_Success_ShouldDeleteSiteAndReply() {
        // Given
        when(event.getName()).thenReturn("remove");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(siteRepository).delete(site);
        verify(event).reply(contains("삭제되었습니다"));
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("/remove - 사이트를 찾을 수 없는 경우 에러 응답")
    void handleRemoveSite_WhenSiteNotFound_ShouldReplyError() {
        // Given
        when(event.getName()).thenReturn("remove");
        when(event.getOption(eq("name"), any())).thenReturn("존재하지않는사이트");
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("찾을 수 없습니다"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(siteRepository, never()).delete(any());
    }

    // ===============================
    // /list 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/list - 사이트가 존재할 때 목록 표시")
    void handleListSites_WhenSitesExist_ShouldReplyWithEmbed() {
        // Given
        when(event.getName()).thenReturn("list");

        Site site1 = Site.builder()
                .id(1L)
                .name("사이트1")
                .url("https://example1.com")
                .active(true)
                .build();
        Site site2 = Site.builder()
                .id(2L)
                .name("사이트2")
                .url("https://example2.com")
                .active(false)
                .build();

        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Arrays.asList(site1, site2));
        when(keywordRepository.findBySite(any())).thenReturn(Collections.emptyList());
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyEmbedsAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyEmbedsAction).queue();
    }

    @Test
    @DisplayName("/list - 등록된 사이트가 없을 때 메시지 표시")
    void handleListSites_WhenNoSites_ShouldReplyEmptyMessage() {
        // Given
        when(event.getName()).thenReturn("list");
        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("등록된 사이트가 없습니다"));
        verify(replyAction).queue();
    }

    // ===============================
    // /toggle 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/toggle - 정상 케이스: 활성화 상태 토글 성공")
    void handleToggleSite_Success_ShouldToggleActiveStatus() {
        // Given
        when(event.getName()).thenReturn("toggle");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .active(true)
                .build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(siteRepository).save(site);
        verify(event).reply(anyString());
        verify(replyAction).queue();
    }

    // ===============================
    // /keyword-add 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-add - 정상 케이스: 키워드 추가 성공")
    void handleAddKeyword_Success_ShouldCreateKeywordAndReply() {
        // Given
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .detectContentChange(true)
                .build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(keywordRepository).save(any(Keyword.class));
        verify(event).reply(contains("추가되었습니다"));
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("/keyword-add - 사이트를 찾을 수 없는 경우 에러 응답")
    void handleAddKeyword_WhenSiteNotFound_ShouldReplyError() {
        // Given
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("존재하지않는사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("찾을 수 없습니다"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(keywordRepository, never()).save(any());
    }

    // ===============================
    // /keyword-remove 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-remove - 정상 케이스: 키워드 삭제 성공")
    void handleRemoveKeyword_Success_ShouldDeleteKeywordAndReply() {
        // Given
        when(event.getName()).thenReturn("keyword-remove");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .build();
        Keyword keyword = Keyword.builder()
                .id(1L)
                .keyword("테스트 키워드")
                .site(site)
                .build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(List.of(keyword));
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(keywordRepository).delete(keyword);
        verify(event).reply(contains("삭제되었습니다"));
        verify(replyAction).queue();
    }

    // ===============================
    // /keyword-list 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-list - 키워드가 존재할 때 목록 표시")
    void handleListKeywords_WhenKeywordsExist_ShouldReplyWithEmbed() {
        // Given
        when(event.getName()).thenReturn("keyword-list");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .build();
        Keyword keyword1 = Keyword.builder()
                .id(1L)
                .keyword("키워드1")
                .site(site)
                .build();
        Keyword keyword2 = Keyword.builder()
                .id(2L)
                .keyword("키워드2")
                .site(site)
                .build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(Arrays.asList(keyword1, keyword2));
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyEmbedsAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyEmbedsAction).queue();
    }

    @Test
    @DisplayName("/keyword-list - 키워드가 없을 때 메시지 표시")
    void handleListKeywords_WhenNoKeywords_ShouldReplyEmptyMessage() {
        // Given
        when(event.getName()).thenReturn("keyword-list");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder()
                .id(1L)
                .name("테스트 사이트")
                .url("https://example.com")
                .build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(Collections.emptyList());
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("등록된 키워드가 없습니다"));
        verify(replyAction).queue();
    }

    // ===============================
    // /status 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/status - 정상 케이스: 시스템 상태 표시")
    void handleStatus_ShouldReplyWithSystemStatus() {
        // Given
        when(event.getName()).thenReturn("status");

        Site site1 = Site.builder()
                .id(1L)
                .name("사이트1")
                .url("https://example1.com")
                .active(true)
                .build();
        Site site2 = Site.builder()
                .id(2L)
                .name("사이트2")
                .url("https://example2.com")
                .active(false)
                .build();

        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Arrays.asList(site1, site2));
        when(keywordRepository.findBySite(any())).thenReturn(Collections.emptyList());
        when(productRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyEmbedsAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyEmbedsAction).queue();
    }

    // ===============================
    // 알 수 없는 명령어 테스트
    // ===============================

    @Test
    @DisplayName("알 수 없는 명령어 처리")
    void onSlashCommandInteraction_WhenUnknownCommand_ShouldReplyError() {
        // Given
        when(event.getName()).thenReturn("unknown-command");
        when(event.reply(anyString())).thenReturn(replyAction);

        // When
        commandHandler.onSlashCommandInteraction(event);

        // Then
        verify(event).reply(contains("알 수 없는 명령어"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }
}
