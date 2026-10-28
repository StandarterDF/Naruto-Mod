@echo off
rem ---------------------------------------------------------------------------
rem  Naruto-Mod - second dev client
rem
rem  Same as run-dev-client.bat but uses a separate game directory (run2) and
rem  the username Dev2, so two clients can connect to one dev server.
rem ---------------------------------------------------------------------------
setlocal
title Naruto-Mod dev client 2
cd /d "%~dp0"

set "JDK17="
if defined JAVA_HOME call :is17 "%JAVA_HOME%" && set "JDK17=%JAVA_HOME%"
if not defined JDK17 for /d %%D in ("C:\Program Files\Java\jdk-17*") do (
	if not defined JDK17 (
		call :is17 "%%~fD"
		if not errorlevel 1 set "JDK17=%%~fD"
	)
)
if not defined JDK17 for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
	if not defined JDK17 (
		call :is17 "%%~fD"
		if not errorlevel 1 set "JDK17=%%~fD"
	)
)
if not defined JDK17 for /d %%D in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17*") do (
	if not defined JDK17 (
		call :is17 "%%~fD"
		if not errorlevel 1 set "JDK17=%%~fD"
	)
)
if not defined JDK17 for /d %%D in ("C:\Program Files\Microsoft\jdk-17*") do (
	if not defined JDK17 (
		call :is17 "%%~fD"
		if not errorlevel 1 set "JDK17=%%~fD"
	)
)

if not defined JDK17 (
	echo.
	echo [ERROR] JDK 17 was not found.
	echo.
	pause
	exit /b 1
)

set "JAVA_HOME=%JDK17%"
set "PATH=%JDK17%\bin;%PATH%"

set "OFFLINE=--offline"
if /i "%NARUTO_ONLINE%"=="1" set "OFFLINE="

echo Using JDK: %JDK17%
echo Starting the second dev client (run2, user Dev2) ...
echo.

call gradlew.bat runClient2 %OFFLINE% %*
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
	echo.
	echo Dev client 2 exited with code %CODE%.
	if defined OFFLINE (
		echo   Gradle was run in offline mode.
	)
	pause
)

exit /b %CODE%

:is17
if not exist "%~1\bin\java.exe" exit /b 1
findstr /r /c:"JAVA_VERSION=.17\." "%~1\release" >nul 2>&1
exit /b %ERRORLEVEL%
