package com.webmonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Setting;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE(Server-Sent Events)를 사용한 실시간 알림 서비스
 */
@Service // Spring의 Service 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class SseService {

    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper; // JSON 변환을 위한 ObjectMapper

    // SseEmitter를 저장하는 맵 (클라이언트 ID별로 관리)
    // ConcurrentHashMap: 멀티스레드 환경에서 안전한 HashMap
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 모든 Emitter를 저장하는 리스트 (브로드캐스트용)
    // CopyOnWriteArrayList: 동시성 제어가 가능한 ArrayList
    private final CopyOnWriteArrayList<SseEmitter> emitterList = new CopyOnWriteArrayList<>();

    // SSE 연결 타임아웃 (30분 = 1800초)
    private static final Long DEFAULT_TIMEOUT = 1800000L;

    /**
     * 클라이언트의 SSE 구독 생성
     * @param clientId 클라이언트 고유 ID
     * @return SseEmitter 객체
     */
    public SseEmitter subscribe(String clientId) {
        // SseEmitter 생성 (타임아웃 설정)
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        log.info("SSE 구독 시작: 클라이언트 ID = {}", clientId);

        // 기존 연결이 있다면 제거
        if (emitters.containsKey(clientId)) {
            emitters.get(clientId).complete();
            emitters.remove(clientId);
            log.debug("기존 SSE 연결 제거: 클라이언트 ID = {}", clientId);
        }

        // 새로운 Emitter 등록
        emitters.put(clientId, emitter);
        emitterList.add(emitter);

        // 연결 완료 시 처리
        emitter.onCompletion(() -> {
            log.info("SSE 연결 완료: 클라이언트 ID = {}", clientId);
            emitters.remove(clientId);
            emitterList.remove(emitter);
        });

        // 타임아웃 시 처리
        emitter.onTimeout(() -> {
            log.warn("SSE 연결 타임아웃: 클라이언트 ID = {}", clientId);
            emitter.complete();
            emitters.remove(clientId);
            emitterList.remove(emitter);
        });

        // 에러 발생 시 처리
        emitter.onError((e) -> {
            log.error("SSE 연결 오류: 클라이언트 ID = {}, 오류 = {}", clientId, e.getMessage());
            emitter.complete();
            emitters.remove(clientId);
            emitterList.remove(emitter);
        });

        // 연결 확인용 초기 메시지 전송 (503 에러 방지)
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE 연결이 성공적으로 수립되었습니다."));
            log.info("SSE 초기 연결 메시지 전송 완료: 클라이언트 ID = {}", clientId);
        } catch (IOException e) {
            log.error("SSE 초기 메시지 전송 실패: {}", e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 특정 클라이언트에게 알림 전송
     * @param clientId 클라이언트 ID
     * @param alert 알림 정보
     */
    public void sendAlertToClient(String clientId, Alert alert) {
        SseEmitter emitter = emitters.get(clientId);

        if (emitter == null) {
            log.warn("SSE Emitter를 찾을 수 없습니다: 클라이언트 ID = {}", clientId);
            return;
        }

        try {
            // Alert 엔티티를 AlertResponse DTO로 변환
            AlertResponse alertResponse = AlertResponse.from(alert);

            // SSE 이벤트 전송
            emitter.send(SseEmitter.event()
                    .name("alert")
                    .data(alertResponse, MediaType.APPLICATION_JSON));

            log.info("SSE 알림 전송 완료: 클라이언트 ID = {}, 알림 ID = {}", clientId, alert.getId());

        } catch (IOException e) {
            log.error("SSE 알림 전송 실패: 클라이언트 ID = {}, 오류 = {}", clientId, e.getMessage());
            emitter.completeWithError(e);
            emitters.remove(clientId);
            emitterList.remove(emitter);
        }
    }

    /**
     * 모든 연결된 클라이언트에게 알림 브로드캐스트
     * @param alert 알림 정보
     */
    public void broadcastAlert(Alert alert) {
        log.info("SSE 알림 브로드캐스트 시작: 알림 ID = {}, 연결 수 = {}", alert.getId(), emitterList.size());

        if (emitterList.isEmpty()) {
            log.debug("연결된 SSE 클라이언트가 없습니다.");
            return;
        }

        // Alert 엔티티를 AlertResponse DTO로 변환
        AlertResponse alertResponse = AlertResponse.from(alert);

        // 삭제할 Emitter 목록 (전송 실패한 경우)
        CopyOnWriteArrayList<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        // 모든 클라이언트에게 전송
        emitterList.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(alertResponse, MediaType.APPLICATION_JSON));
                log.debug("SSE 알림 브로드캐스트 전송 성공");
            } catch (IOException e) {
                log.error("SSE 알림 브로드캐스트 전송 실패: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        });

        // 전송 실패한 Emitter 제거
        deadEmitters.forEach(emitter -> {
            emitter.complete();
            emitterList.remove(emitter);
            // emitters Map에서도 제거 (역방향 검색 필요)
            emitters.entrySet().removeIf(entry -> entry.getValue().equals(emitter));
        });

        log.info("SSE 알림 브로드캐스트 완료: 성공 = {}, 실패 = {}",
                emitterList.size() - deadEmitters.size(), deadEmitters.size());

        // 디스코드 웹훅으로도 전송
        sendDiscordWebhook(alert);
    }

    /**
     * 디스코드 웹훅으로 알림 전송
     * @param alert 알림 정보
     */
    public void sendDiscordWebhook(Alert alert) {
        try {
            // 활성화된 설정 조회
            Setting setting = settingRepository.findFirstByEnabled(true).orElse(null);

            if (setting == null || setting.getDiscordWebhookUrl() == null || setting.getDiscordWebhookUrl().isEmpty()) {
                log.debug("디스코드 웹훅이 설정되지 않았습니다.");
                return;
            }

            log.info("디스코드 웹훅 전송 시작: 알림 ID = {}", alert.getId());

            // 디스코드 메시지 페이로드 생성
            Map<String, Object> payload = Map.of(
                    "content", alert.getMessage(),
                    "embeds", new Object[]{
                            Map.of(
                                    "title", "🔔 키워드 감지 알림",
                                    "description", alert.getMessage(),
                                    "color", 5814783, // 파란색
                                    "fields", new Object[]{
                                            Map.of("name", "사이트", "value", alert.getSite().getName(), "inline", true),
                                            Map.of("name", "키워드", "value", alert.getKeyword().getKeyword(), "inline", true),
                                            Map.of("name", "URL", "value", alert.getDetectedUrl(), "inline", false)
                                    },
                                    "timestamp", alert.getDetectedAt().toString()
                            )
                    }
            );

            // JSON 문자열로 변환
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // HTTP POST 요청 전송
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(setting.getDiscordWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("디스코드 웹훅 전송 성공: 알림 ID = {}", alert.getId());
            } else {
                log.error("디스코드 웹훅 전송 실패: 상태 코드 = {}, 응답 = {}",
                        response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("디스코드 웹훅 전송 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 현재 연결된 클라이언트 수 조회
     * @return 연결된 클라이언트 수
     */
    public int getConnectedClientsCount() {
        return emitterList.size();
    }

    /**
     * 모든 SSE 연결 종료
     */
    public void closeAllConnections() {
        log.info("모든 SSE 연결 종료 시작: 연결 수 = {}", emitterList.size());

        emitterList.forEach(SseEmitter::complete);
        emitters.clear();
        emitterList.clear();

        log.info("모든 SSE 연결 종료 완료");
    }
}
