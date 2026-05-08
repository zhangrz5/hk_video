package com.example.hkvideo.web.dto;

/**
 * 录像回放取流响应。
 */
public record PlaybackResponse(
        String cameraIndexCode,
        String url
) {
}
