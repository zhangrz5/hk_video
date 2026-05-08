package com.example.hkvideo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "hikvision")
public class HikvisionProperties {

    @NotBlank
    private String host;

    private String artemisPath = "/artemis";

    private String appKey;

    private String appSecret;

    @NotBlank
    private String cameraSearchPath = "/api/resource/v2/camera/search";

    @NotBlank
    private String previewPath = "/api/video/v2/cameras/previewURLs";

    private String playbackPath = "/api/video/v2/cameras/playbackURLs";

    private String ptzPath = "/api/video/v1/ptzs/controlling";

    private String capturePath = "/api/video/v1/manualCapture";

    private String manualRecordStartPath = "/api/video/v1/manualRecord/start";

    private String manualRecordStopPath = "/api/video/v1/manualRecord/stop";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private boolean trustAllSsl = true;

    private boolean includeSubRegions = true;

    private List<String> cameraAuthCodes = new ArrayList<>(List.of("view"));

    @Valid
    private DefaultPreview defaultPreview = new DefaultPreview();

    @Valid
    private FfmpegConfig ffmpeg = new FfmpegConfig();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = trimTrailingSlash(host);
    }

    public String getArtemisPath() {
        return artemisPath;
    }

    public void setArtemisPath(String artemisPath) {
        this.artemisPath = normalizePath(artemisPath);
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCameraSearchPath() {
        return cameraSearchPath;
    }

    public void setCameraSearchPath(String cameraSearchPath) {
        this.cameraSearchPath = normalizePath(cameraSearchPath);
    }

    public String getPreviewPath() {
        return previewPath;
    }

    public void setPreviewPath(String previewPath) {
        this.previewPath = normalizePath(previewPath);
    }

    public String getPlaybackPath() {
        return playbackPath;
    }

    public void setPlaybackPath(String playbackPath) {
        this.playbackPath = normalizePath(playbackPath);
    }

    public String getPtzPath() {
        return ptzPath;
    }

    public void setPtzPath(String ptzPath) {
        this.ptzPath = normalizePath(ptzPath);
    }

    public String getCapturePath() {
        return capturePath;
    }

    public void setCapturePath(String capturePath) {
        this.capturePath = normalizePath(capturePath);
    }

    public String getManualRecordStartPath() {
        return manualRecordStartPath;
    }

    public void setManualRecordStartPath(String manualRecordStartPath) {
        this.manualRecordStartPath = normalizePath(manualRecordStartPath);
    }

    public String getManualRecordStopPath() {
        return manualRecordStopPath;
    }

    public void setManualRecordStopPath(String manualRecordStopPath) {
        this.manualRecordStopPath = normalizePath(manualRecordStopPath);
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isTrustAllSsl() {
        return trustAllSsl;
    }

    public void setTrustAllSsl(boolean trustAllSsl) {
        this.trustAllSsl = trustAllSsl;
    }

    public boolean isIncludeSubRegions() {
        return includeSubRegions;
    }

    public void setIncludeSubRegions(boolean includeSubRegions) {
        this.includeSubRegions = includeSubRegions;
    }

    public List<String> getCameraAuthCodes() {
        return cameraAuthCodes;
    }

    public void setCameraAuthCodes(List<String> cameraAuthCodes) {
        this.cameraAuthCodes = cameraAuthCodes == null ? new ArrayList<>() : cameraAuthCodes;
    }

    public DefaultPreview getDefaultPreview() {
        return defaultPreview;
    }

    public void setDefaultPreview(DefaultPreview defaultPreview) {
        this.defaultPreview = defaultPreview;
    }

    public FfmpegConfig getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(FfmpegConfig ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static class DefaultPreview {

        @NotBlank
        private String protocol = "hls";

        @PositiveOrZero
        private Integer streamType = 0;

        private Integer transmode = 1;

        private String expand;

        private String streamform;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public Integer getStreamType() {
            return streamType;
        }

        public void setStreamType(Integer streamType) {
            this.streamType = streamType;
        }

        public Integer getTransmode() {
            return transmode;
        }

        public void setTransmode(Integer transmode) {
            this.transmode = transmode;
        }

        public String getExpand() {
            return expand;
        }

        public void setExpand(String expand) {
            this.expand = expand;
        }

        public String getStreamform() {
            return streamform;
        }

        public void setStreamform(String streamform) {
            this.streamform = streamform;
        }
    }

    public static class FfmpegConfig {

        private String path = "ffmpeg";

        private int timeout = 300;

        private int maxStreams = 10;

        private String videoCodec = "copy";

        private String preset = "ultrafast";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }

        public int getMaxStreams() { return maxStreams; }
        public void setMaxStreams(int maxStreams) { this.maxStreams = maxStreams; }

        public String getVideoCodec() { return videoCodec; }
        public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }

        public String getPreset() { return preset; }
        public void setPreset(String preset) { this.preset = preset; }
    }
}
