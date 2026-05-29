# 🔍 웹 모니터링 시스템

웹사이트의 변경사항을 실시간으로 모니터링하고, 키워드 감지 및 알림을 제공하는 자동화 시스템입니다.

## 📋 프로젝트 소개

이 프로젝트는 특정 웹사이트를 주기적으로 크롤링하여 페이지 변경사항을 자동으로 감지하고, 사용자가 등록한 키워드가 페이지에 나타날 때 실시간으로 알림을 보내주는 시스템입니다.

### 주요 특징

- 🚀 **실시간 모니터링**: 1분 간격으로 자동 체크
- 🔔 **실시간 알림**: SSE(Server-Sent Events)를 통한 즉각적인 알림
- 🎯 **키워드 감지**: 사용자 정의 키워드 자동 검색
- 🔍 **페이지 변경 감지**: SHA-256 해시 기반 콘텐츠 변경 감지
- 💬 **디스코드 연동**: 웹훅을 통한 디스코드 알림
- 🎨 **다크 테마 UI**: Tailwind CSS 기반 모던 인터페이스

## ✨ 주요 기능

### 1. 사이트 관리
- 모니터링할 웹사이트 등록/수정/삭제
- 사이트별 체크 주기 설정
- 활성화/비활성화 토글
- 전체 페이지 변경 감지 옵션

### 2. 키워드 관리
- 사이트별 키워드 등록/삭제
- 키워드 활성화/비활성화
- 다중 키워드 지원

### 3. 알림 시스템
- 키워드 감지 시 즉시 알림 생성
- 페이지 변경 감지 시 알림 생성 (키워드 없이)
- 알림 목록 조회 및 관리
- 읽음/미읽음 상태 관리
- 전송된 알림 일괄 삭제

### 4. 실시간 알림
- SSE를 통한 브라우저 실시간 알림
- 디스코드 웹훅 연동
- Rich Embed 형식 지원

### 5. 자동 모니터링
- 스케줄러를 통한 1분 간격 자동 체크
- 활성화된 사이트만 선택적 모니터링
- 에러 발생 시에도 안정적 동작

## 🛠 기술 스택

### Backend
- **Java 17**: 최신 자바 LTS 버전
- **Spring Boot 3.2.4**: 엔터프라이즈급 프레임워크
- **Spring Data JPA**: ORM 및 데이터 액세스
- **H2 Database**: 내장 인메모리 데이터베이스
- **Lombok**: 보일러플레이트 코드 감소

### Libraries
- **JSoup 1.17.2**: HTML 파싱 및 웹 크롤링
- **Spring Web**: RESTful API 제공
- **SSE (Server-Sent Events)**: 실시간 푸시 알림

### Frontend
- **HTML5 / JavaScript**: 표준 웹 기술
- **Tailwind CSS**: 유틸리티 기반 CSS 프레임워크
- **Fetch API**: 비동기 HTTP 통신

## 🚀 실행 방법

### 1. 요구사항
- Java 17 이상
- Maven 3.6 이상 (또는 포함된 Maven Wrapper 사용)

### 2. 프로젝트 클론
```bash
git clone https://github.com/wosk8250/web-monitor.git
cd web-monitor
```

### 3. 애플리케이션 실행
```bash
# Maven Wrapper 사용 (권장)
./mvnw spring-boot:run

# 또는 Maven이 설치된 경우
mvn spring-boot:run
```

### 4. 접속
브라우저에서 `http://localhost:8080` 접속

### 5. H2 데이터베이스 콘솔 (선택사항)
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:webmonitor`
- Username: `sa`
- Password: (비워두기)

## 📡 API 목록

### 사이트 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/sites` | 전체 사이트 목록 조회 |
| GET | `/api/sites/{id}` | 특정 사이트 조회 |
| GET | `/api/sites/active` | 활성화된 사이트 목록 조회 |
| GET | `/api/sites/search?name={name}` | 사이트명으로 검색 |
| POST | `/api/sites` | 새 사이트 등록 |
| PUT | `/api/sites/{id}` | 사이트 정보 수정 |
| DELETE | `/api/sites/{id}` | 사이트 삭제 |
| PATCH | `/api/sites/{id}/toggle` | 사이트 활성화 토글 |

#### 사이트 등록 요청 예시
```json
{
  "name": "예시 사이트",
  "url": "https://example.com",
  "checkInterval": 1,
  "active": true,
  "detectContentChange": true
}
```

### 키워드 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/keywords` | 전체 키워드 목록 조회 |
| GET | `/api/keywords/{id}` | 특정 키워드 조회 |
| GET | `/api/keywords/site/{siteId}` | 특정 사이트의 키워드 조회 |
| POST | `/api/keywords` | 새 키워드 등록 |
| PUT | `/api/keywords/{id}` | 키워드 수정 |
| DELETE | `/api/keywords/{id}` | 키워드 삭제 |
| PATCH | `/api/keywords/{id}/toggle` | 키워드 활성화 토글 |

#### 키워드 등록 요청 예시
```json
{
  "siteId": 1,
  "keyword": "중요공지",
  "active": true
}
```

### 알림 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/alerts` | 전체 알림 목록 조회 |
| GET | `/api/alerts/{id}` | 특정 알림 조회 |
| GET | `/api/alerts/unsent` | 미전송 알림 조회 |
| GET | `/api/alerts/site/{siteId}` | 특정 사이트의 알림 조회 |
| PATCH | `/api/alerts/{id}/mark-read` | 알림을 읽음으로 표시 |
| PATCH | `/api/alerts/{id}/mark-sent` | 알림을 전송됨으로 표시 |
| DELETE | `/api/alerts/sent` | 전송된 알림 일괄 삭제 |

### 설정 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/settings` | 전체 설정 조회 |
| GET | `/api/settings/{id}` | 특정 설정 조회 |
| GET | `/api/settings/active` | 활성화된 설정 조회 |
| POST | `/api/settings` | 새 설정 등록 |
| PUT | `/api/settings/{id}` | 설정 수정 |
| DELETE | `/api/settings/{id}` | 설정 삭제 |
| PATCH | `/api/settings/{id}/toggle` | 설정 활성화 토글 |

#### 설정 등록 요청 예시
```json
{
  "discordWebhookUrl": "https://discord.com/api/webhooks/...",
  "enabled": true,
  "notificationTitle": "웹 모니터링 알림"
}
```

### SSE (실시간 알림) API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/sse/subscribe` | SSE 구독 (이벤트 스트림) |

## 📁 프로젝트 구조

```
web-monitor/
├── src/
│   ├── main/
│   │   ├── java/com/webmonitor/
│   │   │   ├── WebMonitorApplication.java
│   │   │   ├── controller/          # REST API 컨트롤러
│   │   │   ├── domain/              # JPA 엔티티
│   │   │   ├── dto/                 # 데이터 전송 객체
│   │   │   ├── repository/          # JPA 리포지토리
│   │   │   ├── scheduler/           # 스케줄링 작업
│   │   │   └── service/             # 비즈니스 로직
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │           └── index.html       # 웹 UI
│   └── test/                        # 테스트 코드
├── pom.xml
└── README.md
```

## 🎯 사용 예시

### 1. 사이트 등록
1. 웹 UI에서 "사이트 관리" 탭 선택
2. 사이트 이름, URL, 체크 주기 입력
3. "전체 페이지 변경 감지" 옵션 선택 (선택사항)
4. "등록하기" 버튼 클릭

### 2. 키워드 등록
1. "키워드 관리" 탭 선택
2. 사이트 선택 및 키워드 입력
3. "등록하기" 버튼 클릭

### 3. 디스코드 웹훅 설정
1. "설정" 탭 선택
2. 디스코드 웹훅 URL 입력
3. 알림 제목 설정
4. "저장" 버튼 클릭

### 4. 실시간 알림 확인
- 상단의 SSE 연결 상태 확인
- 키워드 감지 또는 페이지 변경 시 자동으로 알림 수신
- "알림 목록" 탭에서 전체 알림 확인

## 🔧 설정

### application.properties

주요 설정 항목:
```properties
# 서버 포트
server.port=8080

# H2 데이터베이스
spring.datasource.url=jdbc:h2:mem:webmonitor
spring.datasource.username=sa
spring.datasource.password=

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# H2 콘솔 활성화
spring.h2.console.enabled=true

# API 보안 (선택사항)
api.security.enabled=true
api.security.key=$2a$10$...bcrypt-hash...
```

## 🔒 API 보안 설정 (선택사항)

이 프로젝트는 API 엔드포인트 보호를 위한 API 키 인증을 지원합니다.

### API 키 활성화

`application.properties`에 다음 설정을 추가하세요:

```properties
# API 보안 활성화 (기본값: false)
api.security.enabled=true

# API 키 (BCrypt 해시값)
api.security.key=$2a$10$YourBCryptHashHere
```

### BCrypt 해시 생성 방법

API 키는 반드시 **BCrypt 해시 형식**으로 저장해야 합니다. 평문 API 키는 보안상 위험합니다.

#### 방법 1: 온라인 BCrypt 생성기 사용
1. https://bcrypt-generator.com/ 접속
2. 원하는 API 키 입력 (예: `my-secret-api-key-2024`)
3. Rounds는 **10**으로 설정 (권장)
4. Generate Hash 클릭
5. 생성된 해시값을 `api.security.key`에 복사

#### 방법 2: Java 코드로 생성
```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String apiKey = "my-secret-api-key-2024";
        String hash = encoder.encode(apiKey);
        System.out.println("BCrypt Hash: " + hash);
    }
}
```

#### 방법 3: 커맨드라인 (htpasswd 사용)
```bash
htpasswd -bnBC 10 "" my-secret-api-key-2024 | tr -d ':\n'
```

### API 호출 시 인증

API 보안이 활성화된 경우, `/api/**` 경로의 모든 요청은 `X-API-Key` 헤더에 **평문 API 키**를 포함해야 합니다.

```bash
# curl 예시
curl -H "X-API-Key: my-secret-api-key-2024" http://localhost:8080/api/sites

# fetch API 예시
fetch('http://localhost:8080/api/sites', {
  headers: {
    'X-API-Key': 'my-secret-api-key-2024'
  }
})
```

**중요**:
- `application.properties`에는 **BCrypt 해시값**을 저장
- API 호출 시에는 **평문 키**를 전송
- 서버는 평문 키를 받아 BCrypt로 검증

### 보안 권장사항

1. **강력한 API 키 사용**: 최소 32자 이상, 영문/숫자/특수문자 혼합
2. **환경변수 사용**: 프로덕션 환경에서는 환경변수로 관리
   ```properties
   api.security.key=${API_KEY_HASH:}
   ```
3. **HTTPS 사용**: 평문 키 전송 시 중간자 공격 방지
4. **키 주기적 갱신**: 정기적으로 API 키 변경
5. **로그 확인**: ApiKeyFilter는 잘못된 인증 시도를 로그에 기록

### 기존 평문 키에서 마이그레이션

이전에 평문 API 키를 사용했다면 다음 단계로 마이그레이션하세요:

1. 기존 평문 키를 BCrypt로 해시 생성
2. `api.security.key`를 해시값으로 업데이트
3. 애플리케이션 재시작
4. 시작 로그에서 "API Key 설정 검증 완료 (BCrypt 해시 형식)" 확인
5. API 클라이언트는 변경 불필요 (여전히 평문 전송)

**경고**: BCrypt 해시 형식이 아닌 경우 애플리케이션 시작 시 경고 로그가 출력됩니다.

```

## 📝 주요 알고리즘

### 페이지 변경 감지
```
1. JSoup으로 웹페이지 크롤링
2. 페이지 전체 텍스트 추출
3. SHA-256 해시값 계산
4. 이전 해시값과 비교
5. 변경 감지 시 알림 생성
```

### 키워드 감지
```
1. 페이지 텍스트에서 등록된 키워드 검색
2. 매칭되는 키워드 발견 시 알림 생성
3. SSE로 실시간 브로드캐스트
4. 디스코드 웹훅으로 전송
```

## 🤝 기여

이 프로젝트는 개인 프로젝트이지만, 개선 제안이나 버그 리포트는 환영합니다.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 개인 학습용으로 제작되었습니다.

## 📧 문의

프로젝트 링크: [https://github.com/wosk8250/web-monitor](https://github.com/wosk8250/web-monitor)

---

**Made with ❤️ using Spring Boot**
