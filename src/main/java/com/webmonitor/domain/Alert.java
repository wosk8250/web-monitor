package com.webmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 키워드 감지 시 생성되는 알림 기록을 저장하는 엔티티
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "alerts") // 테이블 이름을 'alerts'로 지정
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Alert {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Site 정보를 조회)
    @JoinColumn(name = "site_id", nullable = false) // 외래 키 컬럼명을 'site_id'로 지정, null 불가
    private Site site; // 알림이 발생한 사이트

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Keyword 정보를 조회)
    @JoinColumn(name = "keyword_id", nullable = true) // 외래 키 컬럼명을 'keyword_id'로 지정 (전체 페이지 변경 감지 시 null 가능)
    private Keyword keyword; // 감지된 키워드 (전체 페이지 변경 감지 알림의 경우 null일 수 있음)

    @Column(nullable = false, columnDefinition = "TEXT") // null 불가, TEXT 타입으로 지정 (긴 텍스트 저장 가능)
    private String message; // 알림 메시지 내용

    @Column(nullable = false, length = 500) // null 불가, 최대 길이 500자
    private String detectedUrl; // 키워드가 감지된 URL

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime detectedAt; // 키워드 감지 시간

    @Column(nullable = false) // null 불가
    private Boolean sent = false; // 알림 전송 여부 (기본값: false)

    private LocalDateTime sentAt; // 알림 전송 시간 (전송 전에는 null)
}
