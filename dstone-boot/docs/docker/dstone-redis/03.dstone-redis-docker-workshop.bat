setlocal

REM =========================================================
REM Docker Build 를 위한 기본 골격(workshop)을 생성.
REM Destination : C:/Temp/workshop 아래에 생성.
REM =========================================================


REM /workshop
REM   └─ /dstone-redis                             # <Redis>
REM      ├─ 01.dstone-redis-docker.yml             #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-redis-docker-reg.sh          #   Docker Hub 등록 Shell

set FROM_ROOT=D:\AppHome\framework
set TO_ROOT=C:\Temp\workshop

mkdir %TO_ROOT%

REM 1. Redis
mkdir %TO_ROOT%\dstone-redis
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-redis %TO_ROOT%\dstone-redis /E
del %TO_ROOT%\dstone-redis\*.bat

endlocal
