package com.webmonitor.service;

import com.webmonitor.domain.Setting;
import com.webmonitor.dto.SettingRequest;
import com.webmonitor.dto.SettingResponse;
import com.webmonitor.exception.resource.SettingNotFoundException;
import com.webmonitor.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SettingService {

    private final SettingRepository settingRepository;

    public List<SettingResponse> getAllSettings() {
        return settingRepository.findAll().stream()
                .map(SettingResponse::from)
                .toList();
    }

    public boolean hasAnySetting() {
        return settingRepository.count() > 0;
    }

    public SettingResponse getSettingById(Long id) {
        return settingRepository.findById(id)
                .map(SettingResponse::from)
                .orElseThrow(() -> new SettingNotFoundException(id));
    }

    public SettingResponse getActiveSetting() {
        return settingRepository.findFirstByEnabled(true)
                .map(SettingResponse::from)
                .orElseThrow(() -> new SettingNotFoundException("활성화된 설정이 없습니다."));
    }

    public java.util.Optional<SettingResponse> findActiveSetting() {
        return settingRepository.findFirstByEnabled(true)
                .map(SettingResponse::from);
    }

    @Transactional
    public SettingResponse createSetting(SettingRequest request) {
        Setting setting = Setting.builder()
                .discordWebhookUrl(request.getDiscordWebhookUrl())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        Setting saved = settingRepository.save(setting);
        log.info("설정 생성 완료 - ID: {}", saved.getId());
        return SettingResponse.from(saved);
    }

    @Transactional
    public SettingResponse updateSetting(Long id, SettingRequest request) {
        Setting setting = settingRepository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        if (request.getDiscordWebhookUrl() != null && !request.getDiscordWebhookUrl().isBlank()) {
            setting.setDiscordWebhookUrl(request.getDiscordWebhookUrl());
        }
        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }
        Setting saved = settingRepository.save(setting);
        log.info("설정 수정 완료 - ID: {}", id);
        return SettingResponse.from(saved);
    }

    @Transactional
    public void deleteSetting(Long id) {
        Setting setting = settingRepository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        settingRepository.delete(setting);
        log.info("설정 삭제 완료 - ID: {}", id);
    }

    public List<SettingResponse> getSettingsByEnabled(boolean enabled) {
        return settingRepository.findAll().stream()
                .filter(s -> s.getEnabled() != null && enabled == s.getEnabled())
                .map(SettingResponse::from)
                .toList();
    }
}
