package com.webmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 파싱된 게시글 정보를 담는 DTO
 * URL을 기준으로 동일 게시글 판별 (points, comments 등은 시간에 따라 변경될 수 있음)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "url")
public class ArticleInfo {

    /**
     * 게시글 제목
     */
    private String title;

    /**
     * 게시글 URL
     */
    private String url;

    /**
     * 작성자 (선택)
     */
    private String author;

    /**
     * 추천/포인트 수 (선택)
     */
    private Integer points;

    /**
     * 댓글 수 (선택)
     */
    private Integer comments;

    /**
     * 게시 시간 (선택)
     */
    private LocalDateTime publishedAt;

    /**
     * 간단한 문자열 표현
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(title).append("\"");

        if (author != null) {
            sb.append(" by ").append(author);
        }

        if (points != null) {
            sb.append(" (").append(points).append(" points)");
        }

        if (comments != null) {
            sb.append(" [").append(comments).append(" comments]");
        }

        return sb.toString();
    }
}
