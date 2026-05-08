package com.example.hkvideo.web.dto;

/**
 * 云台控制请求。
 *
 * @param action  动作：0-开始，1-停止
 * @param command 控制命令：LEFT / RIGHT / UP / DOWN / ZOOM_IN / ZOOM_OUT /
 *                LEFT_UP / LEFT_DOWN / RIGHT_UP / RIGHT_DOWN /
 *                FOCUS_NEAR / FOCUS_FAR / IRIS_ENLARGE / IRIS_REDUCE /
 *                GOTO_PRESET（到预置点）
 * @param speed   速度，1~7（默认 4）
 * @param presetIndex 预置点编号，当 command=GOTO_PRESET 时需要
 */
public record PtzRequest(
        Integer action,
        String command,
        Integer speed,
        Integer presetIndex
) {
}
