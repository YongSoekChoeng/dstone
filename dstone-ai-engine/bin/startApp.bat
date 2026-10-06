
set JAVA_OPTS=-Xms512m -Xmx1024m
set JAR_FILE=../target/dstone-ai-engine.jar

set SPRING_PROFILES_ACTIVE=-Dspring.profiles.active=local

rem 로그는 conf/log4j2.xml 에 적힌 대로 남는다: %APP_HOME%/LOGS/dstone-ai-engine/execution/execution.log (APP_HOME 은 conf/env.properties)
java %JAVA_OPTS% %SPRING_PROFILES_ACTIVE% -jar %JAR_FILE% net.dstone.ai.DstoneAiEngineApplication
