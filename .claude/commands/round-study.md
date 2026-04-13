---
description: Round 학습 세션 — 요구사항 기반 keyword 학습 후 study-data/deepdive 문서 작성
argument-hint: Docs 디렉토리 경로 (예: round9-docs)
---

**상황**
- `$ARGUMENTS/00-requirements.md` 를 100% 만족시키는 설계를 작성하기 위한 학습을 진행한다.
- 학습에 사용될 정보들은 `$ARGUMENTS/00-requirements.md` 과 `$ARGUMENTS/01-information.md` 문서에 존재하는 정보들이다.

**목표**
- 최종적으로, 학습을 통해 `$ARGUMENTS/02-study-data.md` 파일을 작성한다.
- 학습과정중 사용자와의 대화에서, 사용자가 많이 헷갈려하거나 깊게 들어간 부분에 대해서는, `$ARGUMENTS/03-study-deepdive.md`로 따로 저장한다. 이때 '사용자의 질문/판단/의견' 등은 최대한 원문 그대로 보존하고, 너와의 학습을 통해 내려진 '결론/근거'등은 기술적 오류 없이 fact만을 기술한다.

**학습 진행방법**
- 학습 진행전 `$ARGUMENTS/02-study-data.md`와 `$ARGUMENTS/03-study-deepdive.md`를 생성한다. 이후 아래의 과정들을 진행하면서 '실시간'으로 해당 문서들을 업데이트한다.
- `$ARGUMENTS/00-requirements.md` 을 만족시키기 위한 keywords들을 준비하고, 각각에 대해 사용자의 지식 수준을 측정하기 위한 질문을 주고받는다.
- 이 과정에서 절대로 '정답을 직접' 알려주지 않는다. 사용자가 정답에 접근할 수 있도록 '질문'을 통해서 유도하되, 절대로 직접 알려주면 안된다.
- 최종적으로 각각의 keyword에 대해 유저가 80점 이상의 지식 수준에 도달했다고 판단되면, 최종적인 100점짜리 정답을 알려주고 다음 keyword로 넘어간다.
- 최종적으로 모든 keyword에 대해 진행이 완료되었다면, `$ARGUMENTS/02-study-data.md`와 `$ARGUMENTS/03-study-deepdive.md`를 전체 검토하여 진행 과정중 나눴던 대화와 어긋나는 부분이 없는지 꼼꼼하게 검토를 진행한다. 이후 검토가 끝났다면 학습을 종료한다.
