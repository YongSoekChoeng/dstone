#! /bin/sh

# 어디서 부르든 이 스크립트가 있는 폴더(bin)에서 실행한다. application.pid 가 여기에 있다.
cd "$(dirname "$0")" || exit 1

FILE="application.pid"
if [ ! -e ${FILE} ]; then
	echo "dstone-ai-engine: PID 파일이 없습니다. 실행 중이 아닌 것으로 보입니다."
	exit 0
fi

PID=`cat ${FILE}`
if ! kill -0 ${PID} 2>/dev/null; then
	echo "dstone-ai-engine: PID ${PID} 프로세스는 이미 끝났습니다."
	rm -f ${FILE}
	exit 0
fi

echo "dstone-ai-engine: 종료합니다. (PID: ${PID})"
kill -15 ${PID}

# 프로그램이 완전히 내려갈 때까지 기다린다(최대 30초). 바로 다시 띄울 때 포트가 아직 잡혀 있지 않게 하려는 것이다.
COUNT=0
while kill -0 ${PID} 2>/dev/null && [ ${COUNT} -lt 30 ]; do
	sleep 1
	COUNT=$((COUNT + 1))
done

# 그래도 안 내려갔으면 PID 파일을 남겨 둔다. 지우면 떠 있는 프로세스를 이 스크립트로 다시 찾을 수 없다.
if kill -0 ${PID} 2>/dev/null; then
	echo "dstone-ai-engine: 30초 안에 내려가지 않았습니다. 확인 후 kill -9 ${PID} 로 내려 주세요."
	exit 1
fi

rm -f ${FILE}
echo "dstone-ai-engine: 종료했습니다."
