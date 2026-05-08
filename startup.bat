@echo off
chcp 65001 >nul
:: ============================================
::  海康视频监控服务 - Windows 启动脚本
:: ============================================

setlocal EnableDelayedExpansion

set APP_NAME=hk-video
set APP_JAR=hk-video-0.0.1-SNAPSHOT.jar
set APP_DIR=%~dp0
set APP_JAR_PATH=%APP_DIR%%APP_JAR%
set LOG_FILE=%APP_DIR%%APP_NAME%.log
set PID_FILE=%APP_DIR%%APP_NAME%.pid

:: ---- 配置项（按需修改） ----
set SERVER_PORT=8080
set FFMPEG_PATH=%APP_DIR%ffmpeg\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe
set JAVA_OPTS=-Xms256m -Xmx512m
:: ---------------------------

if "%~1"=="" goto usage
if /i "%~1"=="start" goto start
if /i "%~1"=="stop" goto stop
if /i "%~1"=="restart" goto restart
if /i "%~1"=="status" goto status
if /i "%~1"=="log" goto log
goto usage

:start
    :: 检查是否已在运行
    if exist "%PID_FILE%" (
        set /p OLD_PID=<"%PID_FILE%"
        tasklist /FI "PID eq !OLD_PID!" 2>nul | findstr /i "java" >nul
        if !errorlevel! equ 0 (
            echo [%APP_NAME%] 已在运行 ^(PID: !OLD_PID!^)
            goto end
        )
        del /f "%PID_FILE%" >nul 2>&1
    )

    :: 检查 jar 文件
    if not exist "%APP_JAR_PATH%" (
        echo [%APP_NAME%] 未找到 %APP_JAR_PATH%
        goto end
    )

    :: 检查 FFmpeg
    if not exist "%FFMPEG_PATH%" (
        echo [%APP_NAME%] 警告: 未找到 FFmpeg: %FFMPEG_PATH%
        echo [%APP_NAME%] 视频播放功能将不可用！
    )

    echo [%APP_NAME%] 启动中...

    start /b "" javaw %JAVA_OPTS% -jar "%APP_JAR_PATH%" ^
        --server.port=%SERVER_PORT% ^
        --hikvision.ffmpeg.path="%FFMPEG_PATH%" ^
        > "%LOG_FILE%" 2>&1

    :: 获取 PID
    timeout /t 3 /nobreak >nul
    for /f "tokens=2" %%a in ('wmic process where "commandline like '%%%APP_JAR%%%' and name='javaw.exe'" get processid 2^>nul ^| findstr /r "[0-9]"') do (
        set NEW_PID=%%a
    )
    if not defined NEW_PID (
        for /f "tokens=2" %%a in ('wmic process where "commandline like '%%%APP_JAR%%%'" get processid 2^>nul ^| findstr /r "[0-9]"') do (
            set NEW_PID=%%a
        )
    )

    if defined NEW_PID (
        echo !NEW_PID!> "%PID_FILE%"
        echo [%APP_NAME%] 启动成功 ^(PID: !NEW_PID!^)
        echo [%APP_NAME%] 日志: %LOG_FILE%
        echo [%APP_NAME%] 访问: http://localhost:%SERVER_PORT%
    ) else (
        echo [%APP_NAME%] 启动中，请稍后检查日志: %LOG_FILE%
    )
    goto end

:stop
    if not exist "%PID_FILE%" (
        echo [%APP_NAME%] 未在运行
        goto end
    )
    set /p STOP_PID=<"%PID_FILE%"
    echo [%APP_NAME%] 停止中 ^(PID: !STOP_PID!^)...
    taskkill /PID !STOP_PID! /F >nul 2>&1
    del /f "%PID_FILE%" >nul 2>&1
    echo [%APP_NAME%] 已停止
    goto end

:restart
    call :stop
    timeout /t 2 /nobreak >nul
    call :start
    goto end

:status
    if exist "%PID_FILE%" (
        set /p CHK_PID=<"%PID_FILE%"
        tasklist /FI "PID eq !CHK_PID!" 2>nul | findstr /i "java" >nul
        if !errorlevel! equ 0 (
            echo [%APP_NAME%] 运行中 ^(PID: !CHK_PID!^)
        ) else (
            echo [%APP_NAME%] 未运行 ^(进程已退出^)
            del /f "%PID_FILE%" >nul 2>&1
        )
    ) else (
        echo [%APP_NAME%] 未运行
    )
    goto end

:log
    if exist "%LOG_FILE%" (
        echo [%APP_NAME%] 日志输出（Ctrl+C 退出）:
        echo ----------------------------------------
        powershell -Command "Get-Content '%LOG_FILE%' -Wait -Tail 50"
    ) else (
        echo [%APP_NAME%] 日志文件不存在
    )
    goto end

:usage
    echo.
    echo  海康视频监控服务
    echo  ==========================================
    echo  用法: %~nx0 {start^|stop^|restart^|status^|log}
    echo.
    echo  start   - 启动服务
    echo  stop    - 停止服务
    echo  restart - 重启服务
    echo  status  - 查看状态
    echo  log     - 实时查看日志
    echo.
    goto end

:end
endlocal
