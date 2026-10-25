#! /bin/sh

BASE_DIR="/app/dstone"

# 1. dstone-boot 실행
cd "$BASE_DIR/dstone-boot/bin" || exit
./stopApp.sh

# 2. dstone-knowlege 실행
cd "$BASE_DIR/dstone-knowledge/bin" || exit
./stopApp.sh

# 3. dstone-ai-engine 실행
cd "$BASE_DIR/dstone-ai-engine/bin" || exit
./stopApp.sh

## 4. dstone-batch 실행
#cd "$BASE_DIR/dstone-batch/bin" || exit
#./stopApp.sh
#
## 5. dstone-batchadmin 실행
#cd "$BASE_DIR/dstone-batchadmin/bin" || exit
#./stopApp.sh
