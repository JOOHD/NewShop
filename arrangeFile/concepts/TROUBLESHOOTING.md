
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
9. [이미지 업로드 경로 — 개발자 로컬 PC 경로가 그대로 커밋되어 있던 문제](#9-이미지-업로드-경로--개발자-로컬-pc-경로가-그대로-커밋되어-있던-문제)
10. [더미 상품 이미지 — 외부 CDN 핫링크의 한계와 로컬 정적 자원으로 전환](#10-더미-상품-이미지--외부-cdn-핫링크의-한계와-로컬-정적-자원으로-전환)
11. [상품 상세 페이지 500 에러 — EntityGraph에 List 컬렉션 2개 이상을 동시에 fetch join](#11-상품-상세-페이지-500-에러--entitygraph에-list-컬렉션-2개-이상을-동시에-fetch-join)

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

## 9. 이미지 업로드 경로 — 개발자 로컬 PC 경로가 그대로 커밋되어 있던 문제

### 문제

코드 구조를 정리하며 `WebConfig.java`를 다시 보다가, `/uploads/**` 요청을 서빙하는 경로가 이렇게 하드코딩돼 있는 걸 발견했다.

```java
String uploadAbsolutePath = "C:/Users/user/OneDrive/바탕 화면/Joo_Shop/jooshop/uploads/";
```

개발 중이던 내 로컬 PC의 절대 경로가 그대로 소스에 박혀 있었던 것 — 당연히 리눅스 기반 EC2/Docker 컨테이너에는 이 경로 자체가 존재하지 않는다.

### 원인

바로 위에 주석으로 남아있던 예전 시도(`resources/static/uploads/`에 직접 저장하는 방식)를 보면 원인을 알 수 있다: JAR로 빌드하면 classpath(`resources/static`)는 읽기 전용이 되어 런타임에 새 파일을 못 쓴다. 이 문제를 피하려고 JAR 바깥의 실제 디스크 경로로 바꾸는 시도까지는 맞았는데, 그 경로를 환경변수로 분리하지 않고 내 PC 경로를 그대로 박아넣은 채 커밋해버린 것이 문제였다.

- 실제 파일 쓰기(`ThumbnailService`, `ProductDetailImageService`, `ProfileService`)는 여전히 `resources/static/uploads/...` 경로를 바라보고 있어서, 읽는 경로(`WebConfig`)와 쓰는 경로가 애초에 서로 다른 곳을 가리키고 있었다.
- Docker Compose에도 업로드 파일을 위한 별도 볼륨이 마운트돼 있지 않아서, 설령 경로를 맞춰도 컨테이너를 재시작/재배포하면 업로드된 파일은 사라진다(컨테이너 파일시스템은 휘발성).

### 해결

우선 급한 불(개발자 개인 PC 경로 노출)만 제거했다.

```java
@Value("${file.upload-dir:uploads/}")
private String uploadDir;

@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String uploadPath = new File(uploadDir).getAbsolutePath();
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + uploadPath + "/");
}
```

로컬은 기본값(프로젝트 루트의 `uploads/`)을 쓰고, 운영은 `file.upload-dir` 환경변수로 실제 경로를 주입받도록 바꿔서 적어도 개인 PC 경로가 코드에 남는 문제는 없앴다.

**단, 이걸로 업로드 기능 자체가 운영에서 완전히 동작하는 건 아니다** — Docker 볼륨 마운트가 없어서 컨테이너가 재시작되면 업로드 파일이 사라지는 문제는 그대로 남아있다. 이건 이미 self-review에 적어둔 "이미지 저장: 외부 URL 참조 → S3 직접 업로드" 항목으로 처리할 계획이라 별도 백로그로 남겨둠 — 지금 당장 로컬 파일 업로드를 억지로 고치기보다, 애초에 S3처럼 컨테이너 재시작과 무관한 스토리지로 옮기는 게 맞는 방향이라 판단.

### 러닝포인트

> 절대 경로를 코드에 하드코딩하면 "내 컴퓨터에서는 됩니다" 문제가 그대로 재현된다. 특히 파일 저장/서빙처럼 환경마다 실제 경로가 달라지는 설정은 처음부터 `@Value`로 외부화해두는 습관이 필요하다.
>
> 더 중요한 교훈은 "읽는 경로와 쓰는 경로가 실제로 같은 곳을 가리키는지"를 항상 같이 확인해야 한다는 것 — 이번처럼 둘이 따로 놀면, 로컬에서는 어쩌다 우연히 맞아서 동작하다가 다른 환경에서만 조용히 깨지는 버그가 된다.

### 추가 발견 — `/uploads/**`와 빌드 시 포함되는 이미지의 경로 충돌

`WebConfig`를 고치고 나서, 더미 상품 이미지를 `static/uploads/thumbnails/`에 넣으려다가 또 다른 문제를 발견했다.

Spring Boot는 기본적으로 `static/` 밑의 모든 파일을 별도 설정 없이 그대로 URL로 노출한다. 그런데 `WebConfig`가 `/uploads/**`라는 경로를 직접 등록해서 "이 경로는 무조건 내가 처리한다"고 선언해버리면, 그 순간부터 `/uploads/`로 시작하는 요청은 기본 규칙(정적 리소스 서빙)으로 다시 넘어가지 않고 `WebConfig`가 지정한 디스크 경로에서만 파일을 찾는다. 그래서 `static/uploads/thumbnails/xxx.jpg`처럼 파일이 실제로 존재해도, `WebConfig`가 보는 디스크 경로에 없으면 그대로 404가 난다.

**해결**: 빌드 시 함께 패키징되는 이미지(더미 상품 시드 이미지 등)는 `/uploads/**`와 겹치지 않는 별도 경로(`static/images/dummy/`)에 두기로 했다. `/uploads/**`는 앞으로 실제 런타임 업로드 기능에만 쓰기로 역할을 분리.

**러닝포인트**: URL 패턴을 커스텀 핸들러로 등록하면 그 패턴에 대한 "기본 동작"은 완전히 사라진다 — 일부만 가로채고 나머지는 기본값으로 폴백되는 게 아니다. 커스텀 리소스 핸들러를 등록할 땐 그 경로 프리픽스를 다른 용도로 절대 겹쳐 쓰지 않아야 한다.

---

## 10. 더미 상품 이미지 — 외부 CDN 핫링크의 한계와 로컬 정적 자원으로 전환

### 문제

메인 페이지 TOP5에서 일부 상품 썸네일이 실제 이미지 대신 맨유 엠블럼(폴백 아이콘)으로 표시되는 걸 발견했다. 원인은 더미 상품 이미지를 전부 맨유 공식몰(`mufc-live.cdn.scayle.cloud`)의 이미지를 외부 URL로 직접 링크(핫링크)해서 쓰고 있었기 때문 — 그 중 일부 URL이 만료되거나 접근이 막히면서 깨진 것.

### 원인

외부 URL을 직접 참조하는 방식은 "내가 관리하지 않는 서버의 가용성"에 내 데모 사이트가 그대로 종속된다는 게 근본 문제다. 판매처가 시즌이 지난 상품 이미지를 내리거나 URL 구조를 바꾸면, 내 쪽 코드는 하나도 안 바뀌었는데도 화면이 깨진다.

### 해결

실제 무신사/29CM 같은 대형 이커머스가 이미지를 다루는 방식을 참고했다: 그들도 URL을 동적으로 관리하지만, 그 URL은 항상 "자기가 소유한" 스토리지(S3, 자체 CDN)를 가리킨다 — MD가 관리자 페이지에서 이미지를 업로드하면 그 즉시 자사 스토리지에 저장되고, DB엔 그 자체 도메인 URL만 남는 구조다. 즉, 문제는 "동적 URL이냐 정적이냐"가 아니라 "그 이미지가 누구 서버에 있느냐"였다.

이 프로젝트는 아직 실제 업로드→S3 파이프라인은 없어서, 더미(시드) 데이터에 한해 절충안을 택했다: 실제 상품 정보(이름, 가격 — `store.manutd.com`의 adidas x Man Utd EQT Collection / Stone Roses Collection 기준)는 그대로 살리되, 이미지 파일만 `resources/static/images/dummy/`에 직접 저장해서 빌드에 포함시켰다. 시드 데이터는 "운영 중 계속 바뀌는 실사용자 데이터"가 아니라 "배포 시점에 고정되는 참고 데이터"라서, 정적 파일로 고정해도 아키텍처상 문제가 없다 — 오히려 실 서비스라면 이 부분이 관리자 업로드 → S3 저장 기능으로 이어져야 하는 지점이라, self-review 백로그의 "이미지 저장: 외부 URL 참조 → S3 직접 업로드"와 정확히 같은 문제의식이다.

### 러닝포인트

> "동적으로 값을 가져오는 것"과 "내가 관리하지 않는 자원에 의존하는 것"은 다른 문제다. 실무에서 이미지/파일을 다룰 때 진짜 중요한 질문은 "이 URL이 내 서버(혹은 내가 계약한 스토리지)를 가리키는가"이지, 하드코딩 여부가 아니다.
>
> 다만 시드/테스트 데이터처럼 "배포 시점에 고정돼도 되는 데이터"는 굳이 동적 인프라(S3 등)를 미리 갖출 필요 없이 정적 자원으로 두는 게 오히려 더 안정적이다 — 모든 걸 "실제 서비스처럼" 만들 필요는 없고, 데이터 성격에 맞는 저장 방식을 고르는 게 핵심이다.

---

## 11. 상품 상세 페이지 500 에러 — EntityGraph에 List 컬렉션 2개 이상을 동시에 fetch join

### 문제

라이브 데모에서 아무 상품이나 클릭하면 상세 페이지 대신 아래 에러가 그대로 노출됐다.

```json
{"status":400,"error":"Bad Request","message":"org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags: [Product.productThumbnails, Product.productVariants]"}
```

상품 하나가 아니라 **전체 상품 상세 페이지가 전부** 이 에러로 막혀있었다.

### 원인

`ProductRepository.findProductWithDetailsByProductId()`가 아래처럼 `@EntityGraph`로 세 개의 컬렉션을 한 번에 fetch join 하고 있었다.

```java
@EntityGraph(attributePaths = {"productThumbnails", "productVariants", "wishLists"})
Optional<Product> findProductWithDetailsByProductId(Long productId);
```

`Product`의 `productThumbnails`, `productVariants`, `wishLists`는 전부 `@OneToMany List<...>` — Hibernate 용어로 순서가 없는 "bag" 컬렉션이다. SQL 레벨에서 한 엔티티에 컬렉션 2개를 동시에 JOIN하면 두 컬렉션 크기만큼 행이 카테시안 곱으로 뻥튀기되는데, `List`는 이 중복 행을 걸러낼 기준(정렬 키 등)이 없어서 Hibernate가 이런 조합을 아예 쿼리 생성 단계에서 예외로 막아버린다. `Set`이었다면 허용됐겠지만, 이 프로젝트는 순서가 의미 있는 썸네일/옵션 목록이라 애초에 `List`를 쓴 것이었다.

### 해결

상세 조회는 상품 1건을 대상으로 하는 쿼리라, fetch join 없이 지연 로딩에 맡겨도 실제로는 "본문 쿼리 1개 + 지연로딩 쿼리 2~3개" 수준이라 성능에 영향이 없다. N+1이 문제가 되는 건 "목록 N건을 조회하면서 각 건마다 추가 쿼리가 나가는" 경우이지, 단건 상세 조회에는 해당하지 않는다.

그래서 fetch join은 화면 렌더링에 가장 먼저 필요한 `productThumbnails` 하나만 남기고, 나머지 두 컬렉션은 지연 로딩으로 전환했다.

```java
@EntityGraph(attributePaths = {"productThumbnails"})
Optional<Product> findProductWithDetailsByProductId(Long productId);
```

호출부(`ProductServiceV1`)가 클래스 레벨 `@Transactional` 안에서 실행되기 때문에, `ProductDetailResponseDto` 생성 시점에 `product.getProductVariants()` / `product.getWishLists()`를 호출해도 트랜잭션이 열려있어 지연 로딩이 정상 동작한다.

### 러닝포인트

> `@EntityGraph`/fetch join에 컬렉션을 넣을 때는 "필요하니까 다 넣는다"가 아니라 "몇 건을 조회하는 쿼리인가"부터 따져야 한다. 목록 조회처럼 N건을 반복 조회하는 경우엔 fetch join으로 N+1을 막는 게 맞지만, 단건 상세 조회에서는 지연 로딩 몇 번이 성능에 미치는 영향이 미미하므로 오히려 fetch join을 줄이는 게 안전하다.
>
> 또한 `List` 타입 연관관계 2개 이상을 동시에 fetch join 하려는 시도는 컴파일 타임에는 안 걸리고 **런타임(첫 호출 시점)에만** 터지는 에러라, 로컬 테스트 데이터가 부실하면 미리 못 잡고 배포 후에 발견되기 쉽다 — 실제로 이번에도 로컬에서는 발견하지 못했고, 라이브 데모를 스크린샷 찍으려고 상품을 클릭해보다가 발견했다.

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
