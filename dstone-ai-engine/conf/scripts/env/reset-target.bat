@echo off
rem Reset a target project (source + optional DB) to its baseline. Runs reset-target.sh (same folder) in WSL,
rem because the test MySQL and its client live in WSL.
rem usage: reset-target.bat <source-dir> [--paths "src db"] [--db --db-name NAME [--schema PATH] [--seed PATH]] [--apply]
rem Without --apply it only shows what would change. For --db set MYSQL_USER and MYSQL_PWD first.
for /f "usebackq delims=" %%p in (`wsl.exe wslpath -a "%~dp0reset-target.sh"`) do set RESET_SH=%%p
wsl.exe -e env MYSQL_USER=%MYSQL_USER% MYSQL_PWD=%MYSQL_PWD% bash "%RESET_SH%" %*
exit /b %ERRORLEVEL%
