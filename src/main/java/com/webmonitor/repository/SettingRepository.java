package com.webmonitor.repository;

import com.webmonitor.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Setting 엔티티에 대한 데이터베이스 접근을 담당하는 Repository
 * JpaRepository를 상속받아 기본 CRUD 메서드 제공
 */
@Repository // Spring의 Repository 컴포넌트로 등록
public interface SettingRepository extends JpaRepository<Setting, Long> {

    /**
     * 활성화된 설정 조회
     * @param enabled 활성화 여부
     * @return 활성화된 설정 (Optional)
     */
    Optional<Setting> findFirstByEnabled(Boolean enabled);

    /**
     * 디스코드 웹훅 URL이 설정된 항목 조회
     * @return 웹훅 URL이 null이 아닌 설정 (Optional)
     */
    Optional<Setting> findFirstByDiscordWebhookUrlIsNotNull();
}
