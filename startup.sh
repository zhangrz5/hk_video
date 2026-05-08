#!/bin/bash
# ============================================
#  海康视频监控服务 - Linux 启动脚本
# ============================================

APP_NAME="hk-video"
APP_JAR="hk-video-0.0.1-SNAPSHOT.jar"
APP_DIR=$(cd "$(dirname "$0")" && pwd)
APP_JAR_PATH="${APP_DIR}/${APP_JAR}"
LOG_FILE="${APP_DIR}/${APP_NAME}.log"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"

# ---- 配置项（按需修改） ----
SERVER_PORT=8080
FFMPEG_PATH="/usr/local/bin/ffmpeg"
JAVA_OPTS="-Xms256m -Xmx512m"
# ---------------------------

start() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo "[${APP_NAME}] 已在运行 (PID: ${PID})"
            return 0
        fi
        rm -f "$PID_FILE"
    fi

    if [ ! -f "$APP_JAR_PATH" ]; then
        echo "[${APP_NAME}] 未找到 ${APP_JAR_PATH}"
        exit 1
    fi

    echo "[${APP_NAME}] 启动中..."
    nohup java ${JAVA_OPTS} -jar "${APP_JAR_PATH}" \
        --server.port=${SERVER_PORT} \
        --hikvision.ffmpeg.path=${FFMPEG_PATH} \
        > "${LOG_FILE}" 2>&1 &

    echo $! > "$PID_FILE"
    sleep 2

    if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "[${APP_NAME}] 启动成功 (PID: $(cat "$PID_FILE"))"
        echo "[${APP_NAME}] 日志: ${LOG_FILE}"
        echo "[${APP_NAME}] 访问: http://localhost:${SERVER_PORT}"
    else
        echo "[${APP_NAME}] 启动失败，查看日志: ${LOG_FILE}"
        rm -f "$PID_FILE"
        exit 1
    fi
}

stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "[${APP_NAME}] 未在运行"
        return 0
    fi

    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "[${APP_NAME}] 停止中 (PID: ${PID})..."
        kill "$PID"
        for i in $(seq 1 15); do
            if ! kill -0 "$PID" 2>/dev/null; then
                break
            fi
            sleep 1
        done
        if kill -0 "$PID" 2>/dev/null; then
            echo "[${APP_NAME}] 强制终止..."
            kill -9 "$PID"
        fi
        echo "[${APP_NAME}] 已停止"
    else
        echo "[${APP_NAME}] 进程不存在"
    fi
    rm -f "$PID_FILE"
}

restart() {
    stop
    sleep 1
    start
}

status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo "[${APP_NAME}] 运行中 (PID: ${PID})"
            return 0
        fi
    fi
    echo "[${APP_NAME}] 未运行"
    return 1
}

log() {
    tail -f "${LOG_FILE}"
}

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    log)     log ;;
    *)
        echo "用法: $0 {start|stop|restart|status|log}"
        exit 1
        ;;
esac
