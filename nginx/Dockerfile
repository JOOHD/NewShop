# Nginx 공식 이미지 사용
# latest는 버전이 바뀔 수 있으므로 운영/포트폴리오에서는 명시 버전 권장
FROM nginx:1.27-alpine

# Nginx 설정 템플릿 복사
# 공식 nginx Docker 이미지는 /etc/nginx/templates/*.template 파일을
# 컨테이너 시작 시 자동으로 envsubst 처리해서 /etc/nginx/conf.d/*.conf로 변환한다.
COPY my-site.conf.template /etc/nginx/templates/default.conf.template

# 별도 CMD를 직접 작성하지 않는다.
# nginx 공식 이미지의 기본 entrypoint가 템플릿 치환 후 nginx를 실행한다.
#
# 내부적으로 대략 다음 흐름으로 동작:
# 1. docker-compose.yml의 environment 값 주입
# 2. ${NGINX_SERVER} 같은 환경 변수 치환
# 3. /etc/nginx/conf.d/default.conf 생성
# 4. nginx -g 'daemon off;' 실행