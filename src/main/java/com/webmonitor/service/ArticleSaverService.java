package com.webmonitor.service;

import com.webmonitor.domain.Article;
import com.webmonitor.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleSaverService {

    private final ArticleRepository articleRepository;

    /**
     * REQUIRES_NEW 트랜잭션으로 article 저장.
     * PostgreSQL은 constraint 위반 시 트랜잭션 전체를 abort하므로 호출자 트랜잭션과 격리가 필수.
     * true: 저장 성공, false: 중복 (이미 다른 스레드가 저장)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveIfNotDuplicate(Article article) {
        try {
            articleRepository.save(article);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("게시글 중복 insert 무시 (동시 실행): {}", article.getArticleUrl());
            return false;
        }
    }
}
