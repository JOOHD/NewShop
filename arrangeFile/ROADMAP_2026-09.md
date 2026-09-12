# 9월 로드맵 — 서버 배포 + 프로젝트 경쟁력 강화 + 취업 준비

> 시작: 2026-09-06 (오늘) / 목표: 2026-10-03까지 완성, 10월 초 지원 시작
> 페이스: 주 4회 × 4시간 (총 약 16회차, 64시간)
> 체크박스에 `[x]` 표시하면서 진행 — 회차 하나 끝날 때마다 이 파일에 바로 체크

---

## 사용법

- 각 회차는 4시간 기준. 못 끝내면 다음 회차로 넘어가도 됨 (순서만 유지)
- "완료 기준"이 체크리스트의 실제 목표 — 이게 안 되면 그 회차는 아직 안 끝난 것
- 막히는 부분은 바로 아래 "트러블슈팅 메모" 칸에 적어두기 (나중에 포폴/면접 자료로 씀)

---

## 1주차 (9/6 ~ 9/12) — 로컬 Docker 완성

### 회차 1 — 환경 세팅 + 첫 실행 시도
- [x] Docker Desktop 설치 확인 (`docker --version`, `docker-compose --version`)
- [x] `.env.example` 복사해서 `.env` 만들고 로컬용 값 채우기
- [ ] 카카오/네이버 개발자 콘솔에서 시크릿 재발급 (아직 안 했다면 최우선)
- [x] `docker-compose config` 로 문법 오류 먼저 확인
- [x] `docker-compose up --build` 첫 실행 — 에러 나면 로그 그대로 캡처해두기

**완료 기준**: 에러가 나더라도 "어떤 에러가 어디서 났는지" 구체적으로 기록됨
→ 겪은 에러 3개: Docker Desktop 엔진 미기동(pipe 에러), 예전 OneDrive 경로의 찌꺼기 컨테이너 혼동, `.dockerignore`가 gradle-wrapper.jar를 잘못 제외

### 회차 2 — 로컬 완주
- [x] 회차 1의 에러 원인 해결
- [x] `http://localhost` (80, nginx 경유) 접속 확인
- [x] `http://localhost:8080` (app 직접) 접속 확인
- [ ] `docker-compose stop nginx` 해보고 80은 안 되고 8080은 되는 것 확인 (nginx 역할 체감) — 여유 있을 때 추가
- [ ] `docker exec -it <mysql 컨테이너> mysql -u root -p` 로 들어가서 테이블 생성 확인 — 여유 있을 때 추가

**완료 기준**: docker-compose로 4개 컨테이너 다 띄운 상태에서 회원가입~로그인까지 실제로 동작

### 회차 3 — 안정성 보강
- [x] `mysql`, `redis` 서비스에 `healthcheck` 적용 (redis는 depends_on.condition: service_started로 처리)
- [x] `app` 서비스에 `depends_on.mysql.condition: service_healthy` 적용
- [x] 컨테이너 전체 재시작(`docker compose down -v && docker compose up -d`)해서 정상 기동 확인
- [ ] `docker-compose.yml` 버전 고정 검토 (`mysql:8.0` → `mysql:8.0.36`처럼) — 여유 있을 때

**완료 기준**: `down` 후 `up`을 반복해도 매번 안정적으로 뜸 ✅

### 회차 4 — 이번 주 정리
- [x] 겪은 에러/해결 과정을 `TROUBLESHOOTING.md`에 추가 (6단계 에러 체인)
- [x] `arrange_SERVER.md`에 실행 흐름/작동 원리 정리
- [x] CI/CD 파이프라인 정리 (`deploy.yml` CodeDeploy→Docker 배포로 통일, `docker-compose.prod.yml` 신규)
- [ ] 다음 주 EC2 배포 전 체크리스트 작성 (AWS 계정 상태, 프리티어 한도 확인) — 회차5에서 같이 진행

**완료 기준**: "로컬에서 Docker로 전체 서비스를 띄워봤다"를 면접에서 구체적으로 설명 가능 ✅

---

## 2주차 (9/13 ~ 9/19) — EC2 실전 배포

> Oracle Cloud는 기존 계정(JOO) 로그인 복구 불가로 2일 이상 소요 후 포기 (9/10). 신규 AWS 계정으로 최종 진행.

### 회차 5 — EC2 인스턴스 준비
- [x] AWS 계정 확인 (naver 계정 사용, 프리티어 만료로 t2.micro 소액 과금 감수)
- [x] EC2 인스턴스 생성 (Ubuntu 22.04 LTS - Jammy, t2.micro, 서울 리전)
- [x] 보안그룹 설정 (80, 443, 22만 열기 — 3306/6379는 외부 비공개)
- [x] SSH 접속 성공

**완료 기준**: `ssh -i key.pem ubuntu@<EC2 IP>` 접속 성공 ✅
**트러블슈팅 메모**: Oracle Cloud 계정 복구 실패(2일 소요, 포기) → AWS 신규 계정은 "Free Plan" 구조상 리전이 가입 국가 기준 3곳 중 하나로 영구 고정되는 문제 발견(한국→시드니 고정) → 결국 기존 naver 계정(프리티어 만료)으로 진행. AMI 선택 시 "Ubuntu with SQL Server" 같은 유료 번들 AMI를 실수로 고르지 않도록 주의 필요했음.

### 회차 6 — 서버에 Docker 환경 구성
- [x] EC2에 Docker, Docker Compose 설치
- [x] `git clone`으로 프로젝트 가져오기
- [x] 서버용 `.env` 작성
- [x] `docker-compose -f docker-compose.prod.yml up -d` 실행

**완료 기준**: EC2 안에서 4개 컨테이너가 정상적으로 뜸 (`docker ps`로 확인) ✅

### 회차 7 — 외부 접속 + 트러블슈팅
- [x] `http://<EC2 퍼블릭 IP>`로 외부에서 접속 성공
- [x] 회원가입~로그인~주문까지 실제 시나리오 테스트

**완료 기준**: 내 컴퓨터가 아닌 다른 네트워크(휴대폰 데이터 등)에서 접속 성공
**트러블슈팅 메모 (실제 겪은 문제들)**:
1. t2.micro RAM 1GB 부족 → SSH 자체가 멈출 정도로 스왑 없이는 버거움 → 스왑 2GB 추가로 해결
2. Stop/Start 반복 시 퍼블릭 IP가 매번 바뀜 (Elastic IP 미사용) → 매번 GitHub Secret/`.env`의 URL 갱신 필요했음
3. GitHub Actions에서 DockerHub 로그인 실패("malformed HTTP Authorization header") → 원인은 유저네임에 "Docker ID" 대신 "Full name" 입력한 실수
4. `git clone` 시점과 이후 push 시점 사이 시차로 EC2의 코드가 오래됨 → 배포 스크립트에 `git pull` 추가로 해결
5. `.env`를 템플릿 그대로 두고 실제 값 채우는 걸 깜빡함 → JWT_SECRET 등 플레이스홀더 값 때문에 크래시
6. MySQL 볼륨이 예전(잘못된) 비밀번호로 이미 초기화되어 있어서, `.env`만 고쳐도 안 먹힘 → `down -v`로 볼륨까지 삭제 후 재생성해야 했음
7. 네이버 OAuth2 클라이언트 ID/시크릿은 있는데 `NAVER_REDIRECT_URI`가 없어서 "redirectUri cannot be empty"로 스프링 시큐리티가 기동 자체를 막음 (실제 크래시 근본 원인)
8. 카카오 로그인 401 → KOE006 → KOE004 순서로 해결 (REST API 키/Redirect URI 등록 키 불일치, 로그인 기능 비활성화) — 최종적으로 카카오 인증 자체는 성공
9. 카카오 로그인 성공 후 `localhost:8080`으로 리다이렉트되며 연결 끊김 → `application.yml`의 `spring.frontend.url`/`spring.backend.url`이 `${FRONTEND_URL}` 환경변수를 안 읽고 하드코딩되어 있던 게 근본 원인 (코드 수정 완료, `.env` 값은 이미 올바름)
10. GitHub Actions `dial tcp ***:22: i/o timeout` — 인스턴스 Stop/Start로 퍼블릭 IP가 또 바뀌었는데 `EC2_HOST` 시크릿이 옛날 IP였던 게 원인. IP/시크릿/카카오 Redirect URI/`.env` 4곳 전부 새 IP로 갱신 후 해결
11. 카카오 로그인 성공 후 헤더 아이콘(오른쪽 프로필/주문/위시리스트) 전체가 사라짐 — `CustomOAuth2User.getAuthorities()`가 `"ROLE_"` 접두사 없이 그냥 `"USER"`를 반환해서 `sec:authorize="hasRole('USER')"`가 항상 실패했던 것. 폼 로그인(`CustomUserDetails`)은 접두사를 붙이는데 소셜 로그인만 빠뜨림 → `"ROLE_" + role`로 수정
12. 로그인 방식(폼 vs 소셜)에 따라 `@AuthenticationPrincipal CustomUserDetails`가 소셜 로그인 시 항상 null이 되어 `/profile`, `/order` 500 에러 — `AuthenticatedMemberResolver`를 만들어 principal 타입 상관없이 memberId만 추출하도록 통일
13. `profile.html`이 존재하지 않는 `member.memberId`를 참조 (`member.info.id`가 맞음) — 템플릿 오탈자, `/profile` 컨트롤러가 정상화되고 나서야 드러난 숨은 버그
14. 로그아웃 시 JSON 메시지만 뜨고 페이지 이동 없음 — `CustomLogoutFilter`가 리다이렉트 없이 JSON만 응답하던 구조적 문제, `response.sendRedirect("/")`로 수정
15. "주문내역" 헤더 링크(`/orders`)가 애초에 컨트롤러가 없던 미구현 기능이었음 — `/api/v1/order/my` + `/orders` 뷰 컨트롤러 + `orders/orderList.html` 신규 구현 (관리자 주문목록과 동일 패턴)
16. admin 계정을 SQL로 직접 INSERT했는데 로그인 401 — 원인 파악 중 MySQL 데이터가 통째로 비어있는 것 발견(회원 테이블 0건, 원인 불명 — 볼륨이 어느 시점에 리셋된 것으로 추정). admin 계정 재INSERT로 임시 해결했지만 **왜 데이터가 사라졌는지는 미해결** — 다음에 `down -v`를 실수로 돌린 적 없는지, 디스크 공간 부족으로 컨테이너가 재생성됐는지 등 원인 파악 필요

### 회차 8 — 마무리 + 병행 작업 시작
- [ ] (여유 되면) 도메인 연결 + HTTPS(Let's Encrypt) 시도
- [ ] EC2 배포 트러블슈팅 전체를 `TROUBLESHOOTING.md`에 정리
- [ ] `OrderServiceTest.java` 수정 시작 (현재 리팩토링 이전 코드를 테스트하고 있어 컴파일 안 되는 상태)

**완료 기준**: "EC2에 실제로 배포해서 외부에서 접속되는 걸 확인했다"고 면접에서 말할 수 있는 상태

---

## 3주차 (9/20 ~ 9/26) — 프로젝트 리팩토링 + 이해도 점검

### 회차 9 — 테스트 정상화
- [ ] `OrderServiceTest`를 현재 `confirmOrder()` 구조에 맞게 재작성
- [ ] `./gradlew test` 전체 그린 확인
- [ ] 필요하면 `MemberAccountServiceTest`, `PaymentServiceTest`도 한 번 더 점검

**완료 기준**: `./gradlew build` 성공 (테스트 실패로 안 막힘)

### 회차 10 — 문서 정합성
- [ ] `README.md`의 "개선 방향(Self-review)" 표를 실제 상태로 갱신 (테스트/배포 항목 반영)
- [ ] 남은 P2/P3 중 시간 되는 것 1~2개 적용 (compose 파일 로컬/운영 분리 등)

**완료 기준**: README만 봐도 프로젝트 실제 상태와 안 어긋남

### 회차 11 — 셀프 면접 연습 1
- [ ] 예상 질문 리스트 작성 (인증/OAuth2/주문·결제/예외처리/Docker 배포 각 2~3개씩)
- [ ] `arrange_INTERVIEW_REFACTORING_STORY.md` 다시 읽으며 막힘없이 설명되는지 셀프 체크
- [ ] 막히는 질문 표시해두기

**완료 기준**: 질문 리스트 최소 15개 확보, 각각에 대해 답변 초안 있음

### 회차 12 — 셀프 면접 연습 2
- [ ] 회차 11에서 막혔던 질문들 답변 보완
- [ ] `arrange_INTERVIEW_REFACTORING_STORY.md`에 Docker/EC2 배포 경험 섹션 추가
- [ ] 가능하면 소리 내어 답변 연습 (글로 아는 것과 말로 하는 것은 다름)

**완료 기준**: 예상 질문 15개 전부 막힘없이 설명 가능

---

## 4주차 (9/27 ~ 10/3) — 포폴 + 이력서

### 회차 13 — Notion 포폴 갱신
- [ ] `PORTFOLIO_NOTION.md` 내용을 실제 Notion 페이지에 반영
- [ ] Docker/EC2 배포 경험, 리팩토링 서사(Before/After) 추가
- [ ] 스크린샷/다이어그램 등 시각 자료 보강

**완료 기준**: 포폴 페이지만 보고도 프로젝트의 기술적 의사결정이 파악됨

### 회차 14 — 이력서 초안
- [ ] 이력서 프로젝트 항목 작성 (임팩트 중심: "무엇을 왜 어떻게 개선했는가")
- [ ] SQLD 자격 반영
- [ ] 기술 스택 요약 정리

**완료 기준**: 이력서 1차 초안 완성

### 회차 15 — 다듬기
- [ ] 이력서 표현 다듬기 (숫자/구체적 성과 위주로)
- [ ] 포폴-이력서-면접 답변 3개 간 내용 일관성 체크

**완료 기준**: 셋이 서로 모순 없이 같은 이야기를 함

### 회차 16 — 최종 점검
- [ ] 이력서/포폴 최종 오탈자·링크 점검
- [ ] 지원 우선순위 회사/공고 리스트업 (SI 중소 위주)

**완료 기준**: 지원 준비 완료 상태

---

## 10/4 ~ 10/6 — 지원 시작

- [ ] 1차 지원 리스트 지원 시작
- [ ] 지원 현황 트래킹 시작 (회사/공고/지원일/결과)

---

## 트러블슈팅 메모 (진행하며 계속 추가)

- 회차별로 막혔던 것, 해결한 것을 여기에 짧게 기록해두면 나중에 면접 스토리로 그대로 쓸 수 있음.
