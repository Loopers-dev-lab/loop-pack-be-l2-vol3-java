---
name: {skill-name}
description: {무엇을 하는지}. {사용자 트리거 문구 2-3개}를 요청할 때 사용합니다.
context: fork
agent: {Explore | Plan | general-purpose | 커스텀 에이전트명}
allowed-tools: {필요한 도구 목록}
---

# {스킬 표시명}

## 작업

$ARGUMENTS에 대해 다음을 수행합니다:

### 1. {탐색/분석 단계}
{Glob, Grep, Read 등을 활용한 정보 수집}

### 2. {처리 단계}
{수집된 정보를 기반으로 분석/변환}

### 3. {결과 정리}
{구체적인 파일 참조를 포함한 결과 요약}

## 에이전트 유형 선택 기준

- **Explore**: 코드베이스 탐색, 읽기 전용 분석
- **Plan**: 구현 계획 수립, 아키텍처 검토
- **general-purpose**: 범용 작업 (기본값)

## 주의사항

- context: fork는 대화 기록에 접근 불가
- 반드시 명시적 작업 지침이 있어야 함
- 결과는 요약되어 메인 대화로 반환됨
