
set /P PID=<application.pid
taskkill /f /pid %PID%
del application.pid
