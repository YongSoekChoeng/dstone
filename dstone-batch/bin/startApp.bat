
set JAVA_OPTS=-Xms512m -Xmx1024m
set JAR_FILE=../target/dstone-batch-0.0.1-SNAPSHOT.jar

java %JAVA_OPTS% -jar %JAR_FILE% net.dstone.batch.common.DstoneBatchApplication
