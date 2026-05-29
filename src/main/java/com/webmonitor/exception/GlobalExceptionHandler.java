package com.webmonitor.exception;

import com.webmonitor.event.CriticalErrorEvent;
import com.webmonitor.exception.crawling.CrawlingException;
import com.webmonitor.exception.notification.NotificationException;
import com.webmonitor.exception.parsing.ParsingException;
import com.webmonitor.exception.resource.ResourceNotFoundException;
import com.webmonitor.exception.validation.ValidationException;
import com.webmonitor.service.MetricsService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.QueryTimeoutException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일관된 형식으로 처리
 *
 * 순환 의존성 해결: CriticalErrorNotifier 직접 의존성 제거
 * ApplicationEventPublisher를 통한 이벤트 기반 알림 발송
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MetricsService metricsService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 입력 검증 실패 (@Valid 어노테이션)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("입력 검증 실패: {}", ex.getMessage());

        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("입력값 검증에 실패했습니다.")
                .details(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 타입 변환 실패 (예: String을 Long으로 변환 불가)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("타입 변환 실패: {}", ex.getMessage());

        String message = String.format("'%s' 파라미터는 '%s' 타입이어야 합니다.",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Type Mismatch")
                .message(message)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * IllegalArgumentException - 잘못된 인수
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("잘못된 인수: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage() != null ? ex.getMessage() : "잘못된 요청입니다.")
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * IllegalStateException - 잘못된 상태
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("잘못된 상태: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Invalid State")
                .message(ex.getMessage() != null ? ex.getMessage() : "현재 상태에서 처리할 수 없는 요청입니다.")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * NullPointerException - null 참조
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException 발생", ex);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Null Pointer Exception")
                .message("서버 내부 오류가 발생했습니다. 관리자에게 문의하세요.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // DATABASE EXCEPTION HANDLERS
    // ========================================

    /**
     * NoSuchElementException - Optional.get() 실패, 엔티티 조회 실패
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(NoSuchElementException ex) {
        log.warn("엔티티를 찾을 수 없음: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage() != null ? ex.getMessage() : "요청한 리소스를 찾을 수 없습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * EntityNotFoundException - JPA 엔티티를 찾을 수 없음
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("JPA 엔티티를 찾을 수 없음: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Entity Not Found")
                .message(ex.getMessage() != null ? ex.getMessage() : "요청한 데이터를 찾을 수 없습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * DataIntegrityViolationException - 데이터 무결성 제약조건 위반 (Unique, Foreign Key 등)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("데이터 무결성 제약조건 위반: {}", ex.getMessage());

        String message = "데이터 제약조건 위반입니다.";

        // 일반적인 제약조건 위반 패턴 감지
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (exMessage.contains("unique") || exMessage.contains("duplicate")) {
            message = "이미 존재하는 데이터입니다. 중복된 값을 입력할 수 없습니다.";
        } else if (exMessage.contains("foreign key") || exMessage.contains("constraint")) {
            message = "참조 무결성 제약조건 위반입니다. 연관된 데이터를 먼저 처리해주세요.";
        } else if (exMessage.contains("not null")) {
            message = "필수 값이 누락되었습니다.";
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Data Integrity Violation")
                .message(message)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * ConstraintViolationException - Bean Validation 제약조건 위반
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("제약조건 위반: {}", ex.getMessage());

        Map<String, Object> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        });

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("입력값 검증 실패: 제약조건을 위반했습니다.")
                .details(violations)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * OptimisticLockingFailureException - 낙관적 락 충돌 (동시 수정)
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("낙관적 락 충돌 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Concurrent Modification")
                .message("다른 사용자가 동시에 같은 데이터를 수정했습니다. 새로고침 후 다시 시도해주세요.")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * QueryTimeoutException - 쿼리 실행 타임아웃
     */
    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(QueryTimeoutException ex) {
        log.error("쿼리 타임아웃 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.REQUEST_TIMEOUT.value())
                .error("Query Timeout")
                .message("데이터베이스 쿼리 처리 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.")
                .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    /**
     * TransactionTimedOutException - 트랜잭션 타임아웃
     */
    @ExceptionHandler(TransactionTimedOutException.class)
    public ResponseEntity<ErrorResponse> handleTransactionTimeout(TransactionTimedOutException ex) {
        log.error("트랜잭션 타임아웃 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.REQUEST_TIMEOUT.value())
                .error("Transaction Timeout")
                .message("트랜잭션 처리 시간이 초과되었습니다. 작업량이 많거나 시스템이 바쁜 상태입니다.")
                .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    /**
     * DataAccessException - Spring Data 접근 오류 (상위 예외)
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException ex) {
        log.error("데이터베이스 접근 오류: {}", ex.getMessage(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.DataAccessException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.DataAccessException"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Database Error")
                .message("데이터베이스 처리 중 오류가 발생했습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // I/O AND NETWORK EXCEPTION HANDLERS
    // ========================================

    /**
     * IOException - 파일/네트워크 I/O 오류
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException ex) {
        log.error("I/O 오류 발생: {}", ex.getMessage(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.IOException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.IOException"
        ));

        String message = "파일 또는 네트워크 처리 중 오류가 발생했습니다.";

        // 특정 I/O 오류 패턴 감지
        if (ex instanceof java.net.SocketTimeoutException) {
            message = "네트워크 연결 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
        } else if (ex instanceof java.net.UnknownHostException) {
            message = "서버 주소를 찾을 수 없습니다. URL을 확인해주세요.";
        } else if (ex instanceof java.net.ConnectException) {
            message = "서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.";
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("I/O Error")
                .message(message)
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    // ========================================
    // CUSTOM WEB MONITOR EXCEPTION HANDLERS
    // ========================================

    /**
     * ResourceNotFoundException - 리소스를 찾을 수 없음 (Site, Keyword, Alert, Product 등)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("리소스를 찾을 수 없음: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * ValidationException - 유효성 검증 실패 (URL, Selector, Interval 등)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.warn("유효성 검증 실패: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * CrawlingException - 웹 크롤링 실패 (URL 검증, 크롤링 실패, Rate Limit 등)
     */
    @ExceptionHandler(CrawlingException.class)
    public ResponseEntity<ErrorResponse> handleCrawlingException(CrawlingException ex) {
        log.error("크롤링 오류 발생: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.CrawlingException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.CrawlingException"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Crawling Failed")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * ParsingException - 파싱 실패 (게시글 추출, 제품 정보 파싱 등)
     */
    @ExceptionHandler(ParsingException.class)
    public ResponseEntity<ErrorResponse> handleParsingException(ParsingException ex) {
        log.error("파싱 오류 발생: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.ParsingException");

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Parsing Failed")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * NotificationException - 알림 전송 실패 (Discord Webhook, SSE 등)
     */
    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ErrorResponse> handleNotificationException(NotificationException ex) {
        log.error("알림 전송 오류 발생: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.NotificationException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.NotificationException"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Notification Failed")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * WebMonitorException - 모든 커스텀 예외의 Base Exception (catch-all for custom exceptions)
     */
    @ExceptionHandler(WebMonitorException.class)
    public ResponseEntity<ErrorResponse> handleWebMonitorException(WebMonitorException ex) {
        log.error("Web Monitor 오류 발생: {} (에러 코드: {})", ex.getMessage(), ex.getErrorCode(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.WebMonitorException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.WebMonitorException"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Web Monitor Error")
                .message(ex.getMessage())
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * RuntimeException - 실행 시간 예외 (상위 예외)
     * IllegalArgumentException, IllegalStateException 등 더 구체적인 예외가 먼저 처리됨
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime 예외 발생: {}", ex.getMessage(), ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.RuntimeException");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.RuntimeException"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Runtime Exception")
                .message("실행 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * SecurityException - 보안 예외
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        log.error("보안 예외 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Security Exception")
                .message("보안 정책에 의해 접근이 거부되었습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * UnsupportedOperationException - 지원하지 않는 작업
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperation(UnsupportedOperationException ex) {
        log.warn("지원하지 않는 작업 요청: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_IMPLEMENTED.value())
                .error("Unsupported Operation")
                .message("지원하지 않는 기능입니다.")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    /**
     * 기타 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("예상치 못한 오류 발생", ex);

        // 메트릭 기록
        metricsService.incrementExceptionCounter(ex.getClass().getSimpleName(), "GlobalExceptionHandler.Exception");

        // 연속 예외 추적 (이벤트 발행)
        eventPublisher.publishEvent(new CriticalErrorEvent(
                this,
                ex.getClass().getSimpleName(),
                "GlobalExceptionHandler.Exception"
        ));

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("서버 내부 오류가 발생했습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
