package com.webmonitor.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사이트에서 감시할 키워드 정보를 저장하는 엔티티
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "keywords") // 테이블 이름을 'keywords'로 지정
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Keyword {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @Column(nullable = false, length = 100) // null 불가, 최대 길이 100자
    private String keyword; // 감시할 키워드

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Site 정보를 조회)
    @JoinColumn(name = "site_id", nullable = false) // 외래 키 컬럼명을 'site_id'로 지정, null 불가
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private Site site; // 키워드가 속한 사이트

    @Column(nullable = false) // null 불가
    private Boolean active = true; // 활성화 여부 (기본값: true)

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime createdAt; // 생성 시간
}
