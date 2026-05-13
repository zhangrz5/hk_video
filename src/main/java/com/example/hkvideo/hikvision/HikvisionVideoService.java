package com.example.hkvideo.hikvision;

import com.example.hkvideo.config.HikvisionProperties;
import com.example.hkvideo.web.dto.CameraPageResponse;
import com.example.hkvideo.web.dto.CameraView;
import com.example.hkvideo.web.dto.CaptureResponse;
import com.example.hkvideo.web.dto.PlaybackRequest;
import com.example.hkvideo.web.dto.PlaybackResponse;
import com.example.hkvideo.web.dto.PreviewRequest;
import com.example.hkvideo.web.dto.PreviewResponse;
import com.example.hkvideo.web.dto.PtzRequest;
import com.example.hkvideo.web.dto.RecordResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HikvisionVideoService {

    private static final Logger log = LoggerFactory.getLogger(HikvisionVideoService.class);

    private final HikvisionProperties properties;
    private final HikvisionOpenApiClient openApiClient;

    public HikvisionVideoService(HikvisionProperties properties, HikvisionOpenApiClient openApiClient) {
        this.properties = properties;
        this.openApiClient = openApiClient;
    }

    // ===================== 监控点搜索 =====================

    public CameraPageResponse searchCameras(int pageNo, int pageSize, String keyword, String regionIndexCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNo", pageNo);
        body.put("pageSize", pageSize);
        if (isV2CameraSearch()) {
            putIfText(body, "name", keyword);
            if (regionIndexCode != null && !regionIndexCode.isBlank()) {
                body.put("regionIndexCodes", List.of(regionIndexCode.trim()));
                body.put("isSubRegion", properties.isIncludeSubRegions());
            }
            if (!properties.getCameraAuthCodes().isEmpty()) {
                body.put("authCodes", properties.getCameraAuthCodes());
            }
        } else {
            putIfText(body, "cameraName", keyword);
            putIfText(body, "regionIndexCode", regionIndexCode);
        }

        JsonNode root = openApiClient.post(properties.getCameraSearchPath(), body);
        JsonNode data = extractData(root);
        long total = data.path("total").asLong(0);
        int responsePageNo = data.path("pageNo").asInt(pageNo);
        int responsePageSize = data.path("pageSize").asInt(pageSize);

        List<CameraView> cameras = new ArrayList<>();
        JsonNode list = data.path("list");
        if (list.isArray()) {
            for (JsonNode item : list) {
                cameras.add(toCameraView(item));
            }
        }
        return new CameraPageResponse(responsePageNo, responsePageSize, total, cameras);
    }

    // ===================== 预览取流 =====================

    public PreviewResponse previewUrl(String cameraIndexCode, PreviewRequest request) {
        PreviewOptions options = PreviewOptions.from(properties.getDefaultPreview(), request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);
        body.put("streamType", options.streamType());
        body.put("protocol", options.protocol());
        if (options.transmode() != null) {
            body.put("transmode", options.transmode());
        }
        putIfText(body, "expand", options.expand());
        putIfText(body, "streamform", options.streamform());

        JsonNode root = openApiClient.post(properties.getPreviewPath(), body);
        JsonNode data = extractData(root);
        String url = text(data, "url");
        if (url == null && data.isTextual()) {
            url = data.asText();
        }
        if (url == null || url.isBlank()) {
            throw new HikvisionException("海康预览接口未返回取流 URL");
        }
        return new PreviewResponse(cameraIndexCode, options.protocol(), url, null);
    }

    // ===================== 录像回放 =====================

    /**
     * 获取监控点回放取流 URL。
     */
    public PlaybackResponse playbackUrl(String cameraIndexCode, PlaybackRequest request) {
        if (request == null || request.beginTime() == null || request.endTime() == null) {
            throw new HikvisionException("回放开始时间和结束时间不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);
        body.put("beginTime", request.beginTime());
        body.put("endTime", request.endTime());
        body.put("protocol", firstNonBlank(request.protocol(), "hls"));
        body.put("streamType", request.streamType() != null ? request.streamType() : 0);
        body.put("transmode", request.transmode() != null ? request.transmode() : 1);
        putIfText(body, "expand", request.expand());

        log.debug("获取回放取流URL: camera={}, begin={}, end={}", cameraIndexCode, request.beginTime(), request.endTime());
        JsonNode root = openApiClient.post(properties.getPlaybackPath(), body);
        JsonNode data = extractData(root);
        String url = text(data, "url");
        if (url == null || url.isBlank()) {
            throw new HikvisionException("海康回放接口未返回取流 URL");
        }
        return new PlaybackResponse(cameraIndexCode, url);
    }

    // ===================== 云台控制 =====================

    /**
     * 云台操作。
     *
     * @param cameraIndexCode 监控点编号
     * @param request         云台控制参数（action, command, speed, presetIndex）
     */
    public void ptzControl(String cameraIndexCode, PtzRequest request) {
        if (request == null || request.command() == null) {
            throw new HikvisionException("云台控制命令不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);
        body.put("action", request.action() != null ? request.action() : 0);
        body.put("command", request.command().toUpperCase(Locale.ROOT));
        body.put("speed", request.speed() != null ? request.speed() : 4);
        if (request.presetIndex() != null) {
            body.put("presetIndex", request.presetIndex());
        }

        log.debug("云台控制: camera={}, action={}, command={}, speed={}",
                cameraIndexCode, request.action(), request.command(), request.speed());
        JsonNode root = openApiClient.post(properties.getPtzPath(), body);
        extractData(root); // 校验返回码
    }

    // ===================== 手动抓图 =====================

    /**
     * 手动抓图。
     *
     * @param cameraIndexCode 监控点编号
     * @return 抓图结果，包含图片 URL
     */
    public CaptureResponse manualCapture(String cameraIndexCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);

        log.debug("手动抓图: camera={}", cameraIndexCode);
        JsonNode root = openApiClient.post(properties.getCapturePath(), body);
        JsonNode data = extractData(root);
        String picUrl = text(data, "picUrl");
        if (picUrl == null || picUrl.isBlank()) {
            throw new HikvisionException("海康抓图接口未返回图片 URL");
        }
        return new CaptureResponse(picUrl);
    }

    // ===================== 手动录像 =====================

    /**
     * 开始手动录像。
     *
     * @param cameraIndexCode 监控点编号
     * @param type            录像存储类型：0-中心存储，1-设备存储（默认 0）
     * @return 包含 taskID 的录像响应
     */
    public RecordResponse startRecord(String cameraIndexCode, Integer type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);
        body.put("recordType", type != null ? type : 0);

        log.debug("开始手动录像: camera={}, type={}", cameraIndexCode, type);
        JsonNode root = openApiClient.post(properties.getManualRecordStartPath(), body);
        JsonNode data = extractData(root);
        String taskID = text(data, "taskID");
        return new RecordResponse(taskID);
    }

    /**
     * 停止手动录像。
     *
     * @param cameraIndexCode 监控点编号
     * @param taskID          开始录像时返回的任务 ID
     */
    public void stopRecord(String cameraIndexCode, String taskID) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cameraIndexCode", cameraIndexCode);
        putIfText(body, "taskID", taskID);

        log.debug("停止手动录像: camera={}, taskID={}", cameraIndexCode, taskID);
        JsonNode root = openApiClient.post(properties.getManualRecordStopPath(), body);
        extractData(root); // 校验返回码
    }
    // ===================== 图片代理下载 =====================

    /**
     * 代理下载海康平台图片，解决前端跨域无法直接下载的问题。
     *
     * @param url 海康返回的图片 URL
     * @return 图片二进制数据
     */
    public ResponseEntity<byte[]> proxyImage(String url) {
        if (url == null || url.isBlank()) {
            throw new HikvisionException("图片 URL 不能为空");
        }
        log.debug("代理下载图片: {}", url);
        try {
            // 海康平台使用自签名证书，跳过 SSL 验证
            javax.net.ssl.TrustManager[] trustAll = {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String t) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String t) {}
                    }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());

            java.net.URL imgUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) imgUrl.openConnection();
            if (conn instanceof javax.net.ssl.HttpsURLConnection httpsConn) {
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new HikvisionException("下载图片失败，HTTP 状态码: " + code);
            }

            byte[] data = conn.getInputStream().readAllBytes();
            String contentType = conn.getContentType();
            conn.disconnect();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    contentType != null && !contentType.isBlank() ? contentType : "image/jpeg"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"capture.jpg\"");
            headers.setContentLength(data.length);

            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (HikvisionException e) {
            throw e;
        } catch (Exception e) {
            log.error("代理下载图片异常: {}", e.getMessage(), e);
            throw new HikvisionException("代理下载图片失败: " + e.getMessage());
        }
    }

    // ===================== 调试 =====================

    public HikvisionProperties getProperties() {
        return properties;
    }

    /**
     * 透传调用海康 OpenAPI，返回原始 JSON 响应。
     */
    public JsonNode debugInvoke(String path, Map<String, Object> body) {
        return openApiClient.post(path, body);
    }

    // ===================== 内部方法 =====================

    private JsonNode extractData(JsonNode root) {
        if (root == null) {
            throw new HikvisionException("海康接口返回为空");
        }
        String code = text(root, "code");
        if (code != null && !"0".equals(code)) {
            throw new HikvisionException(code, defaultString(text(root, "msg"), "海康接口返回失败"));
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return root;
        }
        return data;
    }

    private CameraView toCameraView(JsonNode item) {
        String status = text(item, "status");
        String explicitStatusName = text(item, "statusName");
        return new CameraView(
                firstText(item, "cameraIndexCode", "indexCode", "resourceIndexCode"),
                firstText(item, "cameraName", "name", "resourceName"),
                text(item, "regionIndexCode"),
                firstText(item, "regionName", "regionPathName"),
                firstText(item, "deviceIndexCode", "encodeDevIndexCode", "parentIndexCode"),
                firstText(item, "channelNo", "chanNum"),
                status,
                explicitStatusName == null ? statusName(status) : explicitStatusName
        );
    }

    private boolean isV2CameraSearch() {
        return properties.getCameraSearchPath().contains("/v2/");
    }

    private static String statusName(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "1", "online", "true" -> "在线";
            case "0", "offline", "false" -> "离线";
            default -> status;
        };
    }

    private static void putIfText(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value.trim());
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }

    private record PreviewOptions(
            String protocol,
            Integer streamType,
            Integer transmode,
            String expand,
            String streamform
    ) {
        static PreviewOptions from(HikvisionProperties.DefaultPreview defaults, PreviewRequest request) {
            return new PreviewOptions(
                    firstNonBlank(request == null ? null : request.protocol(), defaults.getProtocol(), "hls"),
                    firstNonNull(request == null ? null : request.streamType(), defaults.getStreamType(), 0),
                    firstNonNull(request == null ? null : request.transmode(), defaults.getTransmode(), 1),
                    firstNonBlank(request == null ? null : request.expand(), defaults.getExpand(), null),
                    firstNonBlank(request == null ? null : request.streamform(), defaults.getStreamform(), null)
            );
        }

        private static String firstNonBlank(String first, String second, String fallback) {
            if (first != null && !first.isBlank()) {
                return first.trim();
            }
            if (second != null && !second.isBlank()) {
                return second.trim();
            }
            return fallback;
        }

        @SafeVarargs
        private static <T> T firstNonNull(T... values) {
            for (T value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }
}
