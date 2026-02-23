# 환경 설정

## 프로파일
- `local`: Docker 기반 로컬 개발
- `dev`, `qa`, `prd`: 환경별 설정

## 포트
- 애플리케이션: 8080
- Actuator/모니터링: 8081

## Docker 서비스 (로컬)
```bash
docker-compose -f docker/docker-compose.yml up -d
```

- MySQL: localhost:3306
- Redis Master: localhost:6379
- Redis Replica: localhost:6380
- Kafka: localhost:19092