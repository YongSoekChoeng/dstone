#! /bin/sh

# dstone 앱들을 한 번에 띄웁니다.
# 부르는 쪽보다 불리는 쪽을 먼저 띄운다: dstone-knowledge → dstone-ai-engine → dstone-boot
# 각 모듈 스크립트가 알아서 자기 bin 폴더로 옮겨 가므로 어디서 실행해도 된다. 하나가 실패해도 나머지는 계속한다.

BASE_DIR="/app/dstone"

# 1. dstone-knowledge (분석 데이터 플랫폼. dstone-ai-engine 과 dstone-boot 가 부른다)
"$BASE_DIR/dstone-knowledge/bin/startApp.sh"

# 2. dstone-ai-engine (AI 엔진. dstone-knowledge 를 부르고, dstone-boot 가 부른다)
"$BASE_DIR/dstone-ai-engine/bin/startApp.sh"

# 3. dstone-boot (웹 화면. 위 둘을 부른다)
"$BASE_DIR/dstone-boot/bin/startApp.sh"

# dstone-batch, dstone-batchadmin 은 필요할 때만 따로 띄운다.
# "$BASE_DIR/dstone-batch/bin/startApp.sh"
# "$BASE_DIR/dstone-batchadmin/bin/startApp.sh"
