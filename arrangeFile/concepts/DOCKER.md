# Docker 개념 정리 — 오늘 Q&A 요약

> "오마주 프로젝트에서 설정만 가져다 쓰고 원리를 몰랐다"는 문제의식에서 시작한 정리.
> 근본 원리 → 프로젝트 실제 코드 → 오늘 발견/수정한 버그 순으로 정리.

---

## 1. Docker가 왜 만들어졌나 (근본 원리)

**문제**: "내 컴퓨터에선 되는데 서버에선 안 돼요." 개발 환경(자바 버전, 라이브러리, OS)과 운영 서버 환경이 달라서 생기는 문제.

**1차 해결책 — 가상머신(VM)**: OS를 통째로 복제해서 옮김. 확실하지만 무겁고 느림 (OS 전체를 다시 띄우는 것과 같음).

**Docker의 발상**: OS의 기초(커널)는 공유해서 쓰고, 애플리케이션 + 필요한 라이브러리만 표준 규격 박스(컨테이너)로 포장해서 옮기자. VM보다 훨씬 가벼우면서도 "어디서든 똑같이 돌아간다"는 목적은 그대로 달성.

**핵심 두 단어**: **격리**(컨테이너끼리 서로 안 섞임) + **이식성**(박스째로 어디 옮겨도 내용물 동일).

---

## 2. 이미지 vs 컨테이너

| 개념 | 정의 |
|---|---|
| 이미지 | 아직 실행 안 된, 포장만 끝난 박스 (붕어빵 틀로 찍어낸 재고) |
| 컨테이너 | 그 박스를 열어서 실제로 돌아가는 상태 |

```bash
docker build -t jooshop-app .   # Dockerfile 설명서대로 이미지(박스) 생성
docker run jooshop-app          # 박스를 열어 실행 → 컨테이너
```

이미지 하나로 컨테이너를 여러 개 찍어낼 수 있음 (틀 하나, 붕어빵 여러 개).

**흔한 오해 정정**: 컨테이너는 "여러 프로그램을 한 군데 모아놓은 통"이 아니라 **원칙적으로 1컨테이너 = 1프로그램**. Redis, MySQL, 앱은 각각 독립된 컨테이너로 따로 존재. 여러 개를 "모아서 실행"하는 것처럼 보이는 건 컨테이너 자체가 아니라 **docker-compose가 여러 독립된 박스를 나란히 놓고 네트워크로 연결**해주는 것.

---

## 3. Dockerfile — 표준 문법 vs 직접 작성하는 값

**표준(고정)**: `FROM`, `WORKDIR`, `COPY`, `RUN`, `CMD`, `ENV`, `EXPOSE`, `ARG` 같은 키워드는 Docker가 정의한 고정 문법. 프로그래밍 언어의 예약어와 같음.

**직접 작성**: 어떤 이미지에서 시작할지, 뭘 복사할지, 무슨 명령을 실행할지는 프로젝트마다 다르므로 개발자가 채워넣음. 다만 언어/프레임워크별 관용적인 패턴(빌드 스테이지+실행 스테이지 분리 등)이 있어서 매번 백지에서 창작하지는 않음.

```dockerfile
# 빌드 스테이지 — 소스 전체 + 빌드 도구 필요
FROM eclipse-temurin:17-jdk as builder
WORKDIR /workspace/app
COPY . .
RUN ./gradlew build

# 실행 스테이지 — 완성된 jar만 있으면 됨 (jre로 충분, jdk는 과함)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar
CMD ["java", "-jar", "app.jar"]
```

**보너스**: 스프링부트는 `./gradlew bootBuildImage`로 Dockerfile 없이도 이미지 자동 생성 가능 (Cloud Native Buildpacks). 실무에서는 세밀한 제어가 필요해 Dockerfile을 직접 쓰는 경우가 더 많음.

---

## 4. docker-compose — 오케스트레이션

**오케스트레이션 = 여러 독립된 컨테이너를 지정된 순서/설정대로 한번에 켜고 서로 연결하는 것.** 합치는 게 아니라 나란히 놓고 전선(네트워크) 연결.

```yaml
services:
  app:
    build: .
    depends_on: [mysql, redis]   # mysql/redis 먼저, app 나중
  nginx:
    build: ./nginx
    depends_on: [app]
  mysql:
    image: mysql:8.0             # ← 오늘 빠져있던 걸 발견해서 추가함
  redis:
    image: redis:7-alpine        # ← 이것도 마찬가지
```

**핵심 원리**: `jdbc:mysql://mysql:3306/...`에서 `mysql`은 IP가 아니라 **서비스 이름 자체가 주소** — Docker 내부 네트워크가 이름을 자동으로 찾아줌.

---

## 5. nginx와 Docker의 관계 — 왜 같이 쓰나

**Docker** = "사무실을 어디서든 똑같이 조립되게 만드는 것" 담당.
**nginx** = "정문(80)으로 온 손님을 어느 사무실(8080)로 보낼지 안내"하는 것 담당.

서로 다른 계층의 문제라서 같이 씀. nginx 없이 Docker만 쓰면 사용자가 `:8080` 포트를 직접 알아야 함. Docker 없이 nginx만 쓰면 서버 환경이 로컬과 달라 "내 컴퓨터에선 됐는데" 문제가 그대로 남음.

```nginx
location / {
    proxy_pass http://app:8080;   # "app"은 docker-compose 서비스 이름과 일치
}
```

---

## 6. MySQL 실제 접속 — 두 가지 방법

MySQL도 홈페이지에서 설치 안 받고 `image: mysql:8.0` 한 줄로 Docker Hub에서 자동으로 받아 실행 가능.

**방법 1 (실무에서 흔함)** — 포트 매핑(`"3306:3306"`)을 통해 IntelliJ Database 도구/DBeaver에서 `localhost:3306`으로 평소처럼 접속. 컨테이너인지 신경 안 써도 됨.

**방법 2** — 컨테이너 내부로 직접 진입:
```bash
docker exec -it <mysql_container_이름> mysql -u root -p
```

---

## 7. 이식성 — 설정 파일 vs 이미지는 다른 곳에 존재

| 대상 | 저장/이동 방식 |
|---|---|
| `Dockerfile`, `docker-compose.yml` | 그냥 텍스트 파일. git으로 코드와 함께 이동 (`git clone`) |
| `mysql:8.0` 같은 공개 이미지 | Docker Hub에 이미 있음. `docker-compose up` 시 자동 다운로드 (로그인 불필요) |
| 직접 만든 `app` 이미지를 남과 공유하고 싶을 때 | Docker Hub에 `docker push`로 올려야 함 (이때만 로그인 필요) |

즉 "설정 파일을 export해서 들고 다닐 필요"도 없고 "Docker에 로그인해서 설정을 받을 필요"도 없음 — git이 설정 파일을, Docker Hub가 이미지를 각자 담당.

---

## 8. 오늘 발견하고 고친 실제 문제

| 문제 | 내용 | 조치 |
|---|---|---|
| `docker-compose.yml`의 mysql/redis에 `image:` 누락 | 이 상태로는 `docker-compose up` 자체가 실패함. 지금까지 로컬 개발은 별도로 설치된 MySQL(`localhost:3306/shop`)에 의존했을 가능성 높음 | `image: mysql:8.0`, `image: redis:7-alpine` 추가 |
| DB 이름이 파일마다 다름 | `application.yml`=`shop`, `docker-compose.yml`=`jooshop`, `deploy.yml`=`pushop` (3개 다 다름) | `shop`으로 통일 (로컬 실제 DB 이름 기준) |
| `.dockerignore` 없음 | `COPY . .` 할 때 `.git`, `.idea` 등 불필요한 파일까지 이미지에 포함됨 | `.dockerignore` 신규 생성 |
| 실행 스테이지가 `jdk` | 실행만 하면 되는데 풀 개발도구(jdk) 그대로 사용 → 이미지 용량 낭비 | `jre`로 변경 |
| `application.yml`/`application-oauth2.yml`에 실제 시크릿 하드코딩 | JWT secret, 카카오/네이버 client-secret, 네이버 메일 비밀번호가 평문으로 git에 커밋되어 있었음 | `${환경변수}` 참조로 전환, `.env` git 추적 해제, `.env.example` 추가 |

---

## 9. 아직 남은 개선 포인트 (선택)

- `depends_on`은 "컨테이너가 켜지기 시작함"만 보장하지 "연결 받을 준비 끝남"은 보장 안 함 → `healthcheck` 추가 고려
- 로컬용/운영용 compose 파일 분리 (`docker-compose.override.yml`, `docker-compose.prod.yml`)
- 이미지 버전 완전 고정 (`mysql:8.0` → `mysql:8.0.36`처럼 패치 버전까지)
- 컨테이너를 root 대신 non-root 유저로 실행 (보안 강화)
