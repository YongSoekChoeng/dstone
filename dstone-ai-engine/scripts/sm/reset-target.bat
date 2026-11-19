@echo off
rem Reset the sm-workflow target project (source + optional DB) to its baseline. Runs reset-target.sh in WSL
rem because the test MySQL and its client live in WSL.
rem usage: reset-target.bat <request-folder or source-path> [--db] [--apply] [--paths "src db"] [--db-name testApp]
rem Without --apply it only shows what would change. For --db set MYSQL_PWD first (set MYSQL_PWD=...).
for /f "usebackq delims=" %%p in (`wsl.exe wslpath -a "%~dp0reset-target.sh"`) do set SM_RESET_SH=%%p
wsl.exe -e env MYSQL_PWD=%MYSQL_PWD% bash "%SM_RESET_SH%" %*
exit /b %ERRORLEVEL%
