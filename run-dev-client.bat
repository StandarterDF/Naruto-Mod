@echo off
rem ---------------------------------------------------------------------------
rem  Naruto-Mod - dev client
rem
rem  Starts Minecraft 1.20.1 with Forge and this mod loaded straight from the
rem  sources (NeoGradle "runClient"). The dev world, configs and logs live in
rem  .\run.
rem
rem  This script picks a JDK 17 on its own: Gradle 8.1.1 (used by the wrapper)
rem  cannot run on newer JVMs, and JAVA_HOME on this machine points to JDK 25.
rem
rem  It also runs Gradle in --offline mode: every dependency, the Minecraft
rem  artifacts, the assets and the natives are already in the local caches, so
rem  the client starts with no network at all. If you have just added/changed a
rem  dependency and need to download it, run once with NARUTO_ONLINE=1:
rem
rem      set NARUTO_ONLINE=1
rem      run-dev-client.bat
rem
rem  Usage:
rem      run-dev-client.bat                 offline start
rem      run-dev-client.bat --debug-jvm     any extra arguments go to Gradle
rem      run-dev-client.bat --stacktrace
rem
rem  Handy debug flags for a single launch:
rem      set JAVA_TOOL_OPTIONS=-Dmixin.debug=true
rem      run-dev-client.bat
rem ---------------------------------------------------------------------------

setlocal
title Naruto-Mod dev client
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

rem --- offline by default -----------------------------------------------------
rem Gradle still contacts repositories for dynamic versions / missing metadata
rem even when everything is cached, and that is what made the dev launch fail
rem without internet. NARUTO_ONLINE=1 opts out (needed only after adding a
rem dependency).
set "OFFLINE=--offline"
if /i "%NARUTO_ONLINE%"=="1" set "OFFLINE="

echo Using JDK: %JDK17%
echo Starting the dev client from "%CD%" ...
echo.

rem --- preflight: a hung Gradle daemon keeps the cache lock ------------------
rem A Gradle daemon can hang inside a Windows file-lock wait and survive as a
rem process that neither Stop-Process nor an elevated taskkill can remove. It
rem keeps %USERPROFILE%\.gradle\caches\journal-1\journal-1.lock open, and every
rem later build then sits at "0% INITIALIZING" for a minute and dies with
rem "Timeout waiting to lock journal cache". Catch that here instead of letting
rem the launch appear to hang. Only a reboot releases such a lock.
call :cacheLocked
if errorlevel 1 goto :lockok
echo.
echo [ERROR] The Gradle cache is locked by a hung process.
echo.
echo   Lock file: %GRADLE_LOCK%
if defined JAVA_PIDS echo   Java PIDs: %JAVA_PIDS%
echo.
echo   Such a holder cannot be killed - taskkill /F fails even elevated.
echo   Reboot Windows, then run this script again.
echo.
pause
exit /b 2
:lockok
rem --- persistent Gradle daemon -----------------------------------------------
rem gradle.properties sets org.gradle.daemon=false, so every launch forks a
rem single-use daemon and stops it when the game exits. That fork/stop churn is
rem what leaves a zombie daemon behind on this machine (an unkillable Java
rem process holding %USERPROFILE%\.gradle\caches\journal-1\journal-1.lock, which
rem then makes every later launch sit at "0% INITIALIZING" and die). One daemon
rem started after boot and reused for every run has no such teardown step.
call gradlew.bat --daemon runClient %OFFLINE% %*
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
	echo.
	echo Dev client exited with code %CODE%.
	if defined OFFLINE (
		echo   Gradle was run in offline mode. If the error says a dependency is
		echo   missing from the cache, run once with:
		echo       set NARUTO_ONLINE=1
		echo   and this script again ^(needs internet^).
	)
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

:cacheLocked
rem Returns 0 when another Gradle process holds the journal cache lock, 1 when
rem it is free (or when the check itself could not run - never block a launch on
rem a broken helper). A healthy idle Gradle does not hold the lock, and the
rem check reads nothing: it only tries an exclusive open for an instant.
set "GRADLE_LOCK=%USERPROFILE%\.gradle\caches\journal-1\journal-1.lock"
set "JAVA_PIDS="
if not exist "%GRADLE_LOCK%" exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $s=[IO.File]::Open($env:GRADLE_LOCK,'Open','ReadWrite','None'); $s.Close(); exit 1 } catch { exit 0 }"
if errorlevel 1 exit /b 1
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "((Get-Process -Name java -ErrorAction SilentlyContinue).Id) -join ', '"`) do set "JAVA_PIDS=%%P"
exit /b 0
