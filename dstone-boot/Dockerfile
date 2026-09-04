# 빌드 컨텍스트는 리포지토리 루트(/app/dstone)여야 한다.
#   docker build -f dstone-boot/Dockerfile -t <tag> .
# dstone-boot이 dstone-common에 의존하는 멀티모듈 구조이므로,
# 루트 aggregator pom.xml + dstone-common + dstone-boot을 함께 복사해 리액터 빌드로 해결한다.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY dstone-common dstone-common
COPY dstone-boot dstone-boot
# 루트 pom.xml이 4개 모듈을 모두 선언하므로, 리액터 구성을 위해 나머지 두 모듈은 pom.xml만 둔다(-pl로 실제 빌드는 제외).
COPY dstone-batch/pom.xml dstone-batch/pom.xml
COPY dstone-batchadmin/pom.xml dstone-batchadmin/pom.xml

RUN mvn -q -pl dstone-common,dstone-boot -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app/dstone/dstone-boot

COPY --from=build /workspace/dstone-boot/target/dstone-boot.war ./dstone-boot.war

EXPOSE 7081

ENTRYPOINT ["java", "-Dspring.profiles.active=k8s", "-jar", "/app/dstone/dstone-boot/dstone-boot.war", "net.dstone.boot.DstoneBootApplication"]
