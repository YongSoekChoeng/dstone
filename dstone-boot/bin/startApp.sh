#! /bin/sh

JAVA_OPTS="-Xms512m -Xmx1024m"
JAR_FILE="../target/dstone-boot.war"

SPRING_PROFILES_ACTIVE=-Dspring.profiles.active=dev

nohup java ${JAVA_OPTS} ${SPRING_PROFILES_ACTIVE} -jar ${JAR_FILE} net.dstone.boot.DstoneBootApplication > /dev/null 2>&1 &
# java ${JAVA_OPTS} ${SPRING_PROFILES_ACTIVE} -jar ${JAR_FILE} net.dstone.boot.DstoneBootApplication 

