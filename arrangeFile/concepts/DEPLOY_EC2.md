# Docker / IntelliJ / docker-compose — 관계, 실행 흐름, 작동 원리

> "IntelliJ Run 버튼 안 눌렀는데 왜 페이지가 뜨지?" 같은 질문에서 출발한 정리.
> Docker와 IntelliJ가 서로 무슨 관계인지, `docker compose up` 한 줄이 실제로 어떤 순서로 무슨 일을 하는지 정리.

---

## 1. IntelliJ와 Docker는 사실 관계가 없다

가장 먼저 깨야 하는 오해: **`Dockerfile`, `docker-compose.yml`은 그냥 텍스트 파일이다.** IntelliJ가 이걸 "이해"하거나 "실행"하는 게 아니라, 메모장으로 열어도 되는 파일을 문법 하이라이팅 정도만 편하게 해주는 것뿐이다.

```
Dockerfile / docker-compose.yml 작성  → 텍스트 편집 (IntelliJ든 메모장이든 상관없음)
Dockerfile / docker-compose.yml 실행  → Docker Desktop의 엔진(Docker Engine)이 담당
```

실제로 이미지를 빌드하고 컨테이너를 띄우는 주체는 **Docker Desktop 내부의 Docker Engine**이다 (Windows에선 WSL2 위에서 리눅스 커널을 빌려 돌아감). IntelliJ는 이 엔진에게 명령을 대신 전달해주는 "리모컨" 역할의 플러그인을 제공할 뿐이고, 그 플러그인 없이 터미널에서 `docker compose up`을 직접 쳐도 결과는 완전히 동일하다.

```
IntelliJ Run 버튼          → JVM을 IntelliJ가 직접 실행 (java 프로세스를 IDE가 관리)
docker compose up          → Docker Engine이 컨테이너 안에서 java 프로세스 실행
```

**그래서 IntelliJ Run을 안 눌러도 페이지가 뜨는 이유**: Dockerfile 마지막 줄이 이미 "실행 명령"을 포함하고 있기 때문이다.

```dockerfile
CMD ["java", "-jar", "app.jar"]
```

`docker compose up`이 컨테이너를 만드는 순간 Docker Engine이 이 `CMD`를 자동으로 실행시킨다. 즉 **"이미지 안에 이미 실행 방법까지 포장돼 있다"** — IntelliJ의 Run 버튼이 하던 일(`java -jar` 실행)을 Docker가 컨테이너 시작 시점에 대신 해주는 것뿐, 둘은 같은 일을 하는 서로 다른 실행 경로다.

---

## 2. 전체 실행 흐름 — `docker compose up --build` 한 줄의 실제 순서

```
1) docker-compose.yml 읽음
       ↓
2) build: . 가 있는 서비스(app)부터 Dockerfile로 이미지 빌드
   ┌─────────────────────────────────────────┐
   │ [빌드 스테이지] FROM eclipse-temurin:17-jdk │
   │   COPY . .          → 소스 전체 복사        │
   │   RUN ./gradlew build → 컴파일 + 테스트 + jar 생성 │
   │                                           │
   │ [실행 스테이지] FROM eclipse-temurin:17-jre │
   │   COPY --from=builder ...jar → 완성된 jar만 가져옴 │
   │   CMD ["java","-jar","app.jar"]           │
   └─────────────────────────────────────────┘
       ↓ 이미지 완성 (아직 실행 전, "포장만 끝난 상태")
3) mysql, redis는 Docker Hub에서 이미지 그대로 다운로드 (빌드 불필요)
       ↓
4) depends_on 순서대로 컨테이너 생성/시작
   mysql, redis 시작
       ↓ (healthcheck 있으면 "healthy" 될 때까지 대기)
   app 시작 (mysql:3306, redis:6379로 접속)
       ↓
   nginx 시작 (app:8080으로 프록시 대상 확보)
       ↓
5) 4개 컨테이너가 같은 docker network 안에서 서비스 이름으로 서로를 찾음
       ↓
6) 호스트(Windows) 포트 매핑을 통해 브라우저에서 접근 가능해짐
   localhost:80   → nginx → app:8080
   localhost:8080 → app 직접
```

---

## 3. 각 단계 작동 원리

### 3-1. Dockerfile — "이미지를 어떻게 포장할지"

```dockerfile
FROM eclipse-temurin:17-jdk as builder   # 시작할 베이스 이미지 (자바 17 + 빌드도구 있는 버전)
WORKDIR /workspace/app                   # 컨테이너 안에서 앞으로의 작업 기준 경로
COPY . .                                 # 호스트의 현재 폴더 전체를 컨테이너 안으로 복사
RUN ./gradlew build                      # 컨테이너 "안"에서 빌드 명령 실행 (컴파일+테스트+jar)

FROM eclipse-temurin:17-jre              # 실행 전용 가벼운 베이스로 새로 시작 (빌드도구 불필요)
WORKDIR /app
COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar  # 위 단계 결과물만 가져옴
CMD ["java", "-jar", "app.jar"]          # 컨테이너가 시작될 때 실행할 명령
```

**멀티 스테이지 빌드인 이유**: 빌드에는 JDK(컴파일러 포함, 무거움)가 필요하지만, 실행에는 JRE(실행기만 있음, 가벼움)면 충분하다. 빌드 스테이지의 무거운 흔적(소스코드, 빌드 캐시, JDK)을 최종 이미지에 안 남기고 결과물(jar)만 쏙 빼오는 게 목적 — 이미지 용량이 훨씬 작아짐.

**`COPY . .`가 위험한 이유**: `.env`, `.git`, `.idea`처럼 이미지에 들어가면 안 되는 파일까지 통째로 복사됨 → `.dockerignore`로 제외 목록을 관리해야 함 (`.gitignore`와 문법은 같지만 별개 파일, 서로 안 읽음).

### 3-2. 이미지 vs 컨테이너

```
이미지    = 아직 실행 안 된 "포장만 끝난 상태" (붕어빵 틀로 찍어낸 재고)
컨테이너  = 그 이미지를 실제로 실행시킨 상태 (틀에서 갓 나온 붕어빵)
```

이미지 하나로 컨테이너를 여러 개 찍어낼 수 있다. `docker compose up`은 "이미지 빌드(필요시) → 컨테이너로 실행"을 한 번에 처리하는 명령.

**흔한 오해**: 컨테이너는 "여러 프로그램을 한 곳에 모아놓은 통"이 아니라 원칙적으로 **1컨테이너 = 1프로세스**. app/mysql/redis/nginx는 각각 독립된 컨테이너다. "여러 개를 묶어서 실행"하는 것처럼 보이는 건 컨테이너 자체 기능이 아니라 **docker-compose가 독립된 컨테이너들을 같은 네트워크에 나란히 놓고 연결**해주는 오케스트레이션이다.

### 3-3. docker-compose.yml — 오케스트레이션 설정

```yaml
services:
  app:
    build: .                     # 이 서비스는 Dockerfile로 직접 빌드
    depends_on:
      mysql:
        condition: service_healthy   # mysql이 "healthy" 판정 날 때까지 app 시작 안 함
      redis:
        condition: service_started
    ports:
      - "8080:8080"               # 호스트포트:컨테이너포트

  mysql:
    image: mysql:8.0               # 이 서비스는 Docker Hub에서 완성된 이미지 그대로 사용
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p$$MYSQL_ROOT_PASSWORD --silent"]
      interval: 5s
      retries: 20
```

**`depends_on`의 진짜 의미**: 조건 없이 그냥 `depends_on: [mysql]`이라고만 쓰면 "mysql 컨테이너가 시작되기 시작했다"만 보장하지 "mysql이 실제로 접속을 받을 준비가 됐다"는 절대 보장 안 함. MySQL은 초기화(데이터 디렉토리 생성 등)에 수 초~수십 초가 걸리는데, app은 그보다 훨씬 빨리 뜨기 때문에 먼저 접속을 시도하다 실패하는 경쟁 상태(race condition)가 생긴다. `healthcheck` + `condition: service_healthy`로 "진짜 준비 끝날 때까지" 기다리게 만들어야 한다.

**서비스 이름 = 네트워크 주소**: `jdbc:mysql://mysql:3306/...`에서 `mysql`은 IP 주소가 아니라 docker-compose가 자동으로 만들어주는 내부 DNS 이름이다. 같은 `docker-compose.yml`에 정의된 서비스들은 서로를 IP 대신 서비스 이름으로 찾을 수 있다.

### 3-4. nginx — 왜 앞에 하나 더 두나

```nginx
location / {
    proxy_pass http://app:8080;   # 들어온 요청을 app 컨테이너로 그대로 전달
}
```

Docker는 "환경을 어디서든 똑같이 조립되게" 하는 게 목적이고, nginx는 "80번 포트로 온 손님을 8080번 방(app)으로 안내"하는 게 목적 — 서로 다른 계층의 문제라 같이 쓴다. nginx가 없으면 사용자가 `:8080`을 직접 붙여야 하고, 나중에 앱을 여러 대로 늘려도(로드밸런싱) 진입점을 하나로 유지할 수 없다.

---

## 4. 오늘 실전 배포에서 겪은 에러 체인 (전부 실제로 겪은 순서)

로컬에서 `docker compose up --build`가 진짜로 정상 동작하기까지 총 6개의 서로 다른 문제를 순서대로 만났다. 하나씩 고칠 때마다 다음 단계로 넘어가는 전형적인 "양파 까기" 디버깅이었음.

| # | 증상 | 원인 | 조치 |
|---|---|---|---|
| 1 | `no main manifest attribute, in app.jar` | Gradle이 만드는 jar가 2개(`jar`=껍데기, `bootJar`=실행용)인데 Dockerfile의 `*.jar` 와일드카드가 껍데기를 잘못 집음 | `build.gradle`에 `jar { enabled = false }` 추가 |
| 2 | nginx는 Bad Gateway, `:8080`은 연결 불가 | app 컨테이너가 mysql이 완전히 준비되기 전에 DB 연결을 시도 (`depends_on`이 시작 순서만 보장, 준비 완료는 미보장) | mysql에 `healthcheck` 추가 + app `depends_on`을 `condition: service_healthy`로 변경 |
| 3 | mysql 컨테이너가 계속 `unhealthy`, 재시작 반복 | `.env`의 `MYSQL_USERNAME=root`를 `MYSQL_USER`에도 넣었는데, MySQL 공식 이미지는 "root라는 일반 유저"를 만드는 걸 거부함 (root는 `MYSQL_ROOT_PASSWORD` 전용) | `MYSQL_USER`/`MYSQL_PASSWORD` 줄 제거 (root는 `MYSQL_ROOT_PASSWORD`만으로 충분) |
| 4 | `Unable to determine Dialect` (Hibernate가 DB 연결 실패로 방언을 못 정함) | `product_management` 등 엔티티 리네임 중 `ProductVariant`에 남아있던 죽은 `@ManyToMany(mappedBy="productVariants")`가 `Orders`에 없는 필드를 가리켜 엔티티 매핑 자체가 실패 | 안 쓰이는 죽은 필드 삭제 |
| 5 | `Illegal base64 character 2d` | JWT 서명 키를 Base64로 디코딩하는데, PowerShell 세션(및 Windows 시스템 환경변수)에 남아있던 `JWT_SECRET=dev-jwt-secret-key-2025`가 `.env` 파일 값보다 **우선순위가 높아서** 덮어씀 (Docker Compose는 셸 환경변수 > `.env` 파일) | 시스템 환경변수에서 `JWT_SECRET` 삭제 |
| 6 | `Column 'product_id' cannot be null` | `ProductThumbnail.attachTo()`가 `this.product = product` 대신 `this.product = null`을 대입하는 오타 버그 — 더미 데이터 시딩 경로를 처음 실행해봐서 드러남 | 오타 수정 |

**패턴으로 보면**: 1~3번은 "Docker/인프라 설정" 문제, 4~6번은 "애플리케이션 코드" 문제였다. Docker가 문제를 만든 게 아니라, **평소엔 실행 안 해봤던 코드 경로(전체 컨테이너 기동 → DB 연결 → 엔티티 매핑 → 더미 데이터 시딩)를 처음으로 끝까지 밀어붙이면서 원래 있던 버그들이 차례로 수면 위로 드러난 것**에 가깝다. 이게 "로컬 IDE에서 부분적으로만 돌려봤을 때는 안 보이던 문제가 실제 배포 환경에서 드러난다"는 말의 실체.

---

## 5. 자바 클래스 리네임과 DB 스키마가 무관한 이유

```java
@Entity
@Table(name = "product_management")   // ← 실제 DB 테이블명은 이 문자열이 결정
public class ProductVariant { ... }   // ← 클래스명은 자바 코드 안에서만 쓰는 이름
```

JPA는 "클래스 이름 = 테이블 이름"으로 자동 매칭하는 게 아니라 `@Table(name=...)`/`@Column(name=...)`로 명시적으로 연결한다. 그래서 `ProductManagement → ProductVariant`처럼 자바 쪽 이름만 바꾸고 어노테이션 안의 문자열은 그대로 두면, DB는 전혀 몰라도 되고 마이그레이션(`ALTER TABLE`)도 필요 없다. 객체 모델과 물리 스키마가 이 어노테이션 덕분에 분리돼 있다는 게 핵심.

---

## 6. Docker 컨테이너 끄는 법

```bash
docker compose stop      # 멈추기만 함. 데이터/컨테이너 유지 → 나중에 up으로 그대로 재개
docker compose down      # 컨테이너+네트워크 삭제. volume(DB 데이터)은 유지
docker compose down -v   # 컨테이너+네트워크+volume 전부 삭제. DB 데이터까지 초기화
```

평소엔 `stop`, 완전 초기화하고 싶을 때만 `down -v`.

---

## 7. 로컬 Docker → 실제 서버(EC2) 배포, 뭐가 다른가

로컬(`docker-compose.yml`)과 운영(`docker-compose.prod.yml`)은 완전히 다른 파일이다 (merge 아님, 독립).

| 구분 | 로컬 | 운영(EC2) |
|---|---|---|
| app 실행 방식 | `build: .` (직접 빌드) | `image: ...` (DockerHub에서 pull) |
| 포트 노출 | mysql/redis/app 다 호스트에 노출 (DBeaver 접속용) | nginx(80)만 노출, 나머지는 Docker 내부망 전용 |
| 빌드 도구 | 로컬 PC에 필요 없음(Docker가 컨테이너 안에서 빌드) | EC2엔 Gradle/JDK 아예 필요 없음 |

**핵심 아이디어**: CI(GitHub Actions)가 코드를 빌드해서 이미지로 포장 → DockerHub에 올려둠 → EC2는 그 완성품을 pull만 받아서 실행. EC2는 "빌드 공장"이 아니라 "실행만 하는 곳".

---

## 8. GitHub Actions CI/CD 파이프라인 흐름

```
push origin main
   ↓
checkout (코드 가져오기)
   ↓
JDK 17 세팅 + ./gradlew build (컴파일+테스트, jar 생성)
   ↓
docker build (이미지에 SHA 태그 + latest 태그 둘 다 붙임)
   ↓
docker/login-action → DockerHub 로그인
   ↓
docker push (SHA 태그, latest 태그 둘 다 push)
   ↓
appleboy/ssh-action → EC2에 SSH 접속해서:
   git pull origin main          (compose 파일 등 최신화)
   docker compose -f docker-compose.prod.yml pull app   (방금 올린 이미지 받기)
   docker compose -f docker-compose.prod.yml up -d      (컨테이너 재기동)
   docker image prune -f         (안 쓰는 옛날 이미지 정리)
```

**SHA 태그 vs latest 태그를 같이 쓰는 이유**: SHA 태그는 "이 이미지가 정확히 어느 커밋인지" 확정할 수 있게 해주고(롤백/추적용), latest는 EC2가 매번 SHA를 몰라도 최신본을 편하게 pull 받을 수 있게 해줌.

**GitHub Secrets** (Settings → Secrets and variables → Actions에 등록, 코드에 직접 안 적음):

| Secret | 용도 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP (SSH 접속 대상) |
| `EC2_USERNAME` | SSH 접속 계정 (Ubuntu AMI면 `ubuntu`) |
| `EC2_SSH_KEY` | `.pem` 키 파일 내용 전체 (비공개키) |
| `DOCKER_USERNAME` | DockerHub **로그인 ID** ("Full name" 아님 — 아래 8번 트러블슈팅 참고) |
| `DOCKER_PASSWORD` | DockerHub 비밀번호(또는 Access Token) |

---

## 9. EC2 인스턴스 관련 개념

- **AMI (Amazon Machine Image)**: 인스턴스의 "초기 상태 스냅샷"(OS + 사전설치 소프트웨어). 같은 Ubuntu 22.04라도 "SQL Server 포함" 같은 유료 번들 AMI가 섞여있어서 프리티어만 필터링해서 골라야 함.
- **t2.micro 프리티어**: 1 vCPU(버스터블) + RAM 1GB. RAM이 진짜 빠듯해서 컨테이너 여러 개 띄우면 스왑 없이는 버거움 (10번 항목 참고).
- **보안 그룹(Security Group)**: 인스턴스 앞단 방화벽. 인바운드 규칙에 없는 포트는 외부에서 아예 접근 불가. 이번 프로젝트는 22(SSH)/80(HTTP)/443(HTTPS)만 열고, DB(3306)/Redis(6379)는 절대 외부 노출 안 함 — 컨테이너 내부망에서만 통신.
- **키 페어(.pem)**: 비밀번호 대신 쓰는 SSH 공개키 인증 방식. 발급은 한 번뿐이라 분실하면 그 인스턴스에 다시 못 들어감 — 다운로드 즉시 잘 보관.
- **퍼블릭 IP 불안정성**: Elastic IP(고정 IP)를 별도로 안 붙이면, 인스턴스를 Stop → Start 할 때마다 퍼블릭 IP가 매번 바뀐다. 바뀔 때마다 `EC2_HOST` 시크릿, `.env`의 URL들, OAuth 콘솔의 Redirect URI를 전부 갱신해야 하는 게 이번에 실제로 겪은 번거로움.

---

## 10. EC2 배포 실전 트러블슈팅 체인 (전부 실제로 겪은 순서)

로컬 배포(4번 항목, 6단계)를 넘어서, 실제 EC2에 올리는 과정에서 또 다른 문제 9개를 순서대로 만났다.

| # | 증상 | 원인 | 조치 |
|---|---|---|---|
| 1 | SSH 세션이 커서만 깜빡이고 멈춤 | t2.micro RAM 1GB로 컨테이너 4개(app+nginx+mysql+redis) 동시 구동 시 리소스 고갈 | 2GB 스왑 파일 추가 (11번 항목) |
| 2 | GitHub Actions: `malformed HTTP Authorization header` (DockerHub 로그인 실패) | `DOCKER_USERNAME` 시크릿에 DockerHub "Full name"(표시이름)을 넣음 — 실제로는 로그인용 "Docker ID"가 따로 있음 | 정확한 Docker ID로 시크릿 교체, `docker login -u <id>`로 로컬에서 먼저 검증 |
| 3 | 배포 스텝: `open .../docker-compose.prod.yml: no such file or directory` | EC2의 `git clone` 시점이 그 이후 push한 커밋보다 오래됨 (clone은 자동으로 최신화 안 됨) | 배포 스크립트에 `git pull origin main` 추가 |
| 4 | 앱 크래시: `Illegal base64 character 3f` | `.env`를 템플릿 그대로 두고(`JWT_SECRET=너의_jwt_시크릿` 같은 placeholder) 실제 값으로 안 바꿈 | `.env`에 실제 값 채우기 |
| 5 | `Access denied for user 'root'@'...' (using password: YES)` | MySQL Docker volume이 이미 예전(잘못된) 비밀번호로 초기화되어 있어서, `.env`만 고쳐도 반영 안 됨 (`MYSQL_ROOT_PASSWORD`는 **최초 초기화 시점에만** 적용) | `docker compose -f docker-compose.prod.yml down -v` (볼륨 삭제) 후 `up -d`로 재초기화 |
| 6 | 앱이 계속 재시작(`RestartCount` 증가), OOM은 아님(`OOMKilled=false`) | 네이버 OAuth2 CLIENT_ID/SECRET은 채워져 있는데 `NAVER_REDIRECT_URI`가 누락 — Spring Security의 `InMemoryClientRegistrationRepository`가 기동 시 **등록된 모든** 클라이언트를 검증하다가 빈 redirectUri에서 예외 발생 | `.env`에 `NAVER_REDIRECT_URI` 추가 |
| 7 | 카카오 로그인: `401` → `KOE006` → `KOE004` 순서로 에러 변경 | ① 사용 중인 `KAKAO_CLIENT_ID`가 Redirect URI를 등록한 REST API 키와 다른 키였음 ② 올바른 키로 바꾸니 그 앱의 "카카오 로그인" 기능 자체가 비활성화 상태였음 | ① `.env`의 `KAKAO_CLIENT_ID`를 Redirect URI가 등록된 키로 교체 ② 카카오 개발자 콘솔 → 카카오 로그인 → 활성화 설정 ON |
| 8 | 카카오 로그인은 성공(알림 문자까지 옴)하는데 직후 `localhost:8080`으로 리다이렉트되며 연결 끊김 | `application.yml`의 `spring.frontend.url` / `spring.backend.url`이 `${FRONTEND_URL}` 환경변수를 읽지 않고 `http://localhost:8080`으로 **하드코딩**되어 있었음 — `.env`/compose에 올바른 값을 넣어도 앱이 아예 안 읽던 구조적 버그 | `application.yml`을 `${FRONTEND_URL:http://localhost:8080}` / `${BACKEND_URL:...}` 형태로 수정 (env var 우선, 로컬은 기본값 유지) |
| 9 | GitHub Actions 배포 스텝: `dial tcp ***:22: i/o timeout` (진행 중) | 로컬 SSH는 정상 접속되고 보안그룹도 `0.0.0.0/0`으로 전체 허용 — 시크릿 값 오타/공백 또는 일시적 네트워크 문제로 추정, 확정 원인 미파악 | 재실행(Re-run) 및 `EC2_HOST` 시크릿 재입력으로 확인 중 |

**패턴**: 1은 인프라 리소스, 2~3은 CI/CD 파이프라인 설정, 4~8은 전부 "설정값(.env/application.yml)이 실제로 어디까지 전파되는지"를 정확히 몰라서 생긴 문제였다. 특히 8번은 로컬에서 IDE로 실행할 때는 `localhost:8080`이 기본값이라 티가 안 나다가, 실제 배포해서 도메인이 바뀌는 순간에야 드러나는 전형적인 "환경 분리 누락" 버그.

---

## 11. 스왑(Swap) 메모리 — 명령어

RAM이 부족할 때 디스크 일부를 가상 메모리처럼 쓰게 해주는 설정. t2.micro(1GB RAM)에서 컨테이너 4개를 돌리려면 사실상 필수.

```bash
sudo fallocate -l 2G /swapfile        # 2GB 크기의 스왑 파일 생성
sudo chmod 600 /swapfile              # 소유자만 읽기/쓰기 가능하게 권한 제한 (필수, 안 하면 mkswap이 거부)
sudo mkswap /swapfile                 # 그 파일을 스왑 영역으로 포맷
sudo swapon /swapfile                 # 스왑 활성화 (지금 바로 적용)
swapon --show                         # 활성화 확인

# 재부팅해도 유지되도록 등록
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
cat /etc/fstab | grep swapfile        # 중복 등록 안 됐는지 확인
```

---

## 12. 운영 서버 디버깅 명령어 모음

```bash
# 컨테이너 상태 확인
docker compose -f docker-compose.prod.yml ps
docker ps

# 로그 확인 (실시간 tail)
docker compose -f docker-compose.prod.yml logs -f app
docker logs <컨테이너ID> --tail 50

# 에러만 뽑아보기
docker logs <컨테이너ID> 2>&1 | grep -B5 -A20 ERROR

# OOM(메모리 부족으로 강제 종료)인지 확인
docker inspect --format='OOMKilled={{.State.OOMKilled}} RestartCount={{.RestartCount}}' <컨테이너ID>
dmesg | grep -i oom          # 커널 레벨 OOM 로그

# 완전 재기동 (볼륨까지 초기화 — DB 데이터도 날아감, 주의)
docker compose -f docker-compose.prod.yml down -v
docker compose -f docker-compose.prod.yml up -d

# 최신 코드 반영 (수동 배포 시)
cd ~/jooshop
git pull origin main
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d
docker image prune -f
```

---

## 13. 핵심 용어 정리

| 용어 | 의미 |
|---|---|
| AMI | 인스턴스 생성 시 사용하는 OS+소프트웨어 초기 스냅샷 |
| 프리티어 | AWS 신규(또는 조건 충족) 계정에 일정 기간/용량 무료로 주는 리소스 |
| 보안 그룹 | 인스턴스 단위 방화벽 (인바운드/아웃바운드 규칙) |
| 키 페어(.pem) | SSH 접속용 비공개키 — 비밀번호 인증 대신 사용 |
| Elastic IP | Stop/Start해도 안 바뀌는 고정 퍼블릭 IP (기본 퍼블릭 IP는 매번 바뀜) |
| CI/CD | 코드 push → 자동 빌드(CI) → 자동 배포(CD)까지 이어지는 파이프라인 |
| GitHub Actions Secrets | 워크플로우에서 쓰는 민감정보 저장소 — 코드에 직접 안 적고 참조만 (`${{ secrets.X }}`) |
| DockerHub Docker ID vs Full name | Docker ID=실제 로그인 계정명(고유), Full name=화면 표시용 이름 — 로그인엔 Docker ID만 써야 함 |
| healthcheck / depends_on | "시작 순서"만 보장하는 `depends_on`의 한계를 `healthcheck`로 보완 — "진짜 준비 완료"까지 기다리게 함 |
| 스왑(Swap) | 부족한 RAM을 디스크로 보완하는 가상 메모리 영역 |
| OOMKilled | 메모리 부족으로 커널이 프로세스(컨테이너)를 강제 종료시킨 상태 |
| InMemoryClientRegistrationRepository | Spring Security가 OAuth2 클라이언트 설정들을 기동 시점에 메모리에 올려두고 검증하는 저장소 — 등록된 것 중 하나라도 설정이 비면 앱 전체가 기동 실패 |
| REST API 키 / JavaScript 키 / Native 앱 키 | 카카오 개발자 콘솔에서 발급하는 키 종류 — **키마다 Redirect URI 등록 목록이 완전히 별개**라 서로 안 섞임 |
| KOE006 | 카카오: 요청에 쓴 client_id에 등록되지 않은 Redirect URI (URI 자체는 맞아도 "등록한 키"가 다르면 발생) |
| KOE004 | 카카오: 해당 앱/키에서 "카카오 로그인" 기능이 비활성화된 상태 |
| `@Value("${...}")` 하드코딩 함정 | `application.yml`에 환경변수 참조(`${FRONTEND_URL}`) 없이 값을 직접 적어두면, `.env`/컨테이너 환경변수를 아무리 바꿔도 앱이 영영 그 값을 안 읽음 |
