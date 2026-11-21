#!/bin/bash
# History/ 아래 지난 달 대화이력을 월 단위 zip으로 묶는다.
#   History/20261015/*.md  →  History/202610.zip (안에는 20261015/xxx.md 그대로)
# 이번 달 폴더는 그대로 둔다. zip이 이미 있으면 빠진 파일만 더한다(다시 돌려도 안전).
# zip에 다 들어간 것을 확인한 폴더만 지운다.
# 사용법: archive-history.sh <History 폴더>

HISTORY_DIR="${1:-$(dirname "$(dirname "$(dirname "$0")")")/History}"
[ -d "$HISTORY_DIR" ] || exit 0

python3 - "$HISTORY_DIR" "$(date +%Y%m)" << 'PYEOF'
import os, re, sys, shutil, zipfile

history_dir = sys.argv[1]
this_month = sys.argv[2]

# 지난 달 일자 폴더를 월별로 모은다
months = {}
for name in sorted(os.listdir(history_dir)):
    path = os.path.join(history_dir, name)
    if os.path.isdir(path) and re.fullmatch(r'\d{8}', name) and name[:6] < this_month:
        months.setdefault(name[:6], []).append(name)

for month, days in sorted(months.items()):
    zip_path = os.path.join(history_dir, month + '.zip')
    with zipfile.ZipFile(zip_path, 'a', zipfile.ZIP_DEFLATED) as zf:
        have = set(zf.namelist())
        for day in days:
            day_path = os.path.join(history_dir, day)
            for fname in sorted(os.listdir(day_path)):
                fpath = os.path.join(day_path, fname)
                arc = day + '/' + fname
                if os.path.isfile(fpath) and arc not in have:
                    zf.write(fpath, arc)

    # zip을 다시 열어 확인한 뒤 폴더 삭제
    with zipfile.ZipFile(zip_path) as zf:
        if zf.testzip() is not None:
            print('[History] zip 손상, 폴더 유지: ' + zip_path, file=sys.stderr)
            continue
        have = set(zf.namelist())
    for day in days:
        day_path = os.path.join(history_dir, day)
        files = [f for f in os.listdir(day_path) if os.path.isfile(os.path.join(day_path, f))]
        if all((day + '/' + f) in have for f in files):
            shutil.rmtree(day_path)
    print('[History] 보관 완료: ' + zip_path, file=sys.stderr)
PYEOF
