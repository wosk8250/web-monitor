package com.webmonitor.service;

import com.webmonitor.domain.Site;
import com.webmonitor.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;

    @Transactional
    public void deleteArticlesBySite(Site site) {
        articleRepository.deleteBySite(site);
        log.debug("사이트 '{}' 게시글 삭제 완료", site.getName());
    }
}
