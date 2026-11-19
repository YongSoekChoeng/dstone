@echo off
rem Generic "overlay and compile" script (Windows). The logic is in overlay-compile.ps1 (same folder).
rem usage: overlay-compile.bat <source-dir> <overlay-dir> <work-dir> [delete-list-file] [build-command]
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0overlay-compile.ps1" %*
exit /b %ERRORLEVEL%
