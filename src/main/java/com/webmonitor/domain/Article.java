package com.webmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 발견한 게시글 정보를 저장하는 엔티티
 * 개별 게시글 추적 및 중복 감지를 위해 사용
 */
@Entity
@Table(name = "articles", indexes = {
    @Index(name = "idx_article_site_article_id",  columnList = "site_id, article_id"),
    @Index(name = "idx_article_site_article_url", columnList = "site_id, article_url", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site; // 게시글이 속한 사이트

    @Column(nullable = false, length = 1000)
    private String articleUrl; // 게시글 URL

    @Column(length = 500)
    private String articleTitle; // 게시글 제목

    @Column(length = 200)
    private String articleId; // 게시글 고유 ID (URL에서 추출)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime firstDetectedAt; // 최초 감지 시간
}
