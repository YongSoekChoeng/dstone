setlocal

REM =========================================================
REM Docker Build 를 위한 기본 골격(workshop)을 생성.
REM Destination : C:/Temp/workshop 아래에 생성.
REM =========================================================


REM /workshop
REM   └─ /dstone-boot                              # <Boot Application>
REM      ├─ conf/                                  #   설정 파일
REM      ├─ target/                                #   실행 파일
REM      ├─ 01.dstone-boot-docker.yml              #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-boot-docker-reg.sh           #   Docker Hub 등록 Shell
REM   └─ /dstone-mysql                             # <Database (MySQL)>
REM      ├─ init-db/                               #   DB 초기화 스크립트
REM      ├─ 01.dstone-mysql-docker.yml             #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-mysql-docker-reg.sh          #   Docker Hub 등록 Shell
REM   └─ /dstone-rabbitmq                          # <RabbitMQ>
REM      ├─ init-data/                             #   초기 데이터
REM      ├─ 01.dstone-rabbitmq-docker.yml          #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-rabbitmq-docker-reg.sh       #   Docker Hub 등록 Shell
REM   └─ /dstone-redis                             # <Redis>
REM      ├─ 01.dstone-redis-docker.yml             #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-redis-docker-reg.sh          #   Docker Hub 등록 Shell
REM   └─ /dstone-boot-docker-compose.yml           # dstone-boot Docker Compose 빌드파일

set FROM_ROOT=D:\AppHome\framework
set TO_ROOT=C:\Temp\workshop

mkdir %TO_ROOT%

REM 1. Boot Application
mkdir %TO_ROOT%\dstone-boot
mkdir %TO_ROOT%\dstone-boot\conf
mkdir %TO_ROOT%\dstone-boot\target
copy  %FROM_ROOT%\dstone-boot\conf\application.yml %TO_ROOT%\dstone-boot\conf
copy  %FROM_ROOT%\dstone-boot\conf\log4j2.xml %TO_ROOT%\dstone-boot\conf
copy  %FROM_ROOT%\dstone-boot\target\dstone-boot.war %TO_ROOT%\dstone-boot\target
copy  %FROM_ROOT%\dstone-boot\docs\docker\dstone-boot\01.dstone-boot-docker.yml %TO_ROOT%\dstone-boot
copy  %FROM_ROOT%\dstone-boot\docs\docker\dstone-boot\02.dstone-boot-docker-reg.sh %TO_ROOT%\dstone-boot
copy  %FROM_ROOT%\dstone-boot\docs\docker\dstone-boot-docker-compose.yml %TO_ROOT%

REM 2. Database
mkdir %TO_ROOT%\dstone-mysql
mkdir %TO_ROOT%\dstone-mysql\init-db
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-mysql %TO_ROOT%\dstone-mysql /E
del %TO_ROOT%\dstone-mysql\*.bat

REM 3. RabbitMQ
mkdir %TO_ROOT%\dstone-rabbitmq
mkdir %TO_ROOT%\dstone-rabbitmq\init-data
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-rabbitmq %TO_ROOT%\dstone-rabbitmq /E
del %TO_ROOT%\dstone-rabbitmq\*.bat

REM 4. Redis
mkdir %TO_ROOT%\dstone-redis
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-redis %TO_ROOT%\dstone-redis /E
del %TO_ROOT%\dstone-redis\*.bat

endlocal
