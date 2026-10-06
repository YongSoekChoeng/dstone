#! /bin/sh

# 어디서 부르든 이 스크립트가 있는 폴더(bin)에서 실행한다. 아래의 상대 경로(../target)와 application.pid 가 여기를 기준으로 한다.
cd "$(dirname "$0")" || exit 1

JAVA_OPTS="-Xms512m -Xmx1024m"
JAR_FILE="../target/dstone-knowledge.jar"

# 프로파일은 기본 wsl. 다른 프로파일로 띄우려면 DSTONE_PROFILE 을 준다(Jenkins 배포는 DSTONE_PROFILE=vm).
SPRING_PROFILES_ACTIVE=-Dspring.profiles.active=${DSTONE_PROFILE:-wsl}

# 화면 출력(stdout)은 버린다. 로그는 conf/log4j2.xml 에 적힌 대로만 남는다:
#   ${APP_HOME}/LOGS/dstone-knowledge/execution/execution.log  (APP_HOME 은 conf/env-*.properties)
# PID 는 프로그램이 뜨면서 이 폴더의 application.pid 에 적는다(stopApp.sh / statusApp.sh 가 읽는다).
nohup java ${JAVA_OPTS} ${SPRING_PROFILES_ACTIVE} -jar ${JAR_FILE} net.dstone.knowledge.DstoneKnowledgeApplication > /dev/null 2>&1 &
# java ${JAVA_OPTS} ${SPRING_PROFILES_ACTIVE} -jar ${JAR_FILE} net.dstone.knowledge.DstoneKnowledgeApplication
