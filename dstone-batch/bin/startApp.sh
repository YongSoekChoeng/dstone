#! /bin/sh

JAVA_OPTS="-Xms512m -Xmx1024m"
JAR_FILE="../target/dstone-batch-0.0.1-SNAPSHOT.jar"

nohup java ${JAVA_OPTS} -jar ${JAR_FILE} net.dstone.batch.common.DstoneBatchApplication > /dev/null 2>&1 &
# java ${JAVA_OPTS} -jar ${JAR_FILE} net.dstone.batch.common.DstoneBatchApplication

