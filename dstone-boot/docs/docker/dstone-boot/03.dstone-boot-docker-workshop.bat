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

endlocal
