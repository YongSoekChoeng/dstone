#! /bin/sh

FILE="application.pid"
if [ -e $FILE ]; then
	PID=`cat application.pid`
	echo ${PID}
	
	kill -15 ${PID}
	rm application.pid
fi