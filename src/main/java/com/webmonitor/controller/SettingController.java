package com.webmonitor.controller;

import com.webmonitor.domain.Setting;
import com.webmonitor.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 시스템 설정 REST API 컨트롤러
 */
@RestController // REST API 컨트롤러로 지정 (@Controller + @ResponseBody)
@RequestMapping("/api/settings") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class SettingController {

    private final SettingRepository settingRepository;

    /**
     * 모든 설정 조회
     * GET /api/settings
     * @return 전체 설정 목록
     */
    @GetMapping
    public ResponseEntity<List<Setting>> getAllSettings() {
        log.info("GET /api/settings - 모든 설정 조회 요청");
        List<Setting> settings = settingRepository.findAll();
        return ResponseEntity.ok(settings);
    }

    /**
     * ID로 특정 설정 조회
     * GET /api/settings/{id}
     * @param id 설정 ID
     * @return 조회된 설정
     */
    @GetMapping("/{id}")
    public ResponseEntity<Setting> getSettingById(@PathVariable Long id) {
        log.info("GET /api/settings/{} - 설정 조회 요청", id);
        return settingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 활성화된 설정 조회
     * GET /api/settings/active
     * @return 활성화된 설정
     */
    @GetMapping("/active")
    public ResponseEntity<Setting> getActiveSetting() {
        log.info("GET /api/settings/active - 활성화된 설정 조회 요청");
        return settingRepository.findFirstByEnabled(true)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 새로운 설정 등록
     * POST /api/settings
     * @param setting 등록할 설정 정보 (JSON)
     * @return 생성된 설정 정보
     */
    @PostMapping
    public ResponseEntity<Setting> createSetting(@RequestBody Setting setting) {
        log.info("POST /api/settings - 설정 등록 요청");
        try {
            Setting createdSetting = settingRepository.save(setting);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSetting);
        } catch (Exception e) {
            log.error("설정 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 설정 정보 수정
     * PUT /api/settings/{id}
     * @param id 수정할 설정 ID
     * @param updatedSetting 수정할 설정 정보 (JSON)
     * @return 수정된 설정 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<Setting> updateSetting(@PathVariable Long id, @RequestBody Setting updatedSetting) {
        log.info("PUT /api/settings/{} - 설정 수정 요청", id);
        try {
            return settingRepository.findById(id)
                    .map(setting -> {
                        setting.setDiscordWebhookUrl(updatedSetting.getDiscordWebhookUrl());
                        setting.setEnabled(updatedSetting.getEnabled());
                        setting.setNotificationTitle(updatedSetting.getNotificationTitle());
                        Setting saved = settingRepository.save(setting);
                        return ResponseEntity.ok(saved);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("설정 수정 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 설정 삭제
     * DELETE /api/settings/{id}
     * @param id 삭제할 설정 ID
     * @return 삭제 성공 여부
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSetting(@PathVariable Long id) {
        log.info("DELETE /api/settings/{} - 설정 삭제 요청", id);
        try {
            if (!settingRepository.existsById(id)) {
                log.error("설정을 찾을 수 없습니다: ID = {}", id);
                return ResponseEntity.notFound().build();
            }

            settingRepository.deleteById(id);
            log.info("설정 삭제 완료: ID = {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("설정 삭제 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 설정 활성화/비활성화 토글
     * PATCH /api/settings/{id}/toggle
     * @param id 토글할 설정 ID
     * @return 변경된 설정 정보
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Setting> toggleSettingEnabled(@PathVariable Long id) {
        log.info("PATCH /api/settings/{}/toggle - 설정 활성화 상태 변경 요청", id);
        try {
            return settingRepository.findById(id)
                    .map(setting -> {
                        setting.setEnabled(!setting.getEnabled());
                        Setting saved = settingRepository.save(setting);
                        log.info("설정 활성화 상태 변경 완료: ID = {}, Enabled = {}", id, saved.getEnabled());
                        return ResponseEntity.ok(saved);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("설정 활성화 토글 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
