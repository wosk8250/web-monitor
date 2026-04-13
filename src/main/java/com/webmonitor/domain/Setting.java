package com.webmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 시스템 설정 정보를 저장하는 엔티티 (디스코드 웹훅 등)
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "settings") // 테이블 이름을 'settings'로 지정
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Setting {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @Column(length = 500) // 최대 길이 500자
    private String discordWebhookUrl; // 디스코드 웹훅 URL

    @Column(nullable = false) // null 불가
    private Boolean enabled = true; // 알림 활성화 여부 (기본값: true)

    @Column(length = 100) // 최대 길이 100자
    private String notificationTitle; // 알림 제목 (선택 사항)

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime createdAt; // 생성 시간

    @UpdateTimestamp // 엔티티 수정 시 자동으로 현재 시간 저장
    @Column(nullable = false) // null 불가
    private LocalDateTime updatedAt; // 수정 시간
}
