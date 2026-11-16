# syntax=docker/dockerfile:1
# 백엔드(Spring Boot 3.3.4, Java 17) 이미지. 빌드 컨텍스트는 backend/ 폴더.
#   docker build -t godgill/backend:dev backend/

# ── 1단계: 빌드 ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 만들어 소스만 바뀔 때 캐시를 재사용한다
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
# Windows에서 체크아웃된 gradlew(CRLF) 대비
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
# 테스트는 CI에서 이미 돌리므로 이미지 빌드에서는 생략한다
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# ── 2단계: 실행 ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
# 제보 이미지 로컬 저장(STORAGE_TYPE=local)용 폴더
RUN mkdir -p /app/local-storage && chown app:app /app/local-storage

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
