# MySQL

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 주요 설정](#5-주요-설정)
- [6. dstone 데이터베이스/사용자/스키마 초기화 (설치 후 필수)](#6-dstone-데이터베이스사용자스키마-초기화-설치-후-필수)
  - [6.1 데이터베이스/사용자 매핑표](#61-데이터베이스사용자-매핑표)
  - [6.2 실행 순서](#62-실행-순서)
  - [6.3 `validate_password` 컴포넌트 주의사항](#63-validate_password-컴포넌트-주의사항)
  - [6.4 초기화 확인](#64-초기화-확인)
- [7. 비밀번호(Jasypt `ENC(...)`) 다루기](#7-비밀번호jasypt-enc-다루기)
- [8. dstone 프로젝트에서의 역할](#8-dstone-프로젝트에서의-역할)

## 1. 개요
dstone 전 모듈의 메인 관계형 데이터베이스. HikariCP + MyBatis + log4jdbc 조합으로 접속한다.

## 2. 설치 정보
- 버전: MySQL Server 8.4.11 (Ubuntu 26.04 공식 패키지, `mysql_native_password` 관련 빌드)
- 설치 방식: Ubuntu 공식 저장소 apt 패키지
- 서비스명: `mysql.service` (systemd, 부팅 시 자동시작은 비활성화되어 있어 수동 기동)

## 3. 설치 방법
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

## 4. 서비스 시작/중지
```bash
/usr/local/bin/start-mysql.sh
/usr/local/bin/stop-mysql.sh
```

`start-mysql.sh`:
```sh
#!/bin/sh

# 이전 실행에서 StartLimitBurst에 걸려 failed 상태로 남아있으면 start가 거부되므로 먼저 리셋
sudo systemctl reset-failed mysql.service >/dev/null 2>&1

sudo systemctl start mysql

for i in 1 2 3 4 5; do
    if systemctl is-active --quiet mysql.service; then
        echo "Mysql started !!!"
        exit 0
    fi
    sleep 1
done

echo "Mysql FAILED to start. Check: sudo systemctl status mysql.service / sudo journalctl -xeu mysql.service" >&2
exit 1
```

`mysql.service`는 `disabled`라 WSL 부팅 시 자동으로 뜨지 않는다. `bind-address`에 `172.18.0.1`([5절](#5-주요-설정) 참고)이 포함되어 있어 Docker/kind 네트워크가 뜨기 전에 mysql을 먼저 켜면 `bind: Cannot assign requested address`로 기동이 실패하는 레이스 컨디션이 있다 — 그래서 `~/start.sh`는 반드시 Docker/kind를 먼저 올린 뒤 mysql을 올리도록 순서가 고정되어 있다 (상세: [environment.md 5.1절](../environment.md#51-개발환경-시작-startsh)). 2026-09-06 이전 버전은 `sudo systemctl start mysql`의 종료 코드를 확인하지 않고 무조건 "Mysql started !!!"를 출력해서, 이 레이스로 실제 기동이 실패했을 때도 성공한 것처럼 로그가 찍히는 문제가 있었다. 지금은 `systemctl reset-failed`로 이전 실행에서 남은 실패 상태를 지운 뒤 `start` → 최대 5초간 `systemctl is-active` 재확인으로 실제 상태에 따라 정확한 성공/실패 메시지를 출력한다.

`stop-mysql.sh`:
```sh
sudo systemctl stop mysql
echo "Mysql stopped !!!"
```

## 5. 주요 설정
설정 파일: `/etc/mysql/mysql.conf.d/mysqld.cnf`
```ini
[mysqld]
mysql_native_password = ON
user                   = mysql
bind-address           = 127.0.0.1,172.18.0.1
mysqlx-bind-address    = 127.0.0.1
key_buffer_size        = 16M
myisam-recover-options = BACKUP
log_error              = /var/log/mysql/error.log
max_binlog_size        = 100M
```
- 포트: 3306 (기본값, 미변경)
- `bind-address`는 기본값(`127.0.0.1`)에 `172.18.0.1`(kind 도커 브리지 게이트웨이 IP)을 추가해, `kind` 클러스터의 Pod에서도 접속할 수 있도록 확장했다(클라우드 아키텍처 시뮬레이션의 일부 — [cloud-architecture.md](../cloud-architecture.md) 참고). 그 외 인터페이스(Windows 호스트, 외부 네트워크)로는 여전히 노출되지 않는다.

## 6. dstone 데이터베이스/사용자/스키마 초기화 (설치 후 필수)

MySQL을 설치하고 기동한 것만으로는 dstone 애플리케이션이 뜨지 않는다. 각 모듈이 참조하는 스키마/사용자/테이블/(선택) 샘플 데이터를 **최초 1회 수동으로 생성**해야 한다(Spring Boot/Batch가 자동 생성하지 않도록 전 모듈이 `initialize-schema: NEVER`로 설정되어 있다 — CLAUDE.md 참고). 실행할 SQL 파일은 이미 리포지토리 안에 전부 포함되어 있으며, git 클론([git.md](git.md#5-dstone-소스-코드-받기)) 직후 아래 순서 그대로 실행하면 된다.

### 6.1 데이터베이스/사용자 매핑표

각 모듈 `conf/application.yml`의 `jdbc-url`/`username`/`password`(Jasypt `ENC(...)`)가 아래 값으로 이미 고정되어 있다 — 즉 이 표와 **똑같은 이름의 데이터베이스/사용자/비밀번호**로 만들어야 애플리케이션이 별도 설정 변경 없이 바로 접속된다.

| 데이터베이스 | 용도 | 사용자 | 비밀번호(평문) | 사용 모듈 |
|---|---|---|---|---|
| `sampleDB` | 샘플 회원/그룹/권한 + SAGA/Outbox 샘플 테이블 | `sampleuser` | `sampleuser` | dstone-boot(`common`/`sample` 데이터소스), dstone-batch(`sample` 데이터소스) |
| `analyzeDB` | 소스 코드 정적분석기 결과 저장 | `analyzeuser` | `analyzeuser` | dstone-boot(`analyzer` 데이터소스) |
| `dataflow` | Spring Batch 메타데이터(`BATCH_JOB_INSTANCE` 등) | `dataflow` | `dataflow` | dstone-batch(`common` 데이터소스), dstone-batchadmin이 `TB_BATCH_SERVER` 레지스트리를 통해 REST/직접조회로 참조 |
| `batchadmin` | dstone-batchadmin 자체 스키마(로그인 사용자/배치서버 레지스트리/Job 메타데이터) | `batchadmin` | `batchadmin123` | dstone-batchadmin(`common` 데이터소스) |

> 사용자는 전부 `'<user>'@'%'`(모든 호스트 허용)로 생성되고 각자 자기 스키마에만 `GRANT ALL PRIVILEGES`가 부여된다 — DB_HOST가 `localhost`가 아닌 원격/Pod 환경(예: kind, VM)에서 접속해도 그대로 동작한다.

### 6.2 실행 순서

각 SQL은 `01-init-*`(DB/사용자 생성) → `02-create-table-*`(테이블 생성) → `03-create-data-*`(선택, 샘플/초기 데이터) 순으로 실행해야 한다. `01`/`02` 파일은 모듈 간에 내용이 겹치는 것도 있지만(예: `sampleDB` 생성 SQL이 dstone-boot/dstone-batch 양쪽에 있음) 전부 `CREATE ... IF NOT EXISTS`라 여러 번 실행해도 안전하다.

```bash
cd /app/dstone   # 리포지토리 루트

# 1) DB + 사용자 생성 (dataflow/sampleDB는 dstone-boot 쪽 파일로 한 번에 생성됨)
sudo mysql -u root < dstone-boot/src/main/resources/schema/01-init-mysql-dstone-boot.sql        # sampleDB + sampleuser
sudo mysql -u root < dstone-boot/src/main/resources/schema/01-init-mysql-dstone-analyze.sql      # analyzeDB + analyzeuser
sudo mysql -u root < dstone-batch/src/main/resources/schema/01-init-mysql-dstone-batch.sql       # sampleDB(중복,무해) + dataflow + dataflow 사용자
sudo mysql -u root < dstone-batchadmin/src/main/resources/schema/01-init-mysql-dstone-batchadmin.sql  # batchadmin + batchadmin 사용자

# 2) 테이블 생성
sudo mysql -u root < dstone-boot/src/main/resources/schema/02-create-table-mysql-dstone-boot.sql        # sampleDB: SAMPLE_* + TB_SAGA_*/TB_OUTBOX_MESSAGE
sudo mysql -u root < dstone-boot/src/main/resources/schema/02-create-table-mysql-dstone-analyze.sql      # analyzeDB
sudo mysql -u root < dstone-batch/src/main/resources/schema/02-create-table-mysql-dstone-batch.sql       # dataflow: Spring Batch 5.1 메타데이터 테이블(BATCH_JOB_INSTANCE 등)
sudo mysql -u root < dstone-batchadmin/src/main/resources/schema/02-create-table-mysql-dstone-batchadmin.sql  # batchadmin: TB_ADMIN_USER/TB_BATCH_SERVER/TB_BATCH_JOB/TB_BATCH_JOB_PARAM

# 3) (선택) 샘플/초기 데이터 — 없어도 앱은 뜨지만, 화면에서 바로 확인해보려면 실행 권장
sudo mysql -u root < dstone-boot/src/main/resources/schema/03-create-data-mysql-dstone-boot.sql          # SAMPLE_MEMBER 등 샘플 회원 데이터
sudo mysql -u root < dstone-batchadmin/src/main/resources/schema/03-create-data-mysql-dstone-batchadmin.sql  # 관리자 로그인 계정(USER_ID=batchadmin) + 배치서버 등록(로컬:6081) + 샘플 Job 11개 메타데이터
```
- `sudo mysql -u root`는 Ubuntu MySQL 패키지의 `auth_socket`/`unix_socket` 인증 기본값 때문에 별도 비밀번호 없이 OS 사용자(`root`)로 소켓 인증된다. `mysql_secure_installation`에서 root 비밀번호를 설정했다면 `mysql -u root -p`로 바꿔 실행한다.
- `03-create-data-mysql-dstone-batchadmin.sql`이 심는 관리자 로그인 계정(`TB_ADMIN_USER.USER_ID='batchadmin'`)의 비밀번호는 Jasypt로 암호화되어 있어 이 문서만으로는 평문을 알 수 없다 — 로그인이 안 되면 `TB_ADMIN_USER.USER_PW`를 [7절](#7-비밀번호jasypt-enc-다루기)의 방법으로 새로 암호화해 직접 `UPDATE`한다.
- `TB_BATCH_SERVER`에 등록되는 로컬 배치서버 행의 `DB_PASSWORD`도 같은 이유로 Jasypt `ENC(...)` 값이다 — dstone-batchadmin이 이 값을 복호화해 `dataflow` DB에 직접 접속(모니터링 화면용)하므로, `dataflow` 계정 비밀번호를 바꿨다면 이 행도 같이 갱신해야 한다.

### 6.3 `validate_password` 컴포넌트 주의사항

`01-init-mysql-dstone-batchadmin.sql` 첫 줄이 `SET GLOBAL validate_password.policy = LOW; SET GLOBAL validate_password.length = 4;`로 시작한다. `validate_password` 컴포넌트가 설치/활성화된 MySQL(예: `mysql_secure_installation`에서 활성화를 선택한 경우)에서는 위 표의 비밀번호(예: `sampleuser`, `dataflow`)처럼 길이가 짧고 규칙이 단순한 값이 `ERROR 1819 (HY000): Your password does not satisfy the current policy requirements`로 거부될 수 있다. 컴포넌트가 아예 설치되어 있지 않다면 이 `SET GLOBAL` 두 줄은 에러 없이 무시된다(변수 자체가 없어서 에러가 나면 컴포넌트 미설치이므로 안전하게 스킵 가능 — 그 경우 해당 두 줄만 지우고 재실행).

### 6.4 초기화 확인

```bash
sudo mysql -u root -e "SHOW DATABASES;"                      # sampleDB / analyzeDB / dataflow / batchadmin 존재 확인
sudo mysql -u root -e "SELECT User, Host FROM mysql.user WHERE User IN ('sampleuser','analyzeuser','dataflow','batchadmin');"
mysql -u sampleuser -psampleuser -h 127.0.0.1 -e "SHOW TABLES;" sampleDB      # 애플리케이션과 동일한 계정으로 접속 테스트
mysql -u dataflow -pdataflow -h 127.0.0.1 -e "SHOW TABLES;" dataflow
```

## 7. 비밀번호(Jasypt `ENC(...)`) 다루기

각 모듈 `conf/application.yml`의 DB/RabbitMQ 계정은 평문이 아니라 Jasypt로 암호화된 `ENC(...)` 형식이다. 복호화 키는 **`conf/env.properties`의 `jasypt.encryptor.password`가 아니라**, `dstone-common`의 `net.dstone.common.utils.EncUtil`(`ENC_KEY` 상수, 알고리즘 `PBEWithSHA256And128BitAES-CBC-BC`, BouncyCastle 제공)에 **소스 코드로 하드코딩**되어 있고, 각 모듈의 `ConfigEnc` 클래스(`@EnableEncryptableProperties` + `@Bean("jasyptStringEncryptor")`)가 이 값을 그대로 jasypt-spring-boot-starter에 등록해서 쓴다. CLAUDE.md/`dstone-common.md`에 적힌 "복호화 키는 `env.properties`의 `jasypt.encryptor.password`" 서술은 실제 코드와 다르므로 참고만 하고 실제로는 `EncUtil.java`를 기준으로 삼는다.

즉 위 6.1절의 평문 비밀번호를 그대로 쓰는 한 `application.yml`을 전혀 건드릴 필요가 없다. **DB/큐 비밀번호를 다른 값으로 바꾸고 싶을 때만** 새 `ENC(...)` 값을 만들어 넣어야 하며, 방법은 두 가지다.

- **(권장) `dstone-common`의 Maven 의존성 클래스패스를 그대로 재사용해 즉석 스크립트로 계산**
  ```bash
  cd /app/dstone/dstone-common
  mvn -q clean install -DskipTests                                              # 최초 1회, 로컬 .m2에 설치
  mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/dstone-common-cp.txt  # jasypt/bouncycastle 등 전체 의존성 classpath 뽑기

  cat > /tmp/EncOnce.java <<'EOF'
  public class EncOnce {
      public static void main(String[] args) {
          System.out.println(net.dstone.common.utils.EncUtil.encrypt(args[0]));
      }
  }
  EOF
  CP="target/classes:$(cat /tmp/dstone-common-cp.txt)"
  javac -cp "$CP" -d /tmp /tmp/EncOnce.java
  java -cp "$CP:/tmp" EncOnce '새비밀번호'
  ```
- **(참고용) `dstone-boot/src/main/java/net/dstone/boot/test/TestBean.java`의 `암복호화()` 메소드**: `EncUtil.encrypt()`/`decrypt()` 호출 예시가 그대로 들어있는 스크래치 클래스다(실행 진입점은 아님 — 위 방식으로 직접 클래스패스를 구성하거나 IDE에서 `main()`으로 돌려본다).

새 `ENC(...)` 값을 얻었으면 해당 모듈 `conf/application.yml`의 `username`/`password`를 교체하고, 실제 DB 사용자 비밀번호도 `ALTER USER '<user>'@'%' IDENTIFIED BY '새비밀번호';`로 동일하게 맞춰준다.

## 8. dstone 프로젝트에서의 역할
- `dstone-boot`: `common`/`sample` 데이터소스가 `sampleDB`를, `analyzer` 데이터소스가 `analyzeDB`를 바라봄 (`ConfigDatasource.java`).
- `dstone-batch`: `common` 데이터소스가 Spring Batch 메타데이터(`dataflow`)를, `sample` 데이터소스가 `sampleDB`를 바라봄. 메타데이터 테이블은 [6.2절](#62-실행-순서)의 스키마 SQL을 수동으로 생성해야 함 (`initialize-schema: NEVER`).
- `dstone-batchadmin`: `common` 데이터소스(`batchadmin` 스키마)로 로그인 사용자/배치서버 등록정보 관리, `RoutingDataSource`로 등록된 각 `dstone-batch` 서버의 `dataflow` DB에도 동적으로 접속해 Job 이력을 직접 조회한다.
- 접속 정보는 각 모듈 `conf/env.properties`의 `DB_HOST`/`DB_PORT`와 `application.yml`의 `ENC(...)` 암호화된 비밀번호(Jasypt)로 구성된다 — 실제 값/생성 절차는 [6절](#6-dstone-데이터베이스사용자스키마-초기화-설치-후-필수)/[7절](#7-비밀번호jasypt-enc-다루기) 참고.
