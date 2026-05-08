package com.example.hkvideo.web.dto;

/**
 * 录像回放取流请求。
 *
 * @param beginTime  开始时间，ISO8601 格式，如 2025-05-01T00:00:00+08:00
 * @param endTime    结束时间，ISO8601 格式
 * @param protocol   取流协议：hls / rtsp / rtmp / hik（默认 hls）
 * @param streamType 码流类型：0-主码流 1-子码流（默认 0）
 * @param transmode  传输协议：0-UDP 1-TCP（默认 1）
 * @param expand     扩展内容，格式 streamform=rtp
 */
public record PlaybackRequest(
        String beginTime,
        String endTime,
        String protocol,
        Integer streamType,
        Integer transmode,
        String expand
) {
}
