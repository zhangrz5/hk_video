package com.example.hkvideo.ffmpeg;

import com.example.hkvideo.config.HikvisionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegStreamService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegStreamService.class);

    private final HikvisionProperties.FfmpegConfig config;
    private final Map<String, Process> activeStreams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public FfmpegStreamService(HikvisionProperties properties) {
        this.config = properties.getFfmpeg();
        log.info("FFmpeg stream service initialized: path={}, videoCodec={}, maxStreams={}, timeout={}s",
                config.getPath(), config.getVideoCodec(), config.getMaxStreams(), config.getTimeout());
    }

    public InputStream startStream(String inputUrl, String streamKey) throws IOException {
        if (activeStreams.size() >= config.getMaxStreams()) {
            throw new IOException("stream limit reached: " + config.getMaxStreams());
        }

        stopStream(streamKey);

        List<String> cmd = buildCommand(inputUrl);
        log.info("Starting FFmpeg: key={}, cmd={}", streamKey, String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        activeStreams.put(streamKey, process);
        CompletableFuture.runAsync(() -> consumeStderr(process, streamKey));

        scheduler.schedule(() -> {
            log.warn("FFmpeg timeout, stopping stream: key={}", streamKey);
            stopStream(streamKey);
        }, config.getTimeout(), TimeUnit.SECONDS);

        process.onExit().thenAccept(p -> {
            log.info("FFmpeg exited: key={}, exitCode={}", streamKey, p.exitValue());
            activeStreams.remove(streamKey);
        });

        return process.getInputStream();
    }

    public void stopStream(String streamKey) {
        Process process = activeStreams.remove(streamKey);
        if (process != null && process.isAlive()) {
            log.info("Stopping FFmpeg: key={}", streamKey);
            process.destroyForcibly();
        }
    }

    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    private List<String> buildCommand(String inputUrl) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getPath());
        cmd.addAll(List.of("-hide_banner", "-loglevel", "warning", "-nostdin"));

        String lowerUrl = inputUrl == null ? "" : inputUrl.toLowerCase();
        if (lowerUrl.startsWith("rtsp://")) {
            cmd.addAll(List.of(
                    "-rtsp_transport", "tcp",
                    "-timeout", "15000000"
            ));
        }

        cmd.addAll(List.of(
                "-analyzeduration", "5000000",
                "-probesize", "5000000",
                "-i", inputUrl
        ));

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

        cmd.addAll(List.of(
                "-c:a", "aac",
                "-f", "flv",
                "pipe:1"
        ));

        return cmd;
    }

    private void consumeStderr(Process process, String streamKey) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    log.warn("FFmpeg[{}]: {}", streamKey, line);
                }
            }
        } catch (IOException ignored) {
        }
    }
}
