#! /bin/sh

# 어디서 부르든 이 스크립트가 있는 폴더(bin)에서 실행한다. application.pid 가 여기에 있다.
cd "$(dirname "$0")" || exit 1

FILE="application.pid"
if [ -e $FILE ]; then
	PID=`cat application.pid`
	echo ${PID}

	kill -15 ${PID}

	# 프로그램이 완전히 내려갈 때까지 기다린다(최대 30초). 바로 다시 띄울 때 포트가 아직 잡혀 있지 않게 하려는 것이다.
	COUNT=0
	while kill -0 ${PID} 2>/dev/null && [ ${COUNT} -lt 30 ]; do
		sleep 1
		COUNT=$((COUNT + 1))
	done

	rm -f application.pid
fi
