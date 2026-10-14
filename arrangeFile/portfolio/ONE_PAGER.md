# JooShop — 1페이지 요약

> Spring Boot 쇼핑몰 백엔드. 설계·구현·배포·장애 대응까지 혼자 한 번 끝까지 돌려 본 프로젝트입니다.

🔗 [라이브 데모](http://54.66.19.135) · [GitHub](https://github.com/JOOHD/NewShop) · [API 문서](https://documenter.getpostman.com/view/16649127/2sB2cUC3Qn) · [ERD](ERD.md)

---

## 한 줄 소개

로그인 → 상품 탐색 → 장바구니 → 주문 → 결제까지의 흐름을 직접 설계하고, AWS EC2에 CI/CD로 배포해 운영한 쇼핑몰입니다.

## 핵심 3가지

| | 한 일 | 근거 |
|---|---|---|
| **1. 인증·보안** | JWT를 HttpOnly 쿠키로 발급하고, 로그아웃 토큰은 Redis 블랙리스트로 즉시 무효화. 카카오·네이버 OAuth2 연동. API/웹 보안 체인 분리 | [JWT.md](../project_flow/JWT.md) |
| **2. 결제 신뢰성** | 클라이언트 값을 믿지 않고 Iamport 서버에서 결제 금액을 재검증. 주문·결제 서비스 단위 테스트 (order 85% / payment 61%) + 주문 롤백 통합 테스트 | [CART_ORDER_PAYMENT.md](../project_flow/CART_ORDER_PAYMENT.md) |
| **3. 배포·운영** | GitHub Actions → DockerHub → EC2 자동 배포. 배포가 조용히 실패하던 문제(디스크 100%), SSH 키 노출 사고를 직접 겪고 해결 | [TROUBLESHOOTING.md](../concepts/TROUBLESHOOTING.md) (17건) |

## 다른 지원자와의 차이

- **장애를 기록으로 남겼습니다.** 문제 → 원인 → 해결 → 재발 방지를 17건 정리했습니다. 예: `MultipleBagFetchException`으로 상품 상세가 500이던 문제, 배포 디스크 부족, 키 노출 후 교체, 동시 주문 시 재고 초과 판매.
- **코드 구조에 기준이 있습니다.** Setter 없는 엔티티(Aggregate Root + 도메인 메서드), 타 도메인은 Service를 통해서만 접근.
- **실제로 운영해 봤습니다.** 로컬이 아니라 EC2에 올려 접속 가능한 상태를 유지하고, 운영/로컬 시딩을 분리(`create-drop` 여부로 구분)했습니다.

## 숫자로 보는 결과

| 항목 | 결과 |
|---|---|
| 재고 동시성 | 50명이 동시에 재고 10개 주문 시: 50건 판매(초과) → 10건만 성공, 재고 0 |
| 상품 목록 쿼리 | 상품 10개 기준 N+1 11번 → 1번 (`@EntityGraph`) |
| 테스트 | order.service 85% / members.service 65% / payment.service 61% (Jacoco 라인 커버리지) |
| 장애 기록 | TROUBLESHOOTING 17건 |

## 기술 스택

Java 17 · Spring Boot 3 · Spring Security 6 · JPA/QueryDSL · MySQL 8 · Redis · Docker · GitHub Actions · AWS EC2

## 아쉬운 점과 다음 계획

HTTPS·도메인 미적용, 모니터링 없음, 이미지는 서버 로컬 저장 → RDS/S3 분리와 Actuator 모니터링이 다음 단계입니다.
