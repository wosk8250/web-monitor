package com.webmonitor.controller;

import com.webmonitor.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * SSE(Server-Sent Events) 실시간 알림 컨트롤러
 */
@RestController // REST API 컨트롤러로 지정 (@Controller + @ResponseBody)
@RequestMapping("/api/sse") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class SseController {

    private final SseService sseService;

    /**
     * SSE 구독 엔드포인트
     * GET /api/sse/subscribe
     * 클라이언트가 이 엔드포인트에 연결하면 실시간 알림을 받을 수 있습니다.
     *
     * 사용 예시 (JavaScript):
     * const eventSource = new EventSource('/api/sse/subscribe');
     * eventSource.addEventListener('alert', (event) => {
     *     const alert = JSON.parse(event.data);
     *     console.log('알림 수신:', alert);
     * });
     *
     * @param clientId 클라이언트 ID (선택 사항, 없으면 자동 생성)
     * @return SseEmitter 객체
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam(required = false) String clientId) {
        // 클라이언트 ID가 없으면 UUID로 자동 생성
        if (clientId == null || clientId.isEmpty()) {
            clientId = UUID.randomUUID().toString();
        }

        log.info("SSE 구독 요청: 클라이언트 ID = {}", clientId);
        return sseService.subscribe(clientId);
    }

    /**
     * 현재 연결된 클라이언트 수 조회
     * GET /api/sse/connections
     * @return 연결된 클라이언트 수
     */
    @GetMapping("/connections")
    public ResponseEntity<ConnectionInfo> getConnections() {
        int count = sseService.getConnectedClientsCount();
        log.info("현재 연결된 SSE 클라이언트 수: {}", count);

        ConnectionInfo info = new ConnectionInfo(count);
        return ResponseEntity.ok(info);
    }

    /**
     * 모든 SSE 연결 종료 (관리자 전용)
     * DELETE /api/sse/connections
     * @return 처리 결과 메시지
     */
    @DeleteMapping("/connections")
    public ResponseEntity<String> closeAllConnections() {
        log.info("모든 SSE 연결 종료 요청");
        sseService.closeAllConnections();
        return ResponseEntity.ok("모든 SSE 연결이 종료되었습니다.");
    }

    /**
     * 연결 정보 DTO
     */
    private record ConnectionInfo(int connectedClients) {
    }
}
