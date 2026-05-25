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

    @GetMapping("/cameras/{cameraIndexCode}/live.flv")
    public ResponseEntity<StreamingResponseBody> liveFlv(@PathVariable String cameraIndexCode) {
        PreviewRequest req = new PreviewRequest("rtsp", null, null, null, null);
        PreviewResponse preview = videoService.previewUrl(cameraIndexCode, req);
        String streamUrl = preview.url();
        log.info("Live HTTP-FLV: camera={}, source={}", cameraIndexCode, streamUrl);

        return buildFlvResponse(streamUrl, "live-" + cameraIndexCode);
    }

    @GetMapping("/cameras/{cameraIndexCode}/playback.flv")
    public ResponseEntity<StreamingResponseBody> playbackFlv(
            @PathVariable String cameraIndexCode,
            @RequestParam String begin,
            @RequestParam String end) {

        String fixedBegin = ensureMillis(begin.replace(' ', '+'));
        String fixedEnd = ensureMillis(end.replace(' ', '+'));
        log.info("Playback HTTP-FLV: camera={}, begin={}, end={}", cameraIndexCode, fixedBegin, fixedEnd);

        PlaybackRequest req = new PlaybackRequest(fixedBegin, fixedEnd, "rtsp", null, null, null);
        PlaybackResponse playback = videoService.playbackUrl(cameraIndexCode, req);

        return buildFlvResponse(playback.url(), "playback-" + cameraIndexCode);
    }

    private ResponseEntity<StreamingResponseBody> buildFlvResponse(String sourceUrl, String streamKey) {
        StreamingResponseBody body = outputStream -> {
            try (InputStream flvStream = ffmpegService.startStream(sourceUrl, streamKey)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = flvStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } catch (Exception e) {
                log.warn("FLV stream ended: key={}, reason={}", streamKey, e.getMessage());
            } finally {
                ffmpegService.stopStream(streamKey);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "video/x-flv");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("X-Accel-Buffering", "no");

        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String ensureMillis(String isoTime) {
        if (isoTime == null) {
            return null;
        }
        if (isoTime.matches(".*T\\d{2}:\\d{2}:\\d{2}\\.\\d+.*")) {
            return isoTime;
        }
        return isoTime.replaceFirst("(T\\d{2}:\\d{2}:\\d{2})", "$1.000");
    }
}
