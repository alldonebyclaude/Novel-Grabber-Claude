@echo off
rem Starts Novel-Grabber. Without arguments it opens the window, with arguments it runs
rem the command line version, e.g.  Novel-Grabber.bat -link https://host.com/novel/ -chapters 1 5
setlocal
set "APP_DIR=%~dp0"
set "REQUIRED_JAVA=25"

rem Use JAVA_HOME when it points to a Java installation, otherwise java from the PATH.
set "JAVA=java"
set "JAVAW=javaw"
if not defined JAVA_HOME goto check_java
if not exist "%JAVA_HOME%\bin\java.exe" goto check_java
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "JAVAW=%JAVA_HOME%\bin\javaw.exe"

:check_java
rem "java -version" prints e.g.: openjdk version "25.0.1" 2025-10-21  (Java 8: "1.8.0_451")
set "JAVA_VERSION="
for /f "tokens=3" %%v in ('call "%JAVA%" -version 2^>^&1 ^| findstr /i /c:"version"') do set "JAVA_VERSION=%%~v"
if not defined JAVA_VERSION goto no_java
for /f "delims=.-+_" %%m in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%m"
set /a "JAVA_MAJOR=JAVA_MAJOR" 2>nul
if %JAVA_MAJOR% LSS %REQUIRED_JAVA% goto old_java

if "%~1"=="" goto gui
"%JAVA%" -jar "%APP_DIR%Novel-Grabber.jar" %*
exit /b %ERRORLEVEL%

:gui
start "" "%JAVAW%" -jar "%APP_DIR%Novel-Grabber.jar"
exit /b 0

:no_java
echo Java was not found. Novel-Grabber needs Java %REQUIRED_JAVA% or newer.
echo Download it from https://adoptium.net/ and start Novel-Grabber again.
goto fail

:old_java
echo Novel-Grabber needs Java %REQUIRED_JAVA% or newer, but found Java %JAVA_VERSION%.
echo Download a newer Java from https://adoptium.net/ and start Novel-Grabber again.
goto fail

:fail
rem Keep the window open when the script was double-clicked.
if "%~1"=="" pause
exit /b 1
