package com.webmonitor.discord;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.ProductRepository;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.service.KeywordService;
import com.webmonitor.service.ProductMonitorService;
import com.webmonitor.service.ProductService;
import com.webmonitor.service.SiteService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscordCommandHandlerTest {

    @Mock private SiteRepository siteRepository;
    @Mock private KeywordRepository keywordRepository;
    @Mock private ProductRepository productRepository;
    @Mock private KeywordService keywordService;
    @Mock private ProductService productService;
    @Mock private ProductMonitorService productMonitorService;
    @Mock private SiteService siteService;

    @InjectMocks
    private DiscordCommandHandler commandHandler;

    @Mock private SlashCommandInteractionEvent event;
    @Mock private ReplyCallbackAction deferAction;
    @Mock private InteractionHook hook;
    @Mock @SuppressWarnings("rawtypes") private WebhookMessageEditAction editAction;
    @Mock private ReplyCallbackAction replyAction;
    @Mock private User user;

    private static final String TEST_USER_ID = "123456789012345678";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(event.deferReply(anyBoolean())).thenReturn(deferAction);
        lenient().doNothing().when(deferAction).queue(any(), any());

        lenient().when(event.getHook()).thenReturn(hook);
        lenient().when(hook.editOriginal(anyString())).thenReturn(editAction);
        lenient().when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
        lenient().doNothing().when(editAction).queue(any(), any());

        lenient().when(event.reply(anyString())).thenReturn(replyAction);
        lenient().when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        lenient().doNothing().when(replyAction).queue(any(), any());

        lenient().when(event.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_USER_ID);
    }

    // ===============================
    // /add 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/add - 정상 케이스: 사이트 추가 성공")
    void handleAddSite_Success_ShouldCreateSiteAndReply() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).save(any(Site.class));
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - URL이 null인 경우 ephemeral 에러 응답")
    void handleAddSite_WhenUrlIsNull_ShouldReplyError() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn(null);
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("URL"));
        verify(replyAction).setEphemeral(true);
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("/add - keyword 있는 경우 사이트+키워드 추가 성공")
    void handleAddSite_WithKeyword_ShouldCreateSiteAndKeywordAndReply() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트키워드");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).save(any(Site.class));
        verify(keywordService).addKeywordToSite(any(Site.class), eq("테스트키워드"));
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - keyword에 앞뒤 공백이 있어도 trimmed 값으로 키워드 저장")
    void handleAddSite_WithKeywordHavingWhitespace_ShouldPassTrimmedKeyword() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("  테스트키워드  ");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        commandHandler.onSlashCommandInteraction(event);

        verify(keywordService).addKeywordToSite(any(Site.class), eq("테스트키워드"));
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - 유니코드 공백(EM SPACE U+2003) 포함 키워드도 strip()으로 정규화되어 저장")
    void handleAddSite_WithUnicodeWhitespaceKeyword_ShouldPassStrippedKeyword() {
        //   = non-breaking space — trim()은 제거 못함, strip()만 제거
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn(" 테스트키워드 ");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        commandHandler.onSlashCommandInteraction(event);

        verify(keywordService).addKeywordToSite(any(Site.class), eq("테스트키워드"));
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - 키워드 저장 실패 시 사이트 롤백 후 에러 응답")
    void handleAddSite_WhenKeywordServiceThrows_ShouldDeleteOrphanSiteAndReplyError() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트키워드");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);
        doThrow(new RuntimeException("DB 오류")).when(keywordService).addKeywordToSite(any(Site.class), eq("테스트키워드"));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).delete(any(Site.class));
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - 공백만 있는 keyword 입력 시 전체글 알림 모드로 등록 (keywordService 호출 없음)")
    void handleAddSite_WithBlankOnlyKeyword_ShouldRegisterAsContentChangeMode() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("   ");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);

        Site savedSite = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).save(any(Site.class));
        verify(keywordService, never()).addKeywordToSite(any(), any());
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - 키워드 저장 실패 + 사이트 롤백도 실패해도 에러 응답은 반드시 전송")
    void handleAddSite_WhenKeywordServiceThrowsAndDeleteThrows_ShouldStillReplyError() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트키워드");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);
        when(siteRepository.save(any(Site.class))).thenReturn(Site.builder().id(1L).build());
        doThrow(new RuntimeException("키워드 DB 오류")).when(keywordService).addKeywordToSite(any(Site.class), eq("테스트키워드"));
        doThrow(new RuntimeException("삭제 DB 오류")).when(siteRepository).delete(any(Site.class));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/add - 중복 URL 등록 시도 시 에러 응답")
    void handleAddSite_WhenUrlAlreadyExists_ShouldReplyError() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(true);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("이미 등록"));
        verify(siteRepository, never()).save(any());
    }

    // ===============================
    // /remove 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/remove - 정상 케이스: 사이트 삭제 성공")
    void handleRemoveSite_Success_ShouldDeleteSiteAndReply() {
        when(event.getName()).thenReturn("remove");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).delete(site);
        verify(hook).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/remove - 삭제 중 예외 발생 시 에러 응답 (Discord 무응답 방지)")
    void handleRemoveSite_WhenDeleteThrows_ShouldStillReplyError() {
        when(event.getName()).thenReturn("remove");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        doThrow(new RuntimeException("DB 오류")).when(siteRepository).delete(any(Site.class));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/remove - 사이트를 찾을 수 없는 경우 에러 응답")
    void handleRemoveSite_WhenSiteNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("remove");
        when(event.getOption(eq("name"), any())).thenReturn("존재하지않는사이트");
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(siteRepository, never()).delete(any());
    }

    // ===============================
    // /list 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/list - 사이트가 존재할 때 목록 표시")
    void handleListSites_WhenSitesExist_ShouldReplyWithEmbed() {
        when(event.getName()).thenReturn("list");

        Site site1 = Site.builder().id(1L).name("사이트1").url("https://example1.com").active(true).build();
        Site site2 = Site.builder().id(2L).name("사이트2").url("https://example2.com").active(false).build();

        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Arrays.asList(site1, site2));
        when(keywordRepository.findBySiteIn(anyList())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/list - 등록된 사이트가 없을 때 메시지 표시")
    void handleListSites_WhenNoSites_ShouldReplyEmptyMessage() {
        when(event.getName()).thenReturn("list");
        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("등록된 사이트가 없습니다"));
    }

    // ===============================
    // /toggle 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/toggle - 정상 케이스: 활성화 상태 토글 성공")
    void handleToggleSite_Success_ShouldToggleActiveStatus() {
        when(event.getName()).thenReturn("toggle");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").active(true).build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).save(site);
        verify(hook).editOriginal(anyString());
    }

    @Test
    @DisplayName("/toggle - save 중 예외 발생 시 에러 응답 (Discord 무응답 방지)")
    void handleToggleSite_WhenSaveThrows_ShouldStillReplyError() {
        when(event.getName()).thenReturn("toggle");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").active(true).build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        doThrow(new RuntimeException("DB 오류")).when(siteRepository).save(any(Site.class));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("활성화"));
        verify(hook, never()).editOriginal(contains("비활성화"));
    }

    // ===============================
    // /keyword-add 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-add - 정상 케이스: 키워드 추가 성공")
    void handleAddKeyword_Success_ShouldAddKeywordAndReply() {
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(keywordService).addKeywordToSite(any(Site.class), eq("테스트 키워드"));
        verify(hook).editOriginal(contains("추가되었습니다"));
    }

    @Test
    @DisplayName("/keyword-add - addKeywordToSite 예외 발생 시 에러 응답")
    void handleAddKeyword_WhenServiceThrows_ShouldReplyError() {
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        doThrow(new RuntimeException("DB 오류")).when(keywordService).addKeywordToSite(any(Site.class), anyString());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("추가되었습니다"));
    }

    @Test
    @DisplayName("/keyword-add - 사이트 이름 누락 시 ephemeral 에러 응답 (deferReply 이전)")
    void handleAddKeyword_WhenSiteNameNull_ShouldReplyEphemeralError() {
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn(null);
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("사이트 이름과 키워드를 모두 입력해주세요"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("/keyword-add - 공백만 있는 키워드 입력 시 ephemeral 에러 응답 (deferReply 이전)")
    void handleAddKeyword_WhenKeywordBlank_ShouldReplyEphemeralError() {
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("   ");

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("키워드를 입력해주세요"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("/keyword-add - 이미 등록된 키워드 시 사용자 친화적 에러 응답")
    void handleAddKeyword_WhenDuplicateKeyword_ShouldReplyDuplicateError() {
        when(event.getName()).thenReturn("keyword-add");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        doThrow(new IllegalArgumentException("이미 등록된 키워드입니다: 테스트 키워드"))
                .when(keywordService).addKeywordToSite(any(Site.class), anyString());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("이미 등록된 키워드입니다"));
        verify(hook, never()).editOriginal(contains("추가되었습니다"));
    }

    // ===============================
    // /keyword-remove 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-remove - 정상 케이스: 키워드 삭제 성공")
    void handleRemoveKeyword_Success_ShouldDeleteKeywordAndReply() {
        when(event.getName()).thenReturn("keyword-remove");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        Keyword keyword = Keyword.builder().id(1L).keyword("테스트 키워드").site(site).build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(List.of(keyword));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(keywordService).removeKeywordFromSite(any(Site.class), eq(keyword));
        verify(hook).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/keyword-remove - removeKeywordFromSite 예외 발생 시 에러 응답")
    void handleRemoveKeyword_WhenServiceThrows_ShouldReplyError() {
        when(event.getName()).thenReturn("keyword-remove");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn("테스트 키워드");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        Keyword keyword = Keyword.builder().id(1L).keyword("테스트 키워드").site(site).build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(List.of(keyword));
        doThrow(new RuntimeException("DB 오류")).when(keywordService).removeKeywordFromSite(any(Site.class), eq(keyword));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/keyword-remove - 앞뒤 공백 포함 입력으로 정규화된 키워드 삭제 성공")
    void handleRemoveKeyword_WithWhitespacePaddedInput_ShouldFindAndDeleteKeyword() {
        when(event.getName()).thenReturn("keyword-remove");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("keyword"), any())).thenReturn(" 테스트 키워드 ");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        Keyword keyword = Keyword.builder().id(1L).keyword("테스트 키워드").site(site).build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(List.of(keyword));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(keywordService).removeKeywordFromSite(any(Site.class), eq(keyword));
        verify(hook).editOriginal(contains("삭제되었습니다"));
    }

    // ===============================
    // /keyword-list 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/keyword-list - 키워드가 존재할 때 목록 표시")
    void handleListKeywords_WhenKeywordsExist_ShouldReplyWithEmbed() {
        when(event.getName()).thenReturn("keyword-list");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        Keyword keyword1 = Keyword.builder().id(1L).keyword("키워드1").site(site).build();
        Keyword keyword2 = Keyword.builder().id(2L).keyword("키워드2").site(site).build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(Arrays.asList(keyword1, keyword2));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/keyword-list - 키워드가 없을 때 메시지 표시")
    void handleListKeywords_WhenNoKeywords_ShouldReplyEmptyMessage() {
        when(event.getName()).thenReturn("keyword-list");
        when(event.getOption(eq("site"), any())).thenReturn("테스트 사이트");

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();

        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        when(keywordRepository.findBySite(site)).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("등록된 키워드가 없습니다"));
    }

    @Test
    @DisplayName("/keyword-list - 사이트 이름 누락 시 ephemeral 에러 응답 (deferReply 이전)")
    void handleListKeywords_WhenSiteNameNull_ShouldReplyEphemeralError() {
        when(event.getName()).thenReturn("keyword-list");
        when(event.getOption(eq("site"), any())).thenReturn(null);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("사이트 이름을 입력해주세요"));
        verify(replyAction).setEphemeral(true);
    }

    // ===============================
    // /status 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/status - 정상 케이스: 시스템 상태 표시")
    void handleStatus_ShouldReplyWithSystemStatus() {
        when(event.getName()).thenReturn("status");

        Site site1 = Site.builder().id(1L).name("사이트1").url("https://example1.com").active(true).build();
        Site site2 = Site.builder().id(2L).name("사이트2").url("https://example2.com").active(false).build();

        when(siteRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Arrays.asList(site1, site2));
        when(keywordRepository.countBySiteIn(anyList())).thenReturn(3L);
        when(productRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    // ===============================
    // /product-add 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-add - 정상 케이스: 제품 추가 성공")
    void handleAddProduct_Success_ShouldCreateProductAndReply() {
        when(event.getName()).thenReturn("product-add");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("url"), any())).thenReturn("https://shop.example.com/item");
        when(event.getOption(eq("priority"), eq("NORMAL"), any())).thenReturn("NORMAL");
        when(event.getOption(eq("interval"), eq(3), any())).thenReturn(3);
        when(event.getOption(eq("selector"), any())).thenReturn(null);
        when(productRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://shop.example.com/item")).thenReturn(false);

        Product saved = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item")
                .priority(Product.Priority.NORMAL).checkIntervalMinutes(3).build();
        when(productService.createProduct(any(Product.class))).thenReturn(saved);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productService).createProduct(any(Product.class));
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/product-add - URL이 null인 경우 ephemeral 에러 응답")
    void handleAddProduct_WhenUrlIsNull_ShouldReplyError() {
        when(event.getName()).thenReturn("product-add");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("url"), any())).thenReturn(null);
        when(event.getOption(eq("priority"), eq("NORMAL"), any())).thenReturn("NORMAL");
        when(event.getOption(eq("interval"), eq(3), any())).thenReturn(3);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("이름과 URL"));
        verify(replyAction).setEphemeral(true);
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("/product-add - 체크 주기 1분 미만 입력 시 ephemeral 에러 응답")
    void handleAddProduct_WhenIntervalTooShort_ShouldReplyError() {
        when(event.getName()).thenReturn("product-add");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("url"), any())).thenReturn("https://shop.example.com/item");
        when(event.getOption(eq("priority"), eq("NORMAL"), any())).thenReturn("NORMAL");
        when(event.getOption(eq("interval"), eq(3), any())).thenReturn(0);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("최소 1분"));
        verify(replyAction).setEphemeral(true);
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("/product-add - 중복 URL 등록 시도 시 에러 응답")
    void handleAddProduct_WhenUrlAlreadyExists_ShouldReplyError() {
        when(event.getName()).thenReturn("product-add");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("url"), any())).thenReturn("https://shop.example.com/item");
        when(event.getOption(eq("priority"), eq("NORMAL"), any())).thenReturn("NORMAL");
        when(event.getOption(eq("interval"), eq(3), any())).thenReturn(3);
        when(event.getOption(eq("selector"), any())).thenReturn(null);
        when(productRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://shop.example.com/item")).thenReturn(true);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("이미 등록한 제품"));
        verify(productService, never()).createProduct(any());
    }

    // ===============================
    // /product-remove 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-remove - 정상 케이스: 제품 삭제 성공")
    void handleRemoveProduct_Success_ShouldDeleteProductAndReply() {
        when(event.getName()).thenReturn("product-remove");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productService).deleteProduct(1L);
        verify(hook).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/product-remove - 삭제 중 예외 발생 시 에러 응답 (Discord 무응답 방지)")
    void handleRemoveProduct_WhenDeleteThrows_ShouldStillReplyError() {
        when(event.getName()).thenReturn("product-remove");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        doThrow(new RuntimeException("DB 오류")).when(productService).deleteProduct(1L);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("삭제되었습니다"));
    }

    @Test
    @DisplayName("/product-remove - 제품을 찾을 수 없는 경우 에러 응답")
    void handleRemoveProduct_WhenNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("product-remove");
        when(event.getOption(eq("name"), any())).thenReturn("없는제품");
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(productService, never()).deleteProduct(any());
    }

    // ===============================
    // /product-list 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-list - 제품이 존재할 때 목록 표시")
    void handleListProducts_WhenProductsExist_ShouldReplyWithEmbed() {
        when(event.getName()).thenReturn("product-list");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item")
                .active(true).priority(Product.Priority.NORMAL)
                .currentStatus(Product.StockStatus.UNKNOWN).checkIntervalMinutes(3).build();
        when(productRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(List.of(product));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/product-list - 등록된 제품이 없을 때 메시지 표시")
    void handleListProducts_WhenNoProducts_ShouldReplyEmptyMessage() {
        when(event.getName()).thenReturn("product-list");
        when(productRepository.findByDiscordUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("등록된 제품이 없습니다"));
    }

    // ===============================
    // /product-check 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-check - 정상 케이스: 제품 확인 성공")
    void handleCheckProduct_Success_ShouldCheckAndReplyWithEmbed() {
        when(event.getName()).thenReturn("product-check");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item")
                .currentStatus(Product.StockStatus.IN_STOCK).build();

        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        doNothing().when(productMonitorService).checkProductNow(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productMonitorService).checkProductNow(1L);
        verify(productRepository).findById(1L);
        verify(hook).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("/product-check - 제품을 찾을 수 없는 경우 에러 응답")
    void handleCheckProduct_WhenProductNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("product-check");
        when(event.getOption(eq("name"), any())).thenReturn("없는제품");
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(productMonitorService, never()).checkProductNow(any());
    }

    @Test
    @DisplayName("/product-check - 체크 중 예외 발생 시 에러 응답")
    void handleCheckProduct_WhenCheckFails_ShouldReplyError() {
        when(event.getName()).thenReturn("product-check");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        doThrow(new RuntimeException("크롤링 실패")).when(productMonitorService).checkProductNow(1L);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
    }

    @Test
    @DisplayName("/product-check - 제품 이름 누락 시 ephemeral 에러 응답 (deferReply 이전)")
    void handleCheckProduct_WhenNameNull_ShouldReplyEphemeralError() {
        when(event.getName()).thenReturn("product-check");
        when(event.getOption(eq("name"), any())).thenReturn(null);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("제품 이름을 입력해주세요"));
        verify(replyAction).setEphemeral(true);
    }

    // ===============================
    // /product-toggle 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-toggle - 정상 케이스: 제품 활성화 상태 토글 성공")
    void handleToggleProduct_Success_ShouldToggleActiveStatus() {
        when(event.getName()).thenReturn("product-toggle");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").active(true).build();
        Product toggled = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").active(false).build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        when(productService.toggleProductActive(1L)).thenReturn(toggled);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productService).toggleProductActive(1L);
        verify(hook).editOriginal(contains("비활성화"));
    }

    @Test
    @DisplayName("/product-toggle - 제품을 찾을 수 없는 경우 에러 응답")
    void handleToggleProduct_WhenNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("product-toggle");
        when(event.getOption(eq("name"), any())).thenReturn("없는제품");
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(productService, never()).toggleProductActive(any());
    }

    @Test
    @DisplayName("/product-toggle - toggleProductActive 예외 발생 시 에러 응답 (동시 삭제 방어)")
    void handleToggleProduct_WhenServiceThrows_ShouldReplyError() {
        when(event.getName()).thenReturn("product-toggle");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").active(true).build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        when(productService.toggleProductActive(1L)).thenThrow(new IllegalArgumentException("제품을 찾을 수 없습니다. ID: 1"));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
    }

    // ===============================
    // /product-set-priority 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-set-priority - 정상 케이스: 우선순위 변경 성공")
    void handleSetProductPriority_Success_ShouldUpdatePriority() {
        when(event.getName()).thenReturn("product-set-priority");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("priority"), any())).thenReturn("URGENT");

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        when(productService.setProductPriority(1L, Product.Priority.URGENT)).thenReturn(product);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productService).setProductPriority(1L, Product.Priority.URGENT);
        verify(hook).editOriginal(contains("우선순위가 변경되었습니다"));
    }

    @Test
    @DisplayName("/product-set-priority - 제품을 찾을 수 없는 경우 에러 응답")
    void handleSetProductPriority_WhenProductNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("product-set-priority");
        when(event.getOption(eq("name"), any())).thenReturn("없는제품");
        when(event.getOption(eq("priority"), any())).thenReturn("URGENT");
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(productService, never()).setProductPriority(any(), any());
    }

    @Test
    @DisplayName("/product-set-priority - 잘못된 우선순위 입력 시 ephemeral 에러 응답")
    void handleSetProductPriority_WhenInvalidPriority_ShouldReplyError() {
        when(event.getName()).thenReturn("product-set-priority");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("priority"), any())).thenReturn("INVALID");

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("URGENT 또는 NORMAL"));
        verify(replyAction).setEphemeral(true);
        verify(productService, never()).setProductPriority(any(), any());
    }

    // ===============================
    // /product-set-interval 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/product-set-interval - 정상 케이스: 체크 주기 변경 성공")
    void handleSetProductInterval_Success_ShouldUpdateInterval() {
        when(event.getName()).thenReturn("product-set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("interval"), any())).thenReturn(10);

        Product product = Product.builder().id(1L).name("테스트 제품").url("https://shop.example.com/item").build();
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(product));
        when(productService.setProductCheckInterval(1L, 10)).thenReturn(product);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(productService).setProductCheckInterval(1L, 10);
        verify(hook).editOriginal(contains("체크 주기가 변경되었습니다"));
    }

    @Test
    @DisplayName("/product-set-interval - 제품을 찾을 수 없는 경우 에러 응답")
    void handleSetProductInterval_WhenProductNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("product-set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("없는제품");
        when(event.getOption(eq("interval"), any())).thenReturn(10);
        when(productRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(productService, never()).setProductCheckInterval(any(), any());
    }

    @Test
    @DisplayName("/product-set-interval - 1분 미만 입력 시 ephemeral 에러 응답")
    void handleSetProductInterval_WhenIntervalTooShort_ShouldReplyError() {
        when(event.getName()).thenReturn("product-set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("interval"), any())).thenReturn(0);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("최소 1분"));
        verify(replyAction).setEphemeral(true);
        verify(productService, never()).setProductCheckInterval(any(), any());
    }

    // ===============================
    // /set-interval 명령어 테스트
    // ===============================

    @Test
    @DisplayName("/set-interval - 정상 케이스: 사이트 체크 주기 변경 성공")
    void handleSetSiteInterval_Success_ShouldUpdateInterval() {
        when(event.getName()).thenReturn("set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("interval"), any())).thenReturn(15);

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(siteRepository).save(site);
        verify(hook).editOriginal(contains("체크 주기가 변경되었습니다"));
    }

    @Test
    @DisplayName("/set-interval - 사이트를 찾을 수 없는 경우 에러 응답")
    void handleSetSiteInterval_WhenSiteNotFound_ShouldReplyError() {
        when(event.getName()).thenReturn("set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("없는사이트");
        when(event.getOption(eq("interval"), any())).thenReturn(15);
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(Collections.emptyList());

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("찾을 수 없습니다"));
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("/set-interval - save 중 예외 발생 시 에러 응답 (Discord 무응답 방지)")
    void handleSetSiteInterval_WhenSaveThrows_ShouldStillReplyError() {
        when(event.getName()).thenReturn("set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("interval"), any())).thenReturn(15);

        Site site = Site.builder().id(1L).name("테스트 사이트").url("https://example.com").build();
        when(siteRepository.findByDiscordUserIdAndName(eq(TEST_USER_ID), anyString())).thenReturn(List.of(site));
        doThrow(new RuntimeException("DB 오류")).when(siteRepository).save(any(Site.class));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("오류가 발생했습니다"));
        verify(hook, never()).editOriginal(contains("체크 주기가 변경되었습니다"));
    }

    @Test
    @DisplayName("/set-interval - 1분 미만 입력 시 ephemeral 에러 응답")
    void handleSetSiteInterval_WhenIntervalTooShort_ShouldReplyError() {
        when(event.getName()).thenReturn("set-interval");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(event.getOption(eq("interval"), any())).thenReturn(0);

        commandHandler.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(anyBoolean());
        verify(event).reply(contains("최소 1분"));
        verify(replyAction).setEphemeral(true);
        verify(siteRepository, never()).save(any());
    }

    // ===============================
    // 알 수 없는 명령어 테스트
    // ===============================

    @Test
    @DisplayName("알 수 없는 명령어 처리")
    void onSlashCommandInteraction_WhenUnknownCommand_ShouldReplyError() {
        when(event.getName()).thenReturn("unknown-command");
        when(event.reply(anyString())).thenReturn(replyAction);

        commandHandler.onSlashCommandInteraction(event);

        verify(event).reply(contains("알 수 없는 명령어"));
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    // ===============================
    // TOCTOU 방어 테스트
    // ===============================

    @Test
    @DisplayName("/add - 동시 요청으로 DB unique 제약 위반 시 에러 응답 (TOCTOU)")
    void handleAddSite_WhenConcurrentDuplicateSave_ShouldReplyError() {
        when(event.getName()).thenReturn("add");
        when(event.getOption(eq("url"), any())).thenReturn("https://example.com");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 사이트");
        when(siteRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://example.com")).thenReturn(false);
        when(siteRepository.save(any(Site.class))).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("이미 등록한 URL"));
        verify(siteRepository).save(any(Site.class));
    }

    @Test
    @DisplayName("/product-add - 동시 요청으로 DB unique 제약 위반 시 에러 응답 (TOCTOU)")
    void handleAddProduct_WhenConcurrentDuplicateSave_ShouldReplyError() {
        when(event.getName()).thenReturn("product-add");
        when(event.getOption(eq("name"), any())).thenReturn("테스트 제품");
        when(event.getOption(eq("url"), any())).thenReturn("https://shop.example.com/item");
        when(event.getOption(eq("priority"), eq("NORMAL"), any())).thenReturn("NORMAL");
        when(event.getOption(eq("interval"), eq(3), any())).thenReturn(3);
        when(event.getOption(eq("selector"), any())).thenReturn(null);
        when(productRepository.existsByDiscordUserIdAndUrl(TEST_USER_ID, "https://shop.example.com/item")).thenReturn(false);
        when(productService.createProduct(any(Product.class))).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        commandHandler.onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal(contains("이미 등록한 제품 URL"));
        verify(productService).createProduct(any(Product.class));
    }
}
