package com.example.hkvideo.web;

import com.example.hkvideo.ffmpeg.FfmpegStreamService;
import com.example.hkvideo.hikvision.HikvisionVideoService;
import com.example.hkvideo.web.dto.PlaybackRequest;
import com.example.hkvideo.web.dto.PlaybackResponse;
import com.example.hkvideo.web.dto.PreviewRequest;
import com.example.hkvideo.web.dto.PreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * FFmpeg 实时视频流端点。
 * <p>
 * 浏览器请求 → 后端获取 RTSP 地址 → FFmpeg 转码 → HTTP-FLV 流式响应。
 */
@RestController
@RequestMapping("/api/hikvision")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final HikvisionVideoService videoService;
    private final FfmpegStreamService ffmpegService;

    public StreamController(HikvisionVideoService videoService, FfmpegStreamService ffmpegService) {
        this.videoService = videoService;
        this.ffmpegService = ffmpegService;
    }

    /**
     * 实时预览 - HTTP-FLV 流。
     * <p>
     * GET /api/hikvision/cameras/{cameraIndexCode}/live.flv
     */
    @GetMapping("/cameras/{cameraIndexCode}/live.flv")
    public ResponseEntity<StreamingResponseBody> liveFlv(
            @PathVariable String cameraIndexCode) {

        // 1. 获取 RTSP URL
        PreviewRequest req = new PreviewRequest("rtsp", null, null, null, null);
        PreviewResponse preview = videoService.previewUrl(cameraIndexCode, req);
        String rtspUrl = preview.url();
        log.info("实时预览 HTTP-FLV: camera={}, rtsp={}", cameraIndexCode, rtspUrl);

        // 2. 启动 FFmpeg 并返回流式响应
        String streamKey = "live-" + cameraIndexCode;
        return buildFlvResponse(rtspUrl, streamKey);
    }

    /**
     * 录像回放 - HTTP-FLV 流。
     * <p>
     * GET /api/hikvision/cameras/{cameraIndexCode}/playback.flv?begin=...&end=...
     */
    @GetMapping("/cameras/{cameraIndexCode}/playback.flv")
    public ResponseEntity<StreamingResponseBody> playbackFlv(
            @PathVariable String cameraIndexCode,
            @RequestParam String begin,
            @RequestParam String end) {

        // 修复 URL 参数中 + 号可能被解码为空格的问题（时区偏移 +08:00 → " 08:00"）
        String fixedBegin = ensureMillis(begin.replace(' ', '+'));
        String fixedEnd = ensureMillis(end.replace(' ', '+'));
        log.info("录像回放: camera={}, begin=[{}], end=[{}]", cameraIndexCode, fixedBegin, fixedEnd);

        // 1. 获取回放 RTSP URL
        PlaybackRequest req = new PlaybackRequest(fixedBegin, fixedEnd, "rtsp", null, null, null);
        PlaybackResponse playback = videoService.playbackUrl(cameraIndexCode, req);
        String rtspUrl = playback.url();
        log.info("录像回放 HTTP-FLV: camera={}, rtsp={}", cameraIndexCode, rtspUrl);

        // 2. 启动 FFmpeg 并返回流式响应
        String streamKey = "playback-" + cameraIndexCode;
        return buildFlvResponse(rtspUrl, streamKey);
    }

    private ResponseEntity<StreamingResponseBody> buildFlvResponse(String rtspUrl, String streamKey) {
        StreamingResponseBody body = outputStream -> {
            InputStream flvStream = null;
            try {
                flvStream = ffmpegService.startStream(rtspUrl, streamKey);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = flvStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } catch (Exception e) {
                log.warn("流传输异常: key={}, reason={}", streamKey, e.getMessage());
                // 向前端写入非 FLV 数据，触发 flvjs 的 ERROR 事件
                try {
                    outputStream.write(("ERROR: " + e.getMessage()).getBytes());
                    outputStream.flush();
                } catch (Exception ignored) {}
            } finally {
                ffmpegService.stopStream(streamKey);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "video/x-flv");
        // 不要手动设置 Transfer-Encoding，Tomcat 会自动处理 chunked 编码
        // 手动设置会导致重复头，Nginx 代理时会返回 502
        headers.set("Cache-Control", "no-cache, no-store");
        headers.set("Connection", "keep-alive");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("X-Accel-Buffering", "no");  // 告诉 Nginx 不要缓冲此响应

        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * 确保 ISO8601 时间字符串包含毫秒部分 .SSS。
     * 海康回放接口要求格式：yyyy-MM-dd'T'HH:mm:ss.SSSXXX
     * 例如：2026-05-08T07:38:00+08:00 → 2026-05-08T07:38:00.000+08:00
     */
    private static String ensureMillis(String isoTime) {
        if (isoTime == null) return null;
        // 已包含毫秒（秒后面有 .）
        if (isoTime.matches(".*T\\d{2}:\\d{2}:\\d{2}\\.\\d+.*")) {
            return isoTime;
        }
        // 在秒和时区偏移之间插入 .000
        return isoTime.replaceFirst("(T\\d{2}:\\d{2}:\\d{2})", "$1.000");
    }
}
