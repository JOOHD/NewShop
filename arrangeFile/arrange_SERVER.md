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
