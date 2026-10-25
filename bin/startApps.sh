#! /bin/sh

BASE_DIR="/app/dstone"

# 1. dstone-boot 실행
cd "$BASE_DIR/dstone-boot/bin" || exit
./startApp.sh

# 2. dstone-knowlege 실행
cd "$BASE_DIR/dstone-knowlege/bin" || exit
./startApp.sh

# 3. dstone-ai-engine 실행
cd "$BASE_DIR/dstone-ai-engine/bin" || exit
./startApp.sh

## 4. dstone-batch 실행
#cd "$BASE_DIR/dstone-batch/bin" || exit
#./startApp.sh
#
## 5. dstone-batchadmin 실행
#cd "$BASE_DIR/dstone-batchadmin/bin" || exit
#./startApp.sh
