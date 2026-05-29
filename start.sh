#!/bin/bash

echo "========================================"
echo "웹 모니터링 시스템 시작 중..."
echo "========================================"

# 1. 8080 포트를 사용 중인 프로세스 종료
echo "1. 포트 8080을 사용 중인 프로세스 확인 중..."
PORT_PID=$(lsof -ti:8080)
if [ ! -z "$PORT_PID" ]; then
    echo "   포트 8080을 사용 중인 프로세스 발견 (PID: $PORT_PID)"
    echo "   프로세스 종료 중..."
    kill -9 $PORT_PID 2>/dev/null
    sleep 1
    echo "   ✓ 프로세스 종료 완료"
else
    echo "   ✓ 포트 8080은 사용 중이지 않습니다"
fi

# 2. Spring Boot 프로세스 종료
echo "2. 실행 중인 Spring Boot 프로세스 확인 중..."
SPRING_PIDS=$(pgrep -f "spring-boot:run")
if [ ! -z "$SPRING_PIDS" ]; then
    echo "   Spring Boot 프로세스 발견"
    echo "   프로세스 종료 중..."
    pkill -9 -f "spring-boot:run" 2>/dev/null
    sleep 2
    echo "   ✓ Spring Boot 프로세스 종료 완료"
else
    echo "   ✓ 실행 중인 Spring Boot 프로세스가 없습니다"
fi

# 3. H2 데이터베이스 락 파일 확인 및 정리
echo "3. 데이터베이스 락 파일 확인 중..."
if [ -f "./data/monitor.lock.db" ]; then
    echo "   락 파일 발견, 삭제 중..."
    rm -f ./data/monitor.lock.db
    echo "   ✓ 락 파일 삭제 완료"
else
    echo "   ✓ 락 파일 없음"
fi

# 4. 잠시 대기
echo "4. 시스템 정리 대기 중..."
sleep 1

# 5. Spring Boot 애플리케이션 시작
echo "5. Spring Boot 애플리케이션 시작 중..."
echo "========================================"
./mvnw spring-boot:run
