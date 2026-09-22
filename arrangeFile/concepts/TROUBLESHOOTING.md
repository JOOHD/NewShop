# NewShop 프로젝트 — 트러블슈팅 & 러닝포인트

> 개발 과정에서 실제로 막혔던 문제들과 그 원인, 해결 과정을 정리한 기록입니다.
> 단순한 해결책이 아닌 **"왜 이런 문제가 발생했는가"**를 중심으로 작성했습니다.

---

## 목차

1. [JPA flush 시점 — NOT NULL 제약 위반](#1-jpa-flush-시점--not-null-제약-위반-insert-이슈)
2. [JPA Dirty Checking — UNIQUE 제약 충돌](#2-jpa-dirty-checking--unique-제약-충돌-update-이슈)
3. [연관관계 — DB에는 있는데 객체에는 없는 문제](#3-연관관계--db에는-있는데-객체에는-없는-문제)
4. [순환 참조 — SecurityConfig와 Service 의존성](#4-순환-참조--securityconfig와-service-의존성)
5. [Docker 로컬 배포 — 6단계 에러 체인](#5-docker-로컬-배포--6단계-에러-체인)
6. [EC2 디스크 100% — "배포 성공"인데 실제로는 반영 안 되는 문제](#6-ec2-디스크-100--배포-성공인데-실제로는-반영-안-되는-문제)
7. [Redis 연결 실패 — Spring Boot 3 설정 키 변경(spring.redis → spring.data.redis)을 놓친 문제](#7-redis-연결-실패--spring-boot-3-설정-키-변경springredis--springdataredis을-놓친-문제)
8. [ddl-auto: update의 함정 — 리네임된 테이블/컬럼이 조용히 갈라지는 문제](#8-ddl-auto-update의-함정--리네임된-테이블컬럼이-조용히-갈라지는-문제)

---

## 1. JPA flush 시점 — NOT NULL 제약 위반 (INSERT 이슈)

### 문제

DummyProductInitializer로 상품 초기 데이터를 생성했을 때, 로그에는 저장 성공처럼 보였지만 상품 목록 페이지에 데이터가 노출되지 않았다.
DB를 직접 확인하니 `Product` / `ProductManagement` 모두 저장되지 않은 상태였다.

### 원인

`ProductManagement` 생성 시 `nullable = false` 제약이 걸린 `category_id` / `color_id`에 null을 전달했다.
문제는 `save()` 자체가 아니라 **flush 시점**에 있었다.

```
save(product)
→ 영속성 컨텍스트에 등록 (아직 DB INSERT 미수행)

save(productManagement)
→ flush 시점에 INSERT 실행
→ NOT NULL 제약 위반 (category_id / color_id = null)
→ 예외 발생 → 트랜잭션 전체 롤백
```

`save()`는 영속성 컨텍스트 등록이지 DB INSERT가 아니기 때문에,
실제 제약 검증은 flush/commit 시점까지 미뤄지고 — 그 시점에 터졌다.

### 해결

- 기본 `Category` / `Color` 데이터를 사전에 생성한 뒤 참조하도록 구조 개선
- DummyProductInitializer 내에서 필수 연관관계가 null이면 코드 레벨에서 차단

### 러닝포인트

> `save()`는 DB에 즉시 쓰는 게 아니다. "저장 로그는 있는데 DB엔 없음" 현상을 마주치면 **트랜잭션 롤백**부터 의심해야 한다.

---

## 2. JPA Dirty Checking — UNIQUE 제약 충돌 (UPDATE 이슈)

### 문제

소셜 로그인 / 계정 활성화 흐름에서 `save()`를 명시적으로 호출하지 않았는데도
트랜잭션 종료 시점에 UPDATE 쿼리가 실행되며 UNIQUE 제약 충돌이 발생했다.

### 원인

**두 가지가 겹쳤다:**

**① Dirty Checking**

```java
Member existedMember = memberRepository.findBySocialId(socialId).get(); // 영속 상태
existedMember.activate(); // 상태 변경만 해도 commit 시점에 UPDATE 발생
// memberRepository.save(existedMember); ← 호출하지 않았음
```

영속 상태 엔티티의 필드를 변경하면, `save()` 없이도 commit 시점에 JPA가 자동으로 UPDATE를 만들어 낸다.

**② 중복 UNIQUE 인덱스**

개발 환경에서 `ddl-auto`로 스키마를 자동 생성하던 중,
`social_id` 컬럼에 동일 기준 UNIQUE 인덱스가 중복 생성되어 있었다.
`activate()` 호출로 발생한 UPDATE가 중복 인덱스와 충돌하며 예외로 이어졌다.

### 해결

1. 중복 UNIQUE 인덱스 제거
2. `social_id`는 Optional 필드이므로 빈 문자열 대신 null로 정규화

```java
String normalized = (socialId == null || socialId.trim().isEmpty()) ? null : socialId;
member.setSocialId(normalized);
```

### 러닝포인트

> JPA에서 영속 엔티티는 `save()` 없이도 commit 시 UPDATE된다.
> UNIQUE 충돌을 분석할 때는 "충돌 원인(스키마)"과 "충돌 발생 시점(Dirty Checking)"을 분리해서 봐야 한다.
> 개발 환경의 `ddl-auto` 자동 생성은 제약 조건 중복을 유발할 수 있어, 운영 환경에서는 지양해야 한다.

---

## 3. 연관관계 — DB에는 있는데 객체에는 없는 문제

### 문제

상품-썸네일 이미지는 1:N 관계로 설계했고, 썸네일 데이터는 DB에 정상 저장됐다.
그런데 상품 조회 시 썸네일이 노출되지 않았다.

### 원인

연관관계의 주인(FK) 설정만 하고, **부모 엔티티의 컬렉션에 자식 엔티티를 추가하지 않았다.**

```java
// ❌ FK는 설정되지만 Product 객체의 productThumbnails 리스트엔 추가되지 않음
new ProductThumbnail(product, imageUrl);
```

DB 기준으로는 FK가 연결되어 관계가 존재하지만,
JPA는 **영속성 컨텍스트(객체 그래프)** 기준으로 동작하기 때문에
`product.getProductThumbnails()`는 빈 리스트를 반환했다.

### 해결

연관관계 편의 메서드로 객체와 DB 상태를 동시에 관리:

```java
// Product 엔티티 내부
public void addThumbnail(ProductThumbnail thumbnail) {
    this.productThumbnails.add(thumbnail); // 객체 그래프 연결
    thumbnail.setProduct(this);            // FK 설정
}
```

### 러닝포인트

> JPA는 DB가 아닌 객체 상태를 기준으로 동작한다.
> FK 설정만으로는 객체 그래프가 연결되지 않는다.
> 연관관계 설계는 "어떻게 저장하느냐"가 아닌 **"어떻게 조회하느냐"** 기준으로 결정해야 한다.

---

## 4. 순환 참조 — SecurityConfig와 Service 의존성

### 문제

`SecurityConfig`에서 `JWTFilterV3`와 `LoginFilter`를 직접 생성하면서 `MemberService`를 필드 주입으로 받으려 했다.
Spring 초기화 과정에서 SecurityConfig → Filter → MemberService → SecurityConfig로 이어지는 순환 의존성이 발생해 기동에 실패했다.

### 시도 과정

**① `@Lazy` 시도 (실패)**

순환 참조를 지연 초기화로 우회하려 했지만,
Filter 초기화 시점 문제와 디버깅 복잡도 증가로 실질적인 해결이 어려웠다.

**② 메서드 파라미터 주입으로 해결**

```java
// SecurityConfig 내부 — 파라미터로 받아서 직접 전달
JWTFilterV3 jwtFilter = new JWTFilterV3(jwtUtil, redisTemplate, memberService);
var loginFilter = filterFactory.createLoginFilter(authenticationManager, memberService);
```

`MemberService`를 필드가 아닌 메서드 파라미터로 전달함으로써,
Spring Bean 초기화 시점의 순환 참조 자체를 회피했다.

### 러닝포인트

> `@Lazy`는 순환 참조를 "지연"시킬 뿐이고, 실제로 해결하지는 않는다. 디버깅이 더 어려워지는 경우도 있다.
>
> 이 과정에서 더 중요한 것을 발견했다: `SecurityConfig`에서 `MemberService`를 직접 참조하는 것 자체가 **계층 침범**이었다.
> Filter와 Service의 책임을 분리하고, 계층 간 의존 방향을 다시 점검하는 계기가 됐다.

---

## 5. Docker 로컬 배포 — 6단계 에러 체인

### 문제

`docker compose up --build`로 4개 컨테이너(app/nginx/mysql/redis)를 완전히 띄우기까지, 서로 다른 원인의 에러 6개를 순서대로 만났다. 하나 고치면 다음 단계에서 새 에러가 나는 전형적인 디버깅.

### 원인과 해결

| # | 에러 | 원인 | 해결 |
|---|---|---|---|
| ① | `no main manifest attribute` | Gradle이 jar 2개 생성(`jar`=껍데기, `bootJar`=실행용) — Dockerfile이 껍데기를 잘못 복사 | `build.gradle`에 `jar { enabled = false }` |
| ② | nginx Bad Gateway | `depends_on`이 "시작 순서"만 보장, "준비 완료"는 미보장 → app이 mysql 초기화 전에 연결 시도 | mysql에 `healthcheck` 추가, app `depends_on.condition: service_healthy` |
| ③ | mysql `unhealthy` 반복 재시작 | `MYSQL_USERNAME=root`를 `MYSQL_USER`에도 넣음 — MySQL 이미지는 root 유저 별도 생성을 거부 | `MYSQL_USER`/`MYSQL_PASSWORD` 제거, `MYSQL_ROOT_PASSWORD`만 사용 |
| ④ | `Unable to determine Dialect` | 엔티티 리네임 중 안 쓰는 `@ManyToMany(mappedBy="productVariants")`가 `Orders`에 없는 필드를 참조 → 엔티티 매핑 실패 | 죽은 필드 삭제 |
| ⑤ | `Illegal base64 character` | Windows 시스템 환경변수에 남은 `JWT_SECRET` 값이 `.env` 값을 덮어씀 (Compose는 셸 환경변수 > `.env` 우선) | 시스템 환경변수에서 삭제 |
| ⑥ | `product_id cannot be null` | `ProductThumbnail.attachTo()`가 `this.product = product` 대신 `this.product = null` 대입 (오타) | 오타 수정 |

### 러닝포인트

> ①~③은 인프라 설정, ④~⑥은 코드 버그. 컨테이너 완전 기동 → DB 연결 → 엔티티 매핑 → 더미 시딩까지 처음으로 끝까지 실행해보면서 평소 안 드러나던 문제들이 순서대로 나온 것에 가깝다. `depends_on`은 준비 완료를 보장하지 않는다는 것과 Compose의 환경변수 우선순위(셸 > `.env`)는 실무에서도 자주 걸리는 함정이라 따로 기억해둘 것.

---

## 6. EC2 디스크 100% — "배포 성공"인데 실제로는 반영 안 되는 문제

### 문제

GitHub Actions 배포 로그는 매번 초록불(성공)이었는데, 실제 사이트는 몇 커밋 전 상태를 계속 보여주고 있었다. 로컬에서 CSS를 수정하고 배포했는데도 화면이 전혀 바뀌지 않음.

### 원인 진단 과정

1. 배포된 정적 파일을 직접 fetch해서 git의 최신 커밋 내용과 비교 → 서버가 실제로 옛날 버전을 서빙 중임을 확인
2. `git log`/`git diff`로 로컬-원격 커밋 자체는 완전히 동기화되어 있음을 확인 (ahead/behind 0/0) → 코드 문제는 아님
3. EC2에 SSH 접속해서 `df -h /` → 루트 디스크 100% 가득 참 (`7.6G 7.6G 16M 100% /`)
4. `docker images`로 확인해보니 지금까지의 모든 배포 이미지(커밋 SHA 태그)가 하나도 안 지워지고 계속 쌓여있었음 (배포 1회당 약 617MB × 누적 9회)

**진짜 원인**: `deploy.yml`의 정리 명령이 `docker image prune -f`(옵션 없음)였는데, 이 명령은 **태그가 안 붙은(dangling) 이미지만** 지운다. 매 배포마다 이미지에 커밋 SHA + `latest` 두 태그를 붙이는 구조라, 옛날 이미지들은 전부 "태그가 붙어있는" 상태로 분류되어 절대 안 지워지고 계속 쌓였다.

디스크가 가득 차면 `docker compose pull`이 새 이미지를 받다가 조용히 실패하는데, 배포 스크립트에 `set -e`가 없어서 이 실패가 GitHub Actions에 전파되지 않고 파이프라인 전체는 "성공"으로 보고된 것.

### 해결

1. EC2에서 수동으로 `docker image prune -af` 실행 → 약 640MB 확보, 디스크 사용률 100% → 84%로 회복
2. `docker compose pull` + `up -d`로 정상 재배포, `docker inspect`의 `Created` 타임스탬프로 진짜 최신 이미지가 떴는지 검증
3. `deploy.yml`의 정리 명령을 영구적으로 수정해 재발 방지

```yaml
# Before
docker image prune -f

# After
docker image prune -af   # 태그 붙은 옛날 이미지까지 정리
```

### 러닝포인트

> "배포 성공" 로그를 곧이곧대로 믿으면 안 된다 — 스크립트에 `set -e`가 없으면 중간 명령이 실패해도 파이프라인 전체는 성공으로 보고될 수 있다. 배포 결과가 의심스러울 땐 CI 로그가 아니라 **실제로 서버에 떠 있는 것**(정적 파일 내용, 이미지 `Created` 시각)을 직접 확인하는 게 가장 확실하다.
>
> `docker image prune -f`는 "안 쓰는 이미지 정리"가 아니라 "태그 없는 이미지만 정리"다. 커밋 SHA 태그로 매 배포마다 새 이미지를 만드는 구조에서는 `-a` 옵션 없이는 디스크가 무한히 쌓인다 — t2.micro의 작은 루트 디스크(7.57GB)가 실제로 꽉 차고 나서야 체감했다.

---

## 7. Redis 연결 실패 — Spring Boot 3 설정 키 변경(spring.redis → spring.data.redis)을 놓친 문제

### 문제

인기 상품 TOP5 기능을 위해 서버 시작 시점에 Redis에 더미 조회수를 심는 `CommandLineRunner`를 추가하고 배포했는데, 배포는 매번 "성공"으로 끝나는데도 사이트가 계속 502 Bad Gateway를 띄웠다.

### 원인 진단 과정

1. `docker compose ps`로 컨테이너 상태 확인 → app/nginx/mysql/redis 전부 `Up` — 컨테이너 자체는 떠 있음
2. `docker logs app | grep -i error`로 실제 로그 확인 → `Connection refused: /127.0.0.1:6379` — Redis 연결 실패가 반복되고 있었음
3. `docker-compose.yml`에는 `REDIS_HOST: redis`가 이미 제대로 설정돼 있는데, 왜 `127.0.0.1`(자기 자신)로 붙으려 했는지가 의문이었음

**진짜 원인**: Spring Boot 3부터 Redis 설정 키가 `spring.redis.*` → `spring.data.redis.*`로 바뀌었다. 그런데 `application.yml`은 옛날 키(`spring.redis.host`)를 쓰고 있었고, `docker-compose.yml`도 그에 맞춰 `SPRING_REDIS_HOST`라는 환경변수를 넘기고 있었다. Spring Boot 3는 이 옛날 키를 아예 읽지 않기 때문에, `SPRING_REDIS_HOST`를 아무리 바꿔도 실제로는 어디에도 바인딩되지 않고 `host` 기본값인 `127.0.0.1`(로컬/컨테이너 자기 자신)로 계속 접속을 시도하고 있었던 것.

이 버그는 사실 **이번에 새로 생긴 게 아니라 훨씬 오래전부터 있었다.** 지금까지 Redis를 쓰는 곳(JWT 블랙리스트, 이미지 캐싱, 조회수 랭킹)이 전부 요청 단위로 실행되고 예외도 대부분 잡혀 있어서, 연결 실패가 나도 그 요청 하나만 조용히 실패하고 넘어갔을 뿐 — 겉으로 티가 안 났다. 그런데 이번에 추가한 더미 시딩 코드는 `CommandLineRunner`(서버 부팅 과정의 일부)에서 예외를 안 잡고 그대로 던지는 구조라, 처음으로 이 연결 실패가 **앱 전체 부팅을 막는 치명적인 에러**로 드러난 것.

### 해결

```yaml
# application.yml — Before
spring:
  redis:
    host: localhost
    port: 6379

# application.yml — After
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
```

```yaml
# docker-compose.yml / docker-compose.prod.yml — Before
SPRING_REDIS_HOST: redis

# After
REDIS_HOST: redis
```

### 러닝포인트

> "요청 단위로 실패하는 버그"와 "부팅 단위로 실패하는 버그"는 같은 원인이어도 체감 심각도가 완전히 다르다. 요청 스코프에선 예외 하나가 그 요청만 망가뜨리고 끝나지만, `CommandLineRunner`처럼 부팅 과정에서 예외가 안 잡히면 서버 전체가 안 뜬다 — 그래서 오래된 잠복 버그가 새 기능(부팅 시점 로직) 추가를 계기로 갑자기 터지는 경우가 있다.
>
> 프레임워크 메이저 버전 업(Spring Boot 2→3)에서 설정 키가 바뀌는 건 흔하고, `application.yml`이 "에러 없이 조용히 기본값으로 폴백"하는 키(`spring.redis.*`처럼 그냥 무시되는 옛날 키)를 쓰고 있으면 컴파일도 되고 부팅 로그도 깨끗해서 몇 달을 모르고 지나갈 수 있다. 마이그레이션 가이드의 "설정 키 변경" 항목은 사소해 보여도 실제로 적용됐는지 직접 확인해야 한다.

---

## 8. ddl-auto: update의 함정 — 리네임된 테이블/컬럼이 조용히 갈라지는 문제

### 문제

`ProductVariant`의 PK 이름이 `inventoryId`인데, 이 엔티티는 "재고"가 아니라 "상품 옵션(사이즈/성별/색상 조합)"을 나타내서 의미가 안 맞았다. 정리하려고 로컬 DB를 열어봤더니, 코드와 실제 스키마가 이미 상당히 어긋나 있는 걸 발견했다.

- `OrderProduct` 엔티티는 `@Table(name = "order_product")`인데, 로컬 DB엔 `orderproduct`(언더바 없음)만 있고 `order_product`는 아예 없었음
- 지금 코드 어디서도 참조하지 않는 `orders_product_management`라는 테이블이 남아있었음
- `cart.product_mgt_id`, `order_product.product_management_id`처럼 FK 컬럼명도 지금 구조("상품 옵션/variant")와 안 맞는 옛날 이름 그대로였음

### 원인

`ddl-auto: update`는 스키마를 "지금 엔티티 기준으로 동기화"하는 게 아니라 **"없는 건 새로 만든다"**만 한다 — 있는 테이블/컬럼을 리네임하거나 지우는 기능은 없다.

- `orders_product_management`는 `Orders`↔`ProductVariant`를 `@ManyToMany`로 직접 연결하던 옛날 설계가 자동 생성한 암묵적 조인 테이블이다. 이후 `OrderProduct`라는 명시적 중간 엔티티로 리팩토링됐지만, `ddl-auto`는 안 쓰는 옛날 테이블을 알아서 지워주지 않아 그대로 남았다.
- `OrderProduct`의 `@Table` 이름이 `order_product`로 바뀐 뒤로 로컬에서 앱을 한 번도 재부팅하지 않았다. 그래서 "새 이름 테이블이 없으니 만들어야 한다"는 상황 자체가 아직 발생한 적이 없었던 것 — 실제로는 옛날 `orderproduct` 테이블이 계속 쓰이는 채로, 코드만 먼저 `order_product`를 기대하도록 앞서가 있는 상태였다.

만약 이 상태를 모르고 로컬에서 앱을 부팅했다면: Hibernate가 `order_product`가 없는 걸 보고 **텅 빈 새 테이블**을 만들고, 기존 `orderproduct`에 있던 주문 데이터는 앱이 더 이상 조회하지 않는 고아 테이블로 남았을 것이다. 에러 없이 "주문 내역이 갑자기 사라진 것처럼 보이는" 조용한 사고로 이어질 뻔했다.

### 해결

1. `SHOW TABLES;` / `DESC <table>;`로 실제 스키마를 먼저 확인해서 코드와의 차이를 정확히 파악
2. `ALTER TABLE orderproduct RENAME TO order_product;` — 데이터 보존한 채 물리 테이블명만 코드에 맞춤
3. FK 컬럼명도 통일: `ALTER TABLE cart RENAME COLUMN product_mgt_id TO product_variant_id;`, `ALTER TABLE order_product RENAME COLUMN product_management_id TO product_variant_id;`
4. 안 쓰는 `orders_product_management`는 `DROP TABLE`로 제거
5. `ProductVariant`의 PK는 Java 필드명만 `inventoryId` → `variantId`로 바꾸고, 실제 DB 컬럼(`inventory_id`)은 `@Column(name = "inventory_id")`로 고정해서 **DB 마이그레이션 없이** 이름만 정리 (엔티티 필드명과 물리 컬럼명을 분리하면, 운영에 데이터가 있는 PK는 안 건드리고도 코드 가독성만 개선할 수 있다)
6. 겸사겸사 발견한 진짜 죽은 코드도 정리: 예전 "Redis 2단계 주문 흐름" 설계의 잔재였던 `TemporaryOrderRedis` / `RedisOrderRepository` / `TempOrderResponse`가 아무 곳에서도 참조되지 않는 걸 확인하고 삭제, `PaymentService`가 존재하지도 않는 Redis 키(`cartIds:`, `tempOrder:`)를 매번 지우려던 무의미한 호출도 함께 제거

### 러닝포인트

> `ddl-auto: update`는 "동기화"가 아니라 "부족분만 보충"이다. 엔티티의 `@Table`/`@JoinColumn` 이름을 바꾸는 순간부터 코드와 실제 DB가 조용히 갈라지기 시작하고, 그 상태에서 재부팅하면 옛날 데이터가 연결되지 않는 빈 테이블/빈 컬럼이 새로 생긴다 — 에러가 안 나서 오히려 알아채기 더 어렵다.
>
> 엔티티의 물리 이름(`@Table`, `@JoinColumn`, `@Column`)을 바꿀 땐 "DB의 실제 이름도 수동으로 같이 맞춰야 한다"를 세트로 기억할 것. 특히 운영처럼 이미 데이터가 쌓인 환경에서는 **DB 리네임이 코드 배포보다 먼저** 이뤄져야 한다 — 순서가 바뀌면 기존 데이터가 참조 끊긴 채로 남는다.
>
> 참고로 이번 건 "컬럼 리네임 전 것이 그대로 남아있던" 상황이라 조용히 넘어갔지만, 만약 `NOT NULL` 컬럼을 진짜로 새로 추가해야 하는 상황이었다면 기존 행에 채울 기본값이 없어 `ALTER TABLE` 자체가 실패하며 **부팅이 막혔을 수도** 있다. `ddl-auto: update`가 "안전하게 무시하고 넘어간다"고 오해하면 안 되는 이유.

---

## 부록 — 이 프로젝트에서 사용한 핵심 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot, Spring Security (Dual Filter Chain), Spring Data JPA |
| 인증 | JWT (HttpOnly Cookie), OAuth2 (Kakao / Naver), Redis Blacklist |
| 주문/결제 | Cart→DB 직접 저장, Iamport 서버 검증 |
| 성능 | Redis ZSet (조회수 랭킹), Fetch Join / EntityGraph (N+1 대응) |
| Frontend | Thymeleaf SSR, Fetch API, jQuery |
| Infra | MySQL, Docker |

📄 [API 문서 (Postman)](https://documenter.getpostman.com/view/16649127/2sB2cUC3Qn)
