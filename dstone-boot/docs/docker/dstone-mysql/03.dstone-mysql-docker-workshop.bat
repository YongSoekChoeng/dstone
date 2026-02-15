setlocal

REM =========================================================
REM Docker Build 를 위한 기본 골격(workshop)을 생성.
REM Destination : C:/Temp/workshop 아래에 생성.
REM =========================================================


REM /workshop
REM   └─ /dstone-mysql                             # <Database (MySQL)>
REM      ├─ init-db/                               #   DB 초기화 스크립트
REM      ├─ 01.dstone-mysql-docker.yml             #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-mysql-docker-reg.sh          #   Docker Hub 등록 Shell

set FROM_ROOT=D:\AppHome\framework
set TO_ROOT=C:\Temp\workshop

mkdir %TO_ROOT%

REM 1. Database
mkdir %TO_ROOT%\dstone-mysql
mkdir %TO_ROOT%\dstone-mysql\init-db
xcopy %FROM_ROOT%\dstone-boot\docs\docker\dstone-mysql %TO_ROOT%\dstone-mysql /E
del %TO_ROOT%\dstone-mysql\*.bat

endlocal
