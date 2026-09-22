# arrangeFile — 스터디/문서 디렉토리 안내

개발하면서 정리한 학습/트러블슈팅/포폴 자료 모음. 카테고리별로 폴더를 나눴다.

## 구조

```
arrangeFile/
├── ROADMAP_2026-09.md              ← 지금 뭐 하고 있는지 (계속 갱신 중)
├── TROUBLESHOOTING.md              ← 겪은 버그 모음, 문제/원인/해결/러닝포인트 (계속 갱신 중)
│
├── concepts/                       ← 일반 CS/인프라 개념 학습 (이 프로젝트에 종속 안 됨)
│   ├── BACKEND_CORE.md               Java/Spring/JPA/DB/보안 핵심 개념 총정리
│   ├── DOCKER.md                     Docker 원리 (이미지/컨테이너/네트워크)
│   ├── DEPLOY_EC2.md                 Docker Compose 실행 흐름 + EC2 배포 실전 트러블슈팅
│   ├── TESTING.md                    테스트 코드 작성법 (JUnit5/Mockito/AssertJ)
│   └── COMMANDS_CHEATSHEET.md        로컬/EC2/MySQL 오가는 법 + 자주 쓰는 git·gradle·docker 명령어
│
├── project_flow/                   ← JooShop 프로젝트 자체의 구조/흐름 정리
│   ├── LOGIN_FLOW.md                 로그인 방식 전체 흐름 (Form + OAuth2)
│   ├── JWT.md                        JWT 디렉토리 구조/토큰 발급-검증 흐름
│   ├── OAUTH2.md                     OAuth2 소셜 로그인 흐름
│   ├── CART_ORDER_PAYMENT.md         장바구니 → 주문 → 결제 흐름
│   └── EXCEPTION_FLOW.svg            예외 처리 흐름 다이어그램
│
└── portfolio/                      ← 면접/이력서/Notion 포폴 준비용
    ├── PROJECT_OVERVIEW.md           기술 스택 + 아키텍처 요약 (Notion에 옮길 내용)
    └── INTERVIEW_REFACTORING_STORY.md  리팩토링 Before/After + 면접 답변 서사
```

## 분류 기준

- **concepts/** — "이 프로젝트가 없어도 알아야 하는 지식." 다른 프로젝트에도 그대로 재활용 가능한 개념 정리.
- **project_flow/** — "이 프로젝트 코드가 실제로 어떻게 흘러가는지." 클래스/메서드 단위로 구체적인 이 프로젝트만의 구조.
- **portfolio/** — "밖으로 내보낼 결과물." 면접관/채용담당자에게 보여줄 걸 전제로 쓴 글.
- **ROADMAP / TROUBLESHOOTING** — 매일 갱신되는 살아있는 문서라 최상위에 그대로 둠.

## 참고

- 예전엔 `project_sum/` 폴더에 몰아넣었었는데, 이름이 내용을 설명 못 해서(JWT/OAuth2/로그인/주문 흐름 정리인데 "project_sum"이라 뭔지 안 보임) `project_flow/`로 이름 바꾸고 `concepts/`, `portfolio/`와 분리함.
- `arrange_` 접두사는 폴더가 이미 카테고리를 말해주므로 제거함.
