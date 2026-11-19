@echo off
rem sm-workflow compile check (Windows). The logic is in compile-check.ps1 (same folder).
rem usage: compile-check.bat <work-dir>
rem The engine runs this file through runShellScript (dstone.ai.tool.shell.allowed-commands).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0compile-check.ps1" %*
exit /b %ERRORLEVEL%
