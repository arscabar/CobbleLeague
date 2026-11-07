@echo off
chcp 65001 >nul
title CobbleLeague Friends Installer
if not exist "%~dp0install-client.ps1" (
  echo Extract the entire ZIP first, then run this file.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-client.ps1" -Action Restore
set "INSTALL_RESULT=%errorlevel%"
echo.
pause
exit /b %INSTALL_RESULT%
