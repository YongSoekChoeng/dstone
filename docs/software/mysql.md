# MySQL

## 개요
dstone 전 모듈의 메인 관계형 데이터베이스. HikariCP + MyBatis + log4jdbc 조합으로 접속한다.

## 설치 정보
- 버전: MySQL Server 8.4.11 (Ubuntu 26.04 공식 패키지, `mysql_native_password` 관련 빌드)
- 설치 방식: Ubuntu 공식 저장소 apt 패키지
- 서비스명: `mysql.service` (systemd, 부팅 시 자동시작은 비활성화되어 있어 수동 기동)

## 설치 방법
```bash
sudo apt update
sudo apt install -y mysql-server
```

초기 보안 설정(권장):
```bash
sudo mysql_secure_installation
```

root 접속 확인:
```bash
sudo mysql -u root -p
```

## 서비스 시작/중지
```bash
/usr/local/bin/start-mysql.sh   # sudo systemctl start mysql
/usr/local/bin/stop-mysql.sh    # sudo systemctl stop mysql
```

## 주요 설정
설정 파일: `/etc/mysql/mysql.conf.d/mysqld.cnf`
```ini
[mysqld]
mysql_native_password = ON
user                   = mysql
bind-address           = 127.0.0.1
mysqlx-bind-address    = 127.0.0.1
key_buffer_size        = 16M
myisam-recover-options = BACKUP
log_error              = /var/log/mysql/error.log
max_binlog_size        = 100M
```
- 포트: 3306 (기본값, 미변경)
- `bind-address=127.0.0.1`로 로컬(WSL 내부)에서만 접속 가능하다. Windows 호스트나 외부에서 접속하려면 바인딩 주소 변경 및 방화벽 설정이 추가로 필요하다.

## dstone 프로젝트에서의 역할
- `dstone-boot`: `common`/`sample` 데이터소스가 `sampleDB`를 바라봄 (`ConfigDatasource.java`).
- `dstone-batch`: Spring Batch 메타데이터 테이블(`src/main/resources/schema/*.sql`)을 수동으로 이 DB에 생성해야 함 (`initialize-schema: NEVER`).
- `dstone-batchadmin`: `common` 데이터소스(`batchadmin` 스키마)로 로그인 사용자/배치서버 등록정보 관리.
- 접속 정보는 각 모듈 `conf/env.properties`의 `DB_HOST`/`DB_PORT`와 `application.yml`의 `ENC(...)` 암호화된 비밀번호(Jasypt)로 구성된다.
