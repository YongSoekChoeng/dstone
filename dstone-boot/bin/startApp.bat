
set JAVA_OPTS=-Xms512m -Xmx1024m
set JAR_FILE=../target/dstone-boot.war

set SPRING_PROFILES_ACTIVE=-Dspring.profiles.active=local

java %JAVA_OPTS% %SPRING_PROFILES_ACTIVE% -jar %JAR_FILE% net.dstone.boot.DstoneBootApplication

rem set SCOUTER_OPTS=-javaagent:"D:\AppHome\framework\dstone-boot\WorkShop\03.Tools\02.Scouter\scouter\agent.java\scouter.agent.jar"
rem set SCOUTER_OPTS=%SCOUTER_OPTS% -Dscouter.config="D:\AppHome\framework\dstone-boot\WorkShop\03.Tools\02.Scouter\scouter\agent.java\conf\scouter.conf"
rem set SCOUTER_OPTS=%SCOUTER_OPTS% -Dobj_name=dstone

rem java %JAVA_OPTS% %SPRING_PROFILES_ACTIVE% %SCOUTER_OPTS% -jar %JAR_FILE% net.dstone.boot.DstoneBootApplication
