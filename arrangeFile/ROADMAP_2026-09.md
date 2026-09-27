# 9월 로드맵 — 서버 배포 + 프로젝트 경쟁력 강화 + 취업 준비

> 시작: 2026-09-06 / 목표: 10월 초 지원 시작 (날짜보다 완성도 우선, 유동적으로 조정 중)
> 페이스: 주 4회 × 4시간 (총 18회차로 조정됨 — 아래 "9/27 우선순위 조정" 참고)
> 체크박스에 `[x]` 표시하면서 진행 — 회차 하나 끝날 때마다 이 파일에 바로 체크

### 9/27 우선순위 조정

셀프 면접 연습(구 회차11/12)은 맨 뒤로 미루고, **포폴 + 이력서 마무리를 먼저** 끝내기로 함. 순서: 회차10.5(시각자료) → 회차11(ERD/Swagger) → 회차12(선택, 코드 구조 정리) → 회차13(Notion 포폴) → 회차14(이력서) → 회차15(다듬기) → 회차16(최종점검) → 회차17~18(셀프 면접, 맨 마지막).

**포폴 작성 톤 가이드 (전 회차 공통 적용)**:
- AI가 쓴 티 안 나게, 신입 개발자 눈높이에 맞는 표현 사용
- "deep-dive", "고도화", "혁신적" 같은 과장/딥한 단어 지양 — 담백하게 "무엇을 왜 했는지"만 명확히
- 상위권 개발자 포폴들 참고하되 그대로 따라 하지 말고, 이 프로젝트만의 실제 강점(EC2 직접 배포, 트러블슈팅 서사, 리팩토링 Before/After)을 살리기

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
- [x] `concepts/DEPLOY_EC2.md`(구 `arrange_SERVER.md`)에 실행 흐름/작동 원리 정리
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
17. 메인 페이지 "인기 상품 TOP5" 기능 추가 작업 중:
    - `ProductRankingService.getProductListByRanking()`이 만들어져 있었는데 아무 컨트롤러도 안 불러서 죽은 코드였음 → `/api/v1/products/ranking` 엔드포인트로 연결
    - 더미 상품/조회수 시딩(`DummyProductInitializer`/`DummyProductViewsInitializer`)이 `@Profile("local")`이라 운영엔 안 뜨는 문제 → "이미 있으면 스킵, 없으면 최초 1회만 생성"으로 바꿔서 운영/로컬 공통 사용 + 주문 FK 파손 위험 제거
    - **커밋 실수**: 관련 파일 6개 중 2개(`DummyProductInitializer`, `DummyProductViewsInitializer`)만 먼저 커밋/푸시해서 GitHub Actions `compileJava` 실패(`ProductRankingService`에 없는 메서드를 호출) — 나머지 4개(`ProductRankingService`, `ProductApiControllerV1`, `home.html`, `home.css`) 마저 커밋해서 해결. **교훈: 여러 파일에 걸친 기능은 `git status`/`git diff --cached --stat`로 빠진 파일 없는지 커밋 전에 꼭 확인할 것**
    - 배포 성공(#220) 이후에도 사이트가 계속 `bad gateway` — **미해결, 내일 이어서 확인**. 유력 원인: 운영에서 처음 도는 더미 시딩(상품 10개 × 옵션 조합 + Redis 조회수)이 늘어난 부팅 작업이라 기존보다 기동 시간이 더 걸리는 것으로 추정 (예전에도 디스크 이슈 직후 유사한 "부팅 중 bad gateway"를 겪은 적 있음 — 그때는 몇 분 기다리니 해결됐었음). 내일 EC2 SSH 접속해서 `docker compose -f docker-compose.prod.yml ps` / `logs -f app`으로 실제 기동 완료됐는지, 에러 로그 있는지부터 확인 필요

**다음에 이어서 할 것 (우선순위 순)**:
1. EC2 SSH 접속 → `docker compose -f docker-compose.prod.yml ps`로 컨테이너 상태 확인, `logs -f app`으로 부팅 로그/에러 확인 → bad gateway 원인 특정
2. 해결되면 사이트에서 TOP5 섹션 정상 노출 확인
3. `arrangeFile` 디렉토리 재정리(`project_sum` → `concepts`/`project_flow`/`portfolio`) 건이 아직 로컬에 커밋 안 된 채 남아있음 — 별도로 커밋할 것 (`git add arrangeFile/`)
4. git remote가 옛 저장소 이름(`Shop.git`)을 보고 있음 — `git remote set-url origin https://github.com/JOOHD/NewShop.git`로 갱신 권장 (아직 안 함)
5. 회차11(셀프 면접 연습 1) 아직 시작 전 — 회차10까지 완료 상태에서 포폴 개선(TOP5 기능) 작업으로 잠깐 벗어났던 것

### 회차 8 — 마무리 + 병행 작업 시작
- [x] ~~(여유 되면) 도메인 연결 + HTTPS(Let's Encrypt) 시도~~ — 스킵 (포폴 목적상 불필요 판단)
- [x] EC2 배포 트러블슈팅 전체를 `TROUBLESHOOTING.md`에 정리 — 디스크 100% 배포 실패 사건을 6번 섹션으로 추가
- [x] `OrderServiceTest.java` 수정 — 이미 리팩토링 반영된 상태였음(확인만 필요했음), `./gradlew test` 로컬 실행 결과 전체 통과

**완료 기준**: "EC2에 실제로 배포해서 외부에서 접속되는 걸 확인했다"고 면접에서 말할 수 있는 상태

---

## 3주차 (9/20 ~ 9/26) — 프로젝트 리팩토링 + 이해도 점검

### 회차 9 — 테스트 정상화
- [x] ~~`OrderServiceTest`를 현재 `confirmOrder()` 구조에 맞게 재작성~~ — 확인 결과 이미 반영되어 있었음 (재작성 불필요)
- [x] `./gradlew test` 전체 그린 확인 — 로컬 실행 성공
- [x] `MemberAccountServiceTest`, `PaymentServiceTest` 점검 — 코드 리뷰로 메서드/필드 시그니처 전부 대조, 정상

**완료 기준**: `./gradlew build` 성공 (테스트 실패로 안 막힘)

### 회차 10 — 문서 정합성
- [x] "개선 방향(Self-review)" 표 갱신 — 실제로는 `README.md`가 아니라 `portfolio/PROJECT_OVERVIEW.md`에 있었음. 배포/테스트 항목 반영 + 디스크 이슈 행 추가
- [x] `PROJECT_OVERVIEW.md`의 "Redis 2단계 주문 흐름" 서술이 통째로 낡아있던 것 발견 → Before/After 성장 서사로 재작성 (Cart→DB 직접 저장으로 단순화한 이유 포함), `README.md`의 Orders 섹션/Redis 표/OAuth2 리다이렉트 경로도 같이 동기화
- [x] ~~남은 P2/P3 중 시간 되는 것 1~2개 적용~~ — 실제 백로그 목록이 없어서 아래에 새로 만들어두고 지금은 스킵 (여유 될 때 진행)

**완료 기준**: README만 봐도 프로젝트 실제 상태와 안 어긋남

#### 백로그 (P2/P3 — 여유 될 때, `portfolio/PROJECT_OVERVIEW.md`의 "개선 방향" 표에서 추출)

| 등급 | 항목 | 내용 |
|---|---|---|
| P2 | 이미지 저장 | 외부 URL 참조 → S3 직접 업로드 + presigned URL |
| P2 | 배포 모니터링 | 디스크/컨테이너 상태 모니터링 알림 (disk-full 재발 조기 감지) |
| P2 | 인프라 확장 | RDS(관리형 DB) + S3로 EC2 단일 인스턴스 구조에서 분리 |
| P3 | 테스트 확대 | Controller/Repository 계층 통합 테스트 추가, 커버리지 측정 |
| P3 | 모니터링 | Spring Actuator + 로그 집계 |
| P3 | 결제 검증 | 웹훅 기반 이중 검증 추가 |
| (보류) | HTTPS | Nginx + Let's Encrypt — 포폴 목적상 의도적으로 스킵 |

### 회차 10.5 — 포폴 시각적/정량적 임팩트 보강 (신입 포폴 비교 피드백 반영)

> 회차11 들어가기 전에 포폴/이력서 퀄리티를 신입 지원자 기준으로 솔직하게 점검받음. 결론: 프로젝트 자체(EC2 배포, CI/CD, 트러블슈팅 서사, 리팩토링 Before/After, 단위 테스트)는 신입 치고 탄탄한 상위권인데, "보여주는 방식"에서 실제 실력보다 덜 어필되는 빈틈이 있었음. 투입 대비 효과 순으로 정리.

**우선순위 P0 — 셋 다 합쳐도 30분 안쪽, 반드시 반영**
- [ ] README/포폴 문서에 실제 화면 스크린샷 3~5장 삽입 (메인 배너, TOP5 섹션, 상품 상세 등 직접 리디자인한 화면 위주) — **회차13(Notion 전면 개편)으로 이관, ERD/Swagger 링크와 한 번에 반영하기로 함 (9/27 결정, 포폴 완성도 먼저 잡고 캡처)**
- [x] README에 라이브 데모 링크 추가 — 완료 (Elastic IP 미사용이라 인스턴스 재시작마다 IP가 바뀜, 도메인/HTTPS 생략 사유도 함께 명시 — 9/27 재시작으로 `3.106.240.183` → `3.25.246.153`로 변경, README/PROJECT_OVERVIEW 갱신함)
- [x] Jacoco 붙여서 테스트 커버리지 % 측정, `portfolio/PROJECT_OVERVIEW.md`에 수치로 반영 — 완료 (order.service 79% / payment.service 65% / members.service 34%, 전체 평균 낮은 이유도 같이 설명)

**우선순위 P1 — 여유 되면**
- [ ] 커밋 컨벤션 도입 시점을 README/포폴에 "성장 서사"로 짧게 녹이기 — 최근 커밋(`fix:`/`feat:`/`docs:`)과 초반 커밋(`"...26/0704"` 식 날짜 붙여쓰기) 사이 온도차가 커서 `git log` 한 번 훑으면 바로 보임. 전체 히스토리 스쿼시는 선택사항(리스크 대비 효과 낮음, 안 해도 됨) → 회차15(다듬기)에서 같이 처리
- [ ] Swagger/OpenAPI 어노테이션 추가해서 API 문서 자동 생성 — 회차11로 분리해서 진행

**그냥 확인만 하면 되는 것**
- [x] GitHub 저장소가 Public인지 확인 — Public 확인 완료 (`github.com/JOOHD/NewShop`)

**완료 기준**: P0 3개는 스크린샷만 남음, P1은 회차11/15로 이관

### 회차 11 — 포폴 기술 자료 보강: ERD 재작성 + Swagger 도입
- [x] ERD 다시 그리기 — 실제 `@Entity` 코드 전수 조사해서 `arrangeFile/portfolio/ERD.md`에 Mermaid ERD로 재작성. `review`/`review_reply`/`orders_product_management` 등 예전 Notion 버전에만 있던 죽은 테이블 제외, `product_management`(물리 테이블명 유지)/`order_product`/`product_variant_id` 등 실제 리네임 결과 반영
- [x] Swagger/OpenAPI 적용 — `springdoc-openapi-starter-webmvc-ui:2.5.0` 추가, API 컨트롤러 17개에 `@Tag` 부여, `SwaggerConfig`로 문서 제목/설명 세팅. Postman 문서는 그대로 두고 병행
- [x] 새 ERD 링크, Swagger 안내를 README/PROJECT_OVERVIEW에 반영
- [ ] (로컬에서) `/swagger-ui/index.html` 실제로 떠서 API 목록 보이는지 최종 확인 — sandbox에서는 빌드 자체가 네트워크 allowlist에 막혀서 컴파일 검증 못 함, 로컬 IntelliJ/`./gradlew bootRun`에서 확인 필요

**완료 기준**: 새 ERD가 실제 DB 스키마와 100% 일치, Swagger UI 접속해서 API 목록 확인 가능 (로컬 확인만 남음)

### 회차 12 — (선택, 여유 있으면) 프로젝트 코드 구조 정리
- [x] `EnvCheck.java` 삭제 — 매 부팅마다 `IMP_API_KEY`를 콘솔에 출력하던 디버그용 컴포넌트, 옛날 트러블슈팅 때 쓰고 남아있던 죽은 코드 (비밀값을 로그에 남기는 것도 문제)
- [x] `WebConfig.java` — `/uploads/**` 서빙 경로가 개발자 개인 PC 절대 경로(`C:/Users/user/OneDrive/...`)로 하드코딩되어 있던 것 발견, `@Value("${file.upload-dir:uploads/}")`로 환경변수화. Docker 볼륨 미마운트로 인한 완전한 동작은 별도 백로그(S3 전환)로 남김 — 자세한 내용은 `TROUBLESHOOTING.md` 9번 참고
- [x] `CategoryControllerV1.java` / `ProductVariantController.java` / `ProductVariantService.java` — 죽은 주석 처리 코드 블록 정리
- [x] `InventoryCreateDto`/`InventoryUpdateDto` → `ProductVariantCreateDto`/`ProductVariantUpdateDto`로 네이밍 통일 (예전 `inventoryId`→`variantId` 리네임과 결이 안 맞던 DTO 클래스명 정리, 참조 2곳 함께 수정)
- [x] `EmailConfig.java` — 항상 켜져있던 `mail.debug=true` 제거, 죽은 주석 라인 정리
- [x] `README.md`의 `TROUBLESHOOTING.md` 링크가 `arrangeFile/` 재구성 이후 경로가 안 맞아 깨져있던 것 발견 → `arrangeFile/concepts/TROUBLESHOOTING.md`로 수정

**완료 기준**: 시간 안에 끝낸 만큼만 반영 — 이 회차는 못 끝내도 다음으로 안 밀림(선택 항목) → 오늘 분량 완료

---

## 4주차 (9/27 ~ 10/3) — 포폴 + 이력서

### 회차 13 — Notion 포폴 전면 개편
- [ ] 기존 Notion 페이지(NewShop) 완성도 낮은 상태 → `portfolio/PROJECT_OVERVIEW.md` 내용 기반으로 재작성
- [ ] 스크린샷(회차10.5) + 새 ERD(회차11) + Swagger/Postman 링크 전부 포함
- [ ] 상위권 개발자들 포폴 구조는 참고하되, 이 프로젝트만의 실제 강점(EC2 직접 배포·트러블슈팅 서사·Before/After 리팩토링) 위주로 차별화
- [ ] 톤: 위 "포폴 작성 톤 가이드" 그대로 적용 — 과장 없이 담백하게

**완료 기준**: 포폴 페이지만 보고도 프로젝트의 기술적 의사결정이 파악됨, AI가 쓴 것처럼 안 읽힘

### 회차 14 — 이력서 초안
- [ ] 이력서 프로젝트 항목 작성 (임팩트 중심: "무엇을 왜 어떻게 개선했는가")
- [ ] SQLD 자격 반영
- [ ] 기술 스택 요약 정리

**완료 기준**: 이력서 1차 초안 완성

### 회차 15 — 다듬기
- [ ] 이력서 표현 다듬기 (숫자/구체적 성과 위주로)
- [ ] 커밋 컨벤션 성장 서사 문장 반영 (회차10.5 P1에서 이관)
- [ ] 포폴-이력서-면접 답변 3개 간 내용 일관성 체크

**완료 기준**: 셋이 서로 모순 없이 같은 이야기를 함

### 회차 16 — 최종 점검
- [ ] 이력서/포폴 최종 오탈자·링크 점검
- [ ] 지원 우선순위 회사/공고 리스트업 (SI 중소 위주)

**완료 기준**: 지원 준비 완료 상태

### 회차 17 — 셀프 면접 연습 1 (구 회차11, 맨 뒤로 이동)
- [ ] 예상 질문 리스트 작성 (인증/OAuth2/주문·결제/예외처리/Docker 배포 각 2~3개씩)
- [ ] `portfolio/INTERVIEW_REFACTORING_STORY.md` 다시 읽으며 막힘없이 설명되는지 셀프 체크
- [ ] 막히는 질문 표시해두기

**완료 기준**: 질문 리스트 최소 15개 확보, 각각에 대해 답변 초안 있음

### 회차 18 — 셀프 면접 연습 2 (구 회차12, 맨 뒤로 이동)
- [ ] 회차 17에서 막혔던 질문들 답변 보완
- [ ] `portfolio/INTERVIEW_REFACTORING_STORY.md`에 Docker/EC2 배포 경험 섹션 추가
- [ ] 가능하면 소리 내어 답변 연습 (글로 아는 것과 말로 하는 것은 다름)

**완료 기준**: 예상 질문 15개 전부 막힘없이 설명 가능

---

## 10/4 ~ 10/6 — 지원 시작

- [ ] 1차 지원 리스트 지원 시작
- [ ] 지원 현황 트래킹 시작 (회사/공고/지원일/결과)

---

## 트러블슈팅 메모 (진행하며 계속 추가)

- 회차별로 막혔던 것, 해결한 것을 여기에 짧게 기록해두면 나중에 면접 스토리로 그대로 쓸 수 있음.

### [해결됨] 메인 배너 이미지 404

배포된 사이트에서 메인 배너(`hero-slide--red`, `mud_adidas.avif`) 이미지가 `Failed to load resource: the server responded with a status of 404` — 안 뜸.

**원인**: `.gitignore` 문제가 아니라 `mud_adidas.avif` 파일 자체가 애초에 `git add`된 적이 없었음(`git log --oneline -- <path>` 결과 없음, `git status --porcelain`에 `??`로 추적 안 됨 표시). 로컬 디스크엔 있지만 커밋/배포 이미지엔 안 들어간 상태였던 것.

**해결**: `git add` → 커밋("fix: 메인 배너 이미지(mud_adidas.avif) 누락 커밋 추가 - 배포에서 404 나던 원인") → push. 재배포 후 라이브 사이트에서 정상 노출 확인 완료.

**러닝포인트**: `git status`에서 파일이 `??`(untracked)로 나오는지 습관적으로 확인할 것 — 로컬에서 잘 보인다고 배포에도 반영됐다는 보장이 없음(로컬 파일시스템과 git 추적 대상은 별개).
