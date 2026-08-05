# 빌드 스테이지
FROM eclipse-temurin:17-jdk as builder
WORKDIR /workspace/app
COPY . .
RUN ./gradlew build

# 실행 스테이지 — 실행만 하면 되므로 jre로 충분 (jdk 대비 이미지 용량 절감)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar
CMD ["java", "-jar", "app.jar"]

