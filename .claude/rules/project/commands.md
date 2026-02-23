# 명령어

## 빌드
./gradlew build
./gradlew build -x test
./gradlew clean build

## 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

## 테스트
./gradlew test
./gradlew :apps:commerce-api:test
./gradlew test jacocoTestReport