package com.webmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 모니터링할 사이트 정보를 저장하는 엔티티
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "sites") // 테이블 이름을 'sites'로 지정
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Site {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @Column(nullable = false, length = 200) // null 불가, 최대 길이 200자
    private String name; // 사이트 이름

    @Column(nullable = false, length = 500) // null 불가, 최대 길이 500자
    private String url; // 사이트 URL

    @Column(nullable = false) // null 불가
    private Integer checkInterval; // 체크 주기 (분 단위)

    @Column(nullable = false) // null 불가
    private Boolean active = true; // 활성화 여부 (기본값: true)

    @Column(nullable = false) // null 불가
    private Boolean detectContentChange = false; // 전체 페이지 변경 감지 여부 (기본값: false)

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime createdAt; // 생성 시간

    @UpdateTimestamp // 엔티티 수정 시 자동으로 현재 시간 저장
    @Column(nullable = false) // null 불가
    private LocalDateTime updatedAt; // 수정 시간

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, Keyword의 site 필드와 매핑, 연쇄 작업, 고아 객체 제거
    @Builder.Default // 빌더 패턴 사용 시 기본값 지정
    private List<Keyword> keywords = new ArrayList<>(); // 해당 사이트에 등록된 키워드 목록
}
