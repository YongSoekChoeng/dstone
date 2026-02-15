setlocal

REM =========================================================
REM Docker Build 를 위한 기본 골격(workshop)을 생성.
REM Destination : C:/Temp/workshop 아래에 생성.
REM =========================================================


REM /workshop
REM   └─ /dstone-rabbitmq                          # <RabbitMQ>
REM      ├─ init-data/                             #   초기 데이터
REM      ├─ 01.dstone-rabbitmq-docker.yml          #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-rabbitmq-docker-reg.sh       #   Docker Hub 등록 Shell

set FROM_ROOT=D:\AppHome\framework
set TO_ROOT=C:\Temp\workshop

mkdir %TO_ROOT%

REM 1. RabbitMQ
mkdir %TO_ROOT%\dstone-rabbitmq
mkdir %TO_ROOT%\dstone-rabbitmq\init-data
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-rabbitmq %TO_ROOT%\dstone-rabbitmq /E
del %TO_ROOT%\dstone-rabbitmq\*.bat

endlocal
