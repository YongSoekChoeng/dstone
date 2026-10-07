# 빌드 컨텍스트는 리포지토리 루트(/app/dstone)여야 한다.
#   docker build -f dstone-ai-engine/Dockerfile -t <tag> .
# dstone-ai-engine이 dstone-common에 의존하는 멀티모듈 구조이므로,
# 루트 aggregator pom.xml + dstone-common + dstone-ai-engine을 함께 복사해 리액터 빌드로 해결한다.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY dstone-common dstone-common
COPY dstone-ai-engine dstone-ai-engine
# 루트 pom.xml이 5개 모듈을 모두 선언하므로, 리액터 구성을 위해 나머지 모듈은 pom.xml만 둔다(-pl로 실제 빌드는 제외).
COPY dstone-boot/pom.xml dstone-boot/pom.xml
COPY dstone-batch/pom.xml dstone-batch/pom.xml
COPY dstone-batchadmin/pom.xml dstone-batchadmin/pom.xml

RUN mvn -q -pl dstone-common,dstone-ai-engine -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app/dstone/dstone-ai-engine

COPY --from=build /workspace/dstone-ai-engine/target/dstone-ai-engine.jar ./dstone-ai-engine.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Dspring.profiles.active=k8s", "-jar", "/app/dstone/dstone-ai-engine/dstone-ai-engine.jar", "net.dstone.ai.DstoneAiEngineApplication"]
