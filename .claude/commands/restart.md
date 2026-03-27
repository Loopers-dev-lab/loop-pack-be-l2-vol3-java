서버를 재시작해줘.

1. 기존 프로세스 종료: `lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null; lsof -ti:8082 2>/dev/null | xargs kill -9 2>/dev/null`
2. 서버 시작: `cd` 프로젝트 루트 후 `./gradlew :apps:commerce-api:bootRun` (백그라운드)
3. 서버가 시작되면 데이터 초기화는 자동으로 실행됨 (app.data-generator.enabled=true)
4. 헬스체크로 시작 확인: `curl -s http://localhost:8080/actuator/health`
