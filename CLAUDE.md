# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Web Monitor is a Spring Boot 3 application designed for web monitoring functionality using JSoup for web scraping. Uses H2 file database for local development and PostgreSQL for production.

## Technology Stack

- **Spring Boot**: 3.2.4
- **Java**: 17
- **Build Tool**: Maven
- **Database**: H2 (dev, file-based) / PostgreSQL (prod)
- **Web Scraping**: JSoup 1.17.2
- **Persistence**: Spring Data JPA + Flyway

## Maven Commands

Run the application (dev profile 필수):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Run tests:
```bash
mvn test
```

Run a single test class:
```bash
mvn test -Dtest=ClassName
```

Run a single test method:
```bash
mvn test -Dtest=ClassName#methodName
```

Build the project:
```bash
mvn clean package
```

## Database Access

H2 Console (dev 프로파일 실행 시): http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/monitor;AUTO_SERVER=TRUE;MODE=PostgreSQL`
- Username: `sa`
- Password: (empty)

Dev DB 초기화 (스키마 처음부터 재생성):
```bash
rm -f data/monitor.mv.db data/monitor.trace.db
```
삭제 후 앱을 재시작하면 Flyway가 마이그레이션을 처음부터 실행합니다.

## Application Configuration

- Server runs on port 8080
- Profiles: `dev` (H2 PostgreSQL 호환 모드, Flyway 활성, 보안 비활성) / `prod` (PostgreSQL, Flyway 활성, 보안 활성)
- 프로파일 미지정 시 datasource 없음 → 앱 시작 실패 (의도된 동작)

## Package Structure

Base package: `com.webmonitor`
- Main application class: `WebMonitorApplication.java`

## 프로젝트 규칙

### 테스트 작성 필수
- 새로운 Service/Controller/Repository 메서드를 작성할 때는
  반드시 대응하는 테스트 코드를 함께 작성한다
- 테스트 없이 구현만 제출하는 것은 "미완성"으로 간주한다
- 테스트 프레임워크: JUnit 5, Mockito, AssertJ
- 테스트 실행 명령: `./mvnw test` 또는 `mvn test`
- 작업 완료 전 반드시 전체 테스트를 실행해서 통과 여부 확인

### 테스트 커버 범위
- 정상 케이스 (Happy Path)
- 예외 케이스 (Exception)
- 경계값 (Edge Case)

---

## Pre-existing 테스트 실패 (건드리지 말 것)

- `ProductRepositoryTest.update_shouldModifyProduct`
- `SettingRepositoryTest.update_shouldModifySetting`
- `SiteRepositoryTest.update_shouldModifySite`
- `AdvancedCrawlerServiceTest` (2건) — httpbin.org 외부 서버 의존
- `MonitorServiceEdgeCaseTest.testEmptyPageHandling` — 동일

---

## 다음 세션 작업 목록

테스트: 572개 실행, pre-existing 실패만 유지 (Repository 3건 + httpbin.org 최대 2건(Errors) + MonitorServiceEdgeCase 최대 1건 — 네트워크 의존).

### 미완료 작업

| 심각도 | 내용 |
|--------|------|
| 🔴 HIGH | `KeywordService.updateKeyword()` strip() 누락 — raw keyword 저장 → Discord /keyword-remove 삭제 불가 (`KeywordService.java:89`) |
| 🟡 MEDIUM | `KeywordController.createKeyword()` — EM SPACE 입력 시 HTTP 응답 201→200 계약 변경 (`KeywordController.java:64`) |
| 🟡 MEDIUM | `handleAddProduct` selector raw 저장 — strip 검증 후 strip 없이 DB 저장 (`DiscordCommandHandler.java:444`) |
| 🟡 MEDIUM | siteService dead code (DiscordCommandHandler 미사용 주입) |
| 🟡 MEDIUM | site INSERT→keyword INSERT 사이 스케줄러 race window |
| 🟡 MEDIUM | API_KEY=changeme 기본값 (#5) |
| 🟡 MEDIUM | SettingInitializer DataIntegrityViolationException 오진단 |
| 🟡 MEDIUM | SettingInitializer trailing slash URL 알림 no-op |
| 🟢 LOW | `isValidCssSelector` null/blank 의미 반전 — caller(유효)·callee(무효) 반전 (`DiscordCommandHandler.java:790`) |
| 🟢 LOW | `KeywordServiceTest` Unicode 경계값 테스트 누락 — NBSP/EM SPACE 경계 미커버 |
| 🟢 LOW | SSRF 방지 (JSoup followRedirects=false) |
| 🟢 LOW | migrate script 개선 (#6, #7, #17, #19) |
