# 앱 공용 컨테이너 빌드(APP 인자로 4개 앱 선택: app-api·app-admin·app-batch·app-migration).
#   docker build --build-arg APP=app-api -t auth-api .
# 빌드는 컨테이너 안에서 래퍼로 수행한다(호스트 산출물 미신뢰 — 재현 가능 빌드). 테스트는 CI 게이트 소유라
# 이미지 빌드에서는 건너뛴다(bootJar만).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
ARG APP
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew ":module-apps:${APP}:bootJar" --no-daemon -q

FROM eclipse-temurin:25-jre
ARG APP
# bootJar 산출물 이름은 ${project.name}.jar로 고정이다(버전 미지정 구성).
COPY --from=build "/workspace/module-apps/${APP}/build/libs/${APP}.jar" /app/app.jar
# 루트 비실행(임의 numeric UID — 파일 쓰기 없음).
USER 65532:65532
# 시크릿·접속정보는 환경변수 주입 계약을 따른다(docs/ops/deployment.md).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
