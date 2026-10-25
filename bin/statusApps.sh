#! /bin/sh

# dstone 앱들의 상태를 한 번에 봅니다.
# 각 모듈의 bin/application.pid 로 프로세스가 떠 있는지 확인한다.

BASE_DIR="/app/dstone"

# 1. dstone-knowledge (분석 데이터 플랫폼. dstone-ai-engine 과 dstone-boot 가 부른다)
"$BASE_DIR/dstone-knowledge/bin/statusApp.sh"

# 2. dstone-ai-engine (AI 엔진. dstone-knowledge 를 부르고, dstone-boot 가 부른다)
"$BASE_DIR/dstone-ai-engine/bin/statusApp.sh"

# 3. dstone-boot (웹 화면. 위 둘을 부른다)
"$BASE_DIR/dstone-boot/bin/statusApp.sh"

# 4. dstone-batch, 5. dstone-batchadmin (따로 띄울 때만 RUNNING)
"$BASE_DIR/dstone-batch/bin/statusApp.sh"
"$BASE_DIR/dstone-batchadmin/bin/statusApp.sh"
