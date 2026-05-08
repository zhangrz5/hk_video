package com.example.hkvideo.hikvision;

import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 海康开放平台 AK/SK 摘要认证签名工具。
 * <p>
 * 按照官方文档实现签名流程：
 * <ol>
 *   <li>组装签名字符串：HTTP METHOD + 特殊Header + 自定义Header + Url</li>
 *   <li>以 AppSecret 为密钥，使用 HmacSHA256 生成消息摘要</li>
 *   <li>对消息摘要进行 BASE64 编码得到签名</li>
 * </ol>
 */
public class HikvisionSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 不参与 Headers 签名计算的 header key（小写）。
     * 参考文档第3节"系统级header"中的说明。
     */
    private static final Set<String> EXCLUDED_FROM_HEADER_SIGN = Set.of(
            "x-ca-signature",
            "x-ca-signature-headers",
            "accept",
            "content-md5",
            "content-type",
            "date",
            "content-length",
            "server",
            "connection",
            "host",
            "transfer-encoding",
            "x-application-context",
            "content-encoding"
    );

    private final String appKey;
    private final String appSecret;

    public HikvisionSigner(String appKey, String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    /**
     * 生成带签名的请求 headers。
     *
     * @param method             HTTP 方法，如 "POST"
     * @param signedResourcePath 签名用的资源路径（path 部分，不含 host）
     * @param body               请求体（JSON 字符串），可为 null 或空
     * @return 包含签名信息的完整 headers
     */
    public Map<String, String> signedHeaders(String method, String signedResourcePath, String body) {
        if (isBlank(appKey) || isBlank(appSecret)) {
            throw new HikvisionException("海康 app-key/app-secret 未配置");
        }

        Map<String, String> headers = new LinkedHashMap<>();

        // 1. 设置 Accept（文档建议显示设置，避免 Http 客户端默认 */* 导致签名校验失败）
        headers.put(HttpHeaders.ACCEPT, "*/*");

        // 2. Content-MD5：当 Body 非 Form 表单时计算 MD5
        //    文档要求：将 Content-MD5 放入请求 headers 中
        if (!isBlank(body)) {
            headers.put("Content-MD5", md5Base64(body));
        }

        // 3. Content-Type
        headers.put(HttpHeaders.CONTENT_TYPE, "application/json");

        // 4. 系统级 header（必选）
        headers.put("X-Ca-Key", appKey);

        // 5. 系统级 header（可选，用于防重放）
        headers.put("X-Ca-Timestamp", String.valueOf(System.currentTimeMillis()));
        headers.put("X-Ca-Nonce", UUID.randomUUID().toString());

        // 6. 计算参与 headers 签名的 key 列表
        String signatureHeaderKeys = buildSignatureHeaderKeys(headers);
        headers.put("X-Ca-Signature-Headers", signatureHeaderKeys);

        // 7. 组装签名字符串并计算签名
        String stringToSign = buildStringToSign(method, signedResourcePath, headers, signatureHeaderKeys);
        headers.put("X-Ca-Signature", hmacSha256Base64(stringToSign));

        return headers;
    }

    /**
     * 组装签名字符串。
     * <p>
     * 格式（文档第1节）：
     * <pre>
     * HTTP METHOD\n
     * Accept\n
     * Content-MD5\n
     * Content-Type\n
     * Date\n
     * Headers
     * Url
     * </pre>
     * 注意：如果请求headers中不存在 Accept/Content-MD5/Content-Type/Date，
     * 则不需要添加换行符"\n"。如果存在且 value 为空白字符串，则只添加换行符"\n"。
     */
    private String buildStringToSign(String method, String signedResourcePath,
                                      Map<String, String> headers, String signatureHeaderKeys) {
        StringBuilder builder = new StringBuilder();

        // HTTP METHOD
        builder.append(method.toUpperCase(Locale.ROOT)).append('\n');

        // 四个特殊 Header：Accept, Content-MD5, Content-Type, Date
        // 按照文档规定的顺序添加
        appendSpecialHeader(builder, headers, HttpHeaders.ACCEPT);
        appendSpecialHeader(builder, headers, "Content-MD5");
        appendSpecialHeader(builder, headers, HttpHeaders.CONTENT_TYPE);
        appendSpecialHeader(builder, headers, HttpHeaders.DATE);

        // Headers 部分：参与签名的自定义 Header
        // 按照 key 字典排序，key 转小写，格式为 "key:value\n"
        String[] headerKeys = signatureHeaderKeys.split(",");
        TreeMap<String, String> sortedHeaders = new TreeMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String lowerKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (!EXCLUDED_FROM_HEADER_SIGN.contains(lowerKey)) {
                sortedHeaders.put(lowerKey, entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : sortedHeaders.entrySet()) {
            builder.append(entry.getKey()).append(':');
            if (!isBlank(entry.getValue())) {
                builder.append(entry.getValue().trim());
            }
            builder.append('\n');
        }

        // Url 部分（path + query + bodyForm）
        builder.append(signedResourcePath);

        return builder.toString();
    }

    /**
     * 处理四个特殊 Header 的签名字符串追加逻辑。
     * <p>
     * 文档规则：
     * - headers 中不存在该 header → 不添加换行符
     * - headers 中存在但 value 为空白字符串 → 只添加换行符"\n"
     * - headers 中存在且 value 非空 → 添加 value + "\n"
     */
    private static void appendSpecialHeader(StringBuilder builder, Map<String, String> headers, String headerName) {
        // 需要同时检查原始 key 和常见变体
        String value = findHeaderValue(headers, headerName);
        if (value == null) {
            // header 不存在，不添加 \n
            return;
        }
        if (value.isBlank()) {
            // header 存在但 value 为空白，只添加 \n
            builder.append('\n');
        } else {
            // header 存在且有值，添加 value + \n
            builder.append(value).append('\n');
        }
    }

    /**
     * 在 headers 中查找指定 headerName 的值（不区分大小写）。
     *
     * @return value 字符串，如果 header 不存在则返回 null
     */
    private static String findHeaderValue(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 构建参与 Headers 签名的 key 列表字符串。
     * <p>
     * 文档规则：
     * - 将参与签名的 Header 的 Key 转换为小写字母
     * - 按照字典排序
     * - 多个 key 之间使用英文逗号分割
     * <p>
     * 建议对 X-Ca 开头以及自定义 Header 计算签名。
     */
    private String buildSignatureHeaderKeys(Map<String, String> headers) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String lowerKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (!EXCLUDED_FROM_HEADER_SIGN.contains(lowerKey)) {
                sorted.put(lowerKey, entry.getValue());
            }
        }
        return String.join(",", sorted.keySet());
    }

    /**
     * 使用 HmacSHA256 + BASE64 计算签名。
     */
    private String hmacSha256Base64(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            byte[] secretBytes = appSecret.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, 0, secretBytes.length, HMAC_SHA256));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new HikvisionException("生成海康 OpenAPI 签名失败", ex);
        }
    }

    /**
     * 计算 Body 的 MD5 信息摘要，并进行 BASE64 编码。
     * <p>
     * 文档规则：使用 MD5 计算 body 的信息摘要，对信息摘要使用 BASE64 算法生成 Content-MD5 字符串。
     */
    private String md5Base64(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new HikvisionException("生成海康 OpenAPI Content-MD5 失败", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
