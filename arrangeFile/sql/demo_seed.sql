-- 로컬 DB(create-drop)는 재기동할 때마다 비워지므로, 앱이 완전히 뜬 뒤 실행한다.
-- 실행: mysql -uroot -p1234 --default-character-set=utf8mb4 < arrangeFile/sql/demo_seed.sql
-- 로그인: user@jooshop.com / admin@jooshop.com (비밀번호 모두 Test1234!)
USE shop;

-- 1) 회원 (이미 같은 이메일이 있으면 건너뜀 — 이메일 중복 시 로그인 401 방지)
-- 비밀번호는 둘 다 Test1234!
-- admin: 관리자 권한(ADMIN, is_admin=1)
INSERT INTO member (email, password, username, nickname, phone, member_role, social_type, social_id, joined_at,
  is_active, is_banned, is_account_expired, is_password_expired, is_admin, is_certified_email, created_at, updated_at)
SELECT 'admin@jooshop.com', '$2a$10$6YjaKLbOCXuS7xB.tw4axOnEjrLBbiXAjylLKv5AHcybbqBapxnw.', 'admin', '관리자', '01012345678',
  'ADMIN', 'GENERAL', NULL, NOW(), 1, 0, 0, 0, 1, 1, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM member WHERE email='admin@jooshop.com');

-- user: 일반 회원
INSERT INTO member (email, password, username, nickname, phone, member_role, social_type, social_id, joined_at,
  is_active, is_banned, is_account_expired, is_password_expired, is_admin, is_certified_email, created_at, updated_at)
SELECT 'user@jooshop.com', '$2a$10$6YjaKLbOCXuS7xB.tw4axOnEjrLBbiXAjylLKv5AHcybbqBapxnw.', 'user', '일반회원', '01012345678',
  'USER', 'GENERAL', NULL, NOW(), 1, 0, 0, 0, 0, 1, NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM member WHERE email='user@jooshop.com');

-- 2) 프로필 (이미지 업로드 전까지 기본 아바타)
INSERT INTO member_profile (member_id, introduction, created_at, updated_at)
SELECT m.member_id, '자기 소개를 수정해주세요.', NOW(), NOW() FROM member m
WHERE m.email IN ('admin@jooshop.com', 'user@jooshop.com')
  AND NOT EXISTS (SELECT 1 FROM member_profile p WHERE p.member_id = m.member_id);

-- 3) 주문 4건 (orders + order_product)
-- 0001: Stone Roses Jersey Blue L (카드, 1일 전)
INSERT INTO orders (member_id, order_name, product_name, pay_method, payment_status, merchant_uid, total_price,
  address, detail_address, post_code, phone_number, created_at)
VALUES ((SELECT member_id FROM member WHERE email='user@jooshop.com'), '일반회원',
  'Manchester United x adidas Stone Roses Jersey Blue', 'card', 'COMPLETE', 'demo-order-0001', 168900,
  '서울특별시 강남구 테헤란로 123', '101동 1001호', '06234', '01012345678', DATE_SUB(NOW(), INTERVAL 1 DAY));
INSERT INTO order_product (orders_id, product_variant_id, price_at_order, product_name, product_size, product_img, quantity, reviewed, returned)
VALUES ((SELECT order_id FROM orders WHERE merchant_uid='demo-order-0001'),
  (SELECT v.inventory_id FROM product_management v JOIN products_table p ON p.productId=v.product_id
    WHERE p.productName='Manchester United x adidas Stone Roses Jersey Blue' AND v.size='L' LIMIT 1),
  168900, 'Manchester United x adidas Stone Roses Jersey Blue', 'L', '/images/demo/StoneRoses_jersey_short.jpg', 1, 0, 0);

-- 0002: Stone Roses T-Shirt White L x2 (카드, 3일 전)
INSERT INTO orders (member_id, order_name, product_name, pay_method, payment_status, merchant_uid, total_price,
  address, detail_address, post_code, phone_number, created_at)
VALUES ((SELECT member_id FROM member WHERE email='user@jooshop.com'), '일반회원',
  'Manchester United x adidas Stone Roses T-Shirt White', 'card', 'COMPLETE', 'demo-order-0002', 150200,
  '서울특별시 강남구 테헤란로 123', '101동 1001호', '06234', '01012345678', DATE_SUB(NOW(), INTERVAL 3 DAY));
INSERT INTO order_product (orders_id, product_variant_id, price_at_order, product_name, product_size, product_img, quantity, reviewed, returned)
VALUES ((SELECT order_id FROM orders WHERE merchant_uid='demo-order-0002'),
  (SELECT v.inventory_id FROM product_management v JOIN products_table p ON p.productId=v.product_id
    WHERE p.productName='Manchester United x adidas Stone Roses T-Shirt White' AND v.size='L' LIMIT 1),
  75100, 'Manchester United x adidas Stone Roses T-Shirt White', 'L', '/images/demo/StoneRoses_T-ShirtWhite.avif', 2, 0, 0);

-- 0003: 한 주문에 상품 2종 (카드, 7일 전)
INSERT INTO orders (member_id, order_name, product_name, pay_method, payment_status, merchant_uid, total_price,
  address, detail_address, post_code, phone_number, created_at)
VALUES ((SELECT member_id FROM member WHERE email='user@jooshop.com'), '일반회원',
  'Manchester United x adidas EQT Jersey Red 외 1건', 'card', 'COMPLETE', 'demo-order-0003', 300200,
  '서울특별시 강남구 테헤란로 123', '101동 1001호', '06234', '01012345678', DATE_SUB(NOW(), INTERVAL 7 DAY));
INSERT INTO order_product (orders_id, product_variant_id, price_at_order, product_name, product_size, product_img, quantity, reviewed, returned)
VALUES ((SELECT order_id FROM orders WHERE merchant_uid='demo-order-0003'),
  (SELECT v.inventory_id FROM product_management v JOIN products_table p ON p.productId=v.product_id
    WHERE p.productName='Manchester United x adidas EQT Jersey Red' AND v.size='M' LIMIT 1),
  131300, 'Manchester United x adidas EQT Jersey Red', 'M', '/images/demo/EQT_jersey.avif', 1, 0, 0);
INSERT INTO order_product (orders_id, product_variant_id, price_at_order, product_name, product_size, product_img, quantity, reviewed, returned)
VALUES ((SELECT order_id FROM orders WHERE merchant_uid='demo-order-0003'),
  (SELECT v.inventory_id FROM product_management v JOIN products_table p ON p.productId=v.product_id
    WHERE p.productName='Manchester United x adidas Stone Roses Jersey Blue' AND v.size='L' LIMIT 1),
  168900, 'Manchester United x adidas Stone Roses Jersey Blue', 'L', '/images/demo/StoneRoses_jersey_short.jpg', 1, 0, 0);

-- 0004: EQT Half Zip Top Black XL (계좌이체, 12일 전)
INSERT INTO orders (member_id, order_name, product_name, pay_method, payment_status, merchant_uid, total_price,
  address, detail_address, post_code, phone_number, created_at)
VALUES ((SELECT member_id FROM member WHERE email='user@jooshop.com'), '일반회원',
  'Manchester United x adidas EQT Half Zip Top Black', 'trans', 'COMPLETE', 'demo-order-0004', 159500,
  '서울특별시 강남구 테헤란로 123', '101동 1001호', '06234', '01012345678', DATE_SUB(NOW(), INTERVAL 12 DAY));
INSERT INTO order_product (orders_id, product_variant_id, price_at_order, product_name, product_size, product_img, quantity, reviewed, returned)
VALUES ((SELECT order_id FROM orders WHERE merchant_uid='demo-order-0004'),
  (SELECT v.inventory_id FROM product_management v JOIN products_table p ON p.productId=v.product_id
    WHERE p.productName='Manchester United x adidas EQT Half Zip Top Black' AND v.size='XL' LIMIT 1),
  159500, 'Manchester United x adidas EQT Half Zip Top Black', 'XL', '/images/demo/EQT_halfzip.avif', 1, 0, 0);

-- 4) 좋아요 (상품 앞 4개)
INSERT INTO wish_list (member_id, product_id)
SELECT (SELECT member_id FROM member WHERE email='user@jooshop.com'), productId
FROM products_table ORDER BY productId LIMIT 4;

-- 확인
SELECT member_id, email FROM member;
SELECT order_id, merchant_uid, total_price FROM orders;
SELECT COUNT(*) AS wish_cnt FROM wish_list;
