setlocal

REM =========================================================
REM Docker Build 를 위한 기본 골격(workshop)을 생성.
REM Destination : C:/Temp/workshop 아래에 생성.
REM =========================================================


REM /workshop
REM   └─ /dstone-batch                             # <Batch Application>
REM      ├─ conf/                                  #   설정 파일
REM      ├─ target/                                #   실행 파일
REM      ├─ 01.dstone-batch-docker.yml             #   개별 Docker Compose 빌드파일
REM      └─ 02.dstone-batch-docker-reg.sh          #   Docker Hub 등록 Shell

set FROM_ROOT=D:\AppHome\framework
set TO_ROOT=C:\Temp\workshop

mkdir %TO_ROOT%

REM 1. Batch Application
mkdir %TO_ROOT%\dstone-batch
mkdir %TO_ROOT%\dstone-batch\conf
mkdir %TO_ROOT%\dstone-batch\target
copy  %FROM_ROOT%\dstone-batch\conf\application.yml %TO_ROOT%\dstone-batch\conf
copy  %FROM_ROOT%\dstone-batch\conf\log4j2.xml %TO_ROOT%\dstone-batch\conf
copy  %FROM_ROOT%\dstone-batch\target\*.jar %TO_ROOT%\dstone-batch\target
copy  %FROM_ROOT%\dstone-batch\docs\docker\dstone-batch\01.dstone-batch-docker.yml %TO_ROOT%\dstone-batch
copy  %FROM_ROOT%\dstone-batch\docs\docker\dstone-batch\02.dstone-batch-docker-reg.sh %TO_ROOT%\dstone-batch

endlocal
