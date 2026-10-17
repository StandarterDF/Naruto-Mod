@echo off
rem ---------------------------------------------------------------------------
rem  Naruto-Mod - dev server
rem
rem  Starts a dedicated 1.20.1 Forge server with the mod loaded from sources
rem  (NeoGradle "runServer"). Handy for testing things that need two players,
rem  e.g. jutsu and (later) shadow clones.
rem
rem  The dev server world/configs land in .\run as well. Add the usual server
rem  arguments after the script name if needed:
rem      run-dev-server.bat --nogui
rem ---------------------------------------------------------------------------

setlocal
title Naruto-Mod dev server
cd /d "%~dp0"

rem --- locate a JDK 17 -------------------------------------------------------
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
	echo   Gradle 8.1.1 in this project does not run on Java 18+ and JAVA_HOME
	echo   currently points to a newer JDK. Install a JDK 17, or point JAVA_HOME
	echo   at one, then run this script again.
	echo.
	pause
	exit /b 1
)

set "JAVA_HOME=%JDK17%"
set "PATH=%JDK17%\bin;%PATH%"

echo Using JDK: %JDK17%
echo Starting the dev server from "%CD%" ...
echo.

call gradlew.bat runServer %*
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
	echo.
	echo Dev server exited with code %CODE%.
	pause
)

exit /b %CODE%

rem --- helpers ---------------------------------------------------------------
:is17
rem Returns 0 when %~1 looks like a JDK 17 (checks the "release" file, so no JVM
rem has to be spawned). The pattern is a regex on purpose: writing the literal
rem quote would unbalance the quoting and swallow the redirection.
if not exist "%~1\bin\java.exe" exit /b 1
findstr /r /c:"JAVA_VERSION=.17\." "%~1\release" >nul 2>&1
exit /b %ERRORLEVEL%
