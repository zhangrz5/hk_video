@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

set "APP_NAME=hk-video"
set "APP_JAR=hk-video-0.0.1-SNAPSHOT.jar"
set "APP_DIR=%~dp0"
set "APP_JAR_PATH=%APP_DIR%%APP_JAR%"
set "LOG_FILE=%APP_DIR%%APP_NAME%.log"
set "PID_FILE=%APP_DIR%%APP_NAME%.pid"

set "SERVER_PORT=8080"
set "FFMPEG_PATH=%APP_DIR%ffmpeg\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe"
set "JAVA_OPTS=-Xms256m -Xmx512m"

if "%~1"=="" goto usage
if /i "%~1"=="start" goto start
if /i "%~1"=="stop" goto stop
if /i "%~1"=="restart" goto restart
if /i "%~1"=="status" goto status
if /i "%~1"=="log" goto showlog
goto usage

:start
if exist "%PID_FILE%" (
    set /p OLD_PID=<"%PID_FILE%"
    tasklist /FI "PID eq !OLD_PID!" 2>nul | findstr /i "java" >nul
    if !errorlevel! equ 0 (
        echo [%APP_NAME%] Already running, PID: !OLD_PID!
        goto theend
    )
    del /f "%PID_FILE%" >nul 2>&1
)
if not exist "%APP_JAR_PATH%" (
    echo [%APP_NAME%] JAR not found: %APP_JAR_PATH%
    goto theend
)
echo [%APP_NAME%] Starting...
start /b "" javaw %JAVA_OPTS% -jar "%APP_JAR_PATH%" --server.port=%SERVER_PORT% --hikvision.ffmpeg.path="%FFMPEG_PATH%" > "%LOG_FILE%" 2>&1
timeout /t 3 /nobreak >nul
for /f "tokens=2" %%a in ('wmic process where "commandline like '%%%APP_JAR%%%' and name='javaw.exe'" get processid 2^>nul ^| findstr /r "[0-9]"') do (
    set "NEW_PID=%%a"
)
if not defined NEW_PID (
    for /f "tokens=2" %%a in ('wmic process where "commandline like '%%%APP_JAR%%%'" get processid 2^>nul ^| findstr /r "[0-9]"') do (
        set "NEW_PID=%%a"
    )
)
if defined NEW_PID (
    echo !NEW_PID!> "%PID_FILE%"
    echo [%APP_NAME%] Started, PID: !NEW_PID!
    echo [%APP_NAME%] Log: %LOG_FILE%
    echo [%APP_NAME%] URL: http://localhost:%SERVER_PORT%
) else (
    echo [%APP_NAME%] Starting, check log: %LOG_FILE%
)
goto theend

:stop
if not exist "%PID_FILE%" (
    echo [%APP_NAME%] Not running
    goto theend
)
set /p STOP_PID=<"%PID_FILE%"
echo [%APP_NAME%] Stopping PID: !STOP_PID! ...
taskkill /PID !STOP_PID! /F >nul 2>&1
del /f "%PID_FILE%" >nul 2>&1
echo [%APP_NAME%] Stopped
goto theend

:restart
call :stop
timeout /t 2 /nobreak >nul
call :start
goto theend

:status
if exist "%PID_FILE%" (
    set /p CHK_PID=<"%PID_FILE%"
    tasklist /FI "PID eq !CHK_PID!" 2>nul | findstr /i "java" >nul
    if !errorlevel! equ 0 (
        echo [%APP_NAME%] Running, PID: !CHK_PID!
    ) else (
        echo [%APP_NAME%] Not running (process exited)
        del /f "%PID_FILE%" >nul 2>&1
    )
) else (
    echo [%APP_NAME%] Not running
)
goto theend

:showlog
if exist "%LOG_FILE%" (
    echo [%APP_NAME%] Tailing log (Ctrl+C to exit):
    echo ----------------------------------------
    powershell -Command "Get-Content '%LOG_FILE%' -Wait -Tail 50"
) else (
    echo [%APP_NAME%] Log file not found
)
goto theend

:usage
echo.
echo  HK Video Service
echo  ==========================================
echo  Usage: %~nx0 {start^|stop^|restart^|status^|log}
echo.
echo  start   - Start service
echo  stop    - Stop service
echo  restart - Restart service
echo  status  - Check status
echo  log     - Tail log
echo.
goto theend

:theend
endlocal
