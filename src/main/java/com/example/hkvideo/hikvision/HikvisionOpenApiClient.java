package com.example.hkvideo.hikvision;

import com.example.hkvideo.config.HikvisionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.artemis.sdk.ArtemisHttpUtil;
import com.hikvision.artemis.sdk.config.ArtemisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 海康开放平台 API 客户端。
 * <p>
 * 使用海康官方 artemis-http-client SDK 进行接口调用，
 * SDK 内部已实现 AK/SK 签名认证逻辑。
 */
@Component
public class HikvisionOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(HikvisionOpenApiClient.class);

    private final HikvisionProperties properties;
    private final ObjectMapper objectMapper;

    public HikvisionOpenApiClient(HikvisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        initArtemisConfig();
    }

    /**
     * 初始化海康 ArtemisConfig 静态配置。
     */
    private void initArtemisConfig() {
        String host = properties.getHost();
        if (host != null) {
            host = host.replaceFirst("^https?://", "");
        }
        ArtemisConfig.host = host;
        ArtemisConfig.appKey = properties.getAppKey();
        ArtemisConfig.appSecret = properties.getAppSecret();

        log.info("海康 ArtemisConfig 初始化完成: host={}, appKey={}", ArtemisConfig.host, ArtemisConfig.appKey);
    }

    /**
     * 使用海康官方 SDK 发起 POST 请求。
     */
    public JsonNode post(String apiPath, Map<String, Object> body) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            String fullPath = properties.getArtemisPath() + normalizePath(apiPath);

            Map<String, String> path = new HashMap<>(2);
            String protocol = properties.getHost() != null && properties.getHost().startsWith("https")
                    ? "https://" : "http://";
            path.put(protocol, fullPath);

            log.debug("海康接口调用: URL={}{}{}, body={}", protocol, ArtemisConfig.host, fullPath, jsonBody);

            String result = ArtemisHttpUtil.doPostStringArtemis(path, jsonBody, null, null, "application/json", null);

            log.debug("海康接口响应: {}", result);

            if (result == null || result.isBlank()) {
                throw new HikvisionException("海康接口返回为空");
            }
            return objectMapper.readTree(result);
        } catch (HikvisionException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("调用海康接口异常", ex);
            throw new HikvisionException("调用海康接口失败: " + apiPath, ex);
        }
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
