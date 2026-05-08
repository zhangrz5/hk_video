package com.example.hkvideo.ffmpeg;

import com.example.hkvideo.config.HikvisionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg 进程管理服务。
 * <p>
 * 启动 FFmpeg 读取 RTSP 流，输出 FLV 到 stdout，
 * 由 Controller 通过 HTTP chunked response 推送给浏览器。
 */
@Service
public class FfmpegStreamService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegStreamService.class);

    private final HikvisionProperties.FfmpegConfig config;
    private final Map<String, Process> activeStreams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public FfmpegStreamService(HikvisionProperties properties) {
        this.config = properties.getFfmpeg();
        log.info("FFmpeg 流服务初始化: path={}, videoCodec={}, maxStreams={}, timeout={}s",
                config.getPath(), config.getVideoCodec(), config.getMaxStreams(), config.getTimeout());
    }

    /**
     * 启动 FFmpeg 进程，读取 RTSP 流并输出 FLV 到 stdout。
     *
     * @param rtspUrl   海康返回的 RTSP 取流地址
     * @param streamKey 唯一标识（用于后续停止）
     * @return FFmpeg 进程的 stdout InputStream（FLV 数据）
     */
    public InputStream startStream(String rtspUrl, String streamKey) throws IOException {
        if (activeStreams.size() >= config.getMaxStreams()) {
            throw new IOException("转码路数已达上限: " + config.getMaxStreams());
        }

        // 如果同 key 已有进程，先停止
        stopStream(streamKey);

        List<String> cmd = buildCommand(rtspUrl);
        log.info("启动 FFmpeg: key={}, cmd={}", streamKey, String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        activeStreams.put(streamKey, process);

        // 超时自动终止
        scheduler.schedule(() -> {
            log.warn("FFmpeg 超时终止: key={}", streamKey);
            stopStream(streamKey);
        }, config.getTimeout(), TimeUnit.SECONDS);

        // 异步消费 stderr（FFmpeg 日志/进度信息），防止缓冲区满导致阻塞
        CompletableFuture.runAsync(() -> consumeStderr(process, streamKey));

        // 监听进程退出
        process.onExit().thenAccept(p -> {
            log.info("FFmpeg 进程退出: key={}, exitCode={}", streamKey, p.exitValue());
            activeStreams.remove(streamKey);
        });

        return process.getInputStream();
    }

    /**
     * 停止指定的 FFmpeg 进程。
     */
    public void stopStream(String streamKey) {
        Process p = activeStreams.remove(streamKey);
        if (p != null && p.isAlive()) {
            log.info("终止 FFmpeg: key={}", streamKey);
            p.destroyForcibly();
        }
    }

    /**
     * 获取当前活跃的流数量。
     */
    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    private List<String> buildCommand(String rtspUrl) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getPath());

        // 输入参数
        cmd.addAll(List.of(
                "-rtsp_transport", "tcp",
                "-i", rtspUrl
        ));

        // 视频编解码
        String vcodec = config.getVideoCodec();
        if ("copy".equalsIgnoreCase(vcodec)) {
            cmd.addAll(List.of("-c:v", "copy"));
        } else {
            cmd.addAll(List.of(
                    "-c:v", vcodec,
                    "-preset", config.getPreset(),
                    "-tune", "zerolatency"
            ));
        }

        // 音频 + 输出格式
        cmd.addAll(List.of(
                "-c:a", "aac",
                "-f", "flv",
                "pipe:1"
        ));

        return cmd;
    }

    private void consumeStderr(Process process, String streamKey) {
        try (InputStream stderr = process.getErrorStream()) {
            byte[] buf = new byte[4096];
            while (stderr.read(buf) != -1) {
                // 丢弃 stderr 输出，仅防止缓冲区阻塞
                // 如需调试，可在此处打印日志
            }
        } catch (IOException ignored) {
        }
    }
}
