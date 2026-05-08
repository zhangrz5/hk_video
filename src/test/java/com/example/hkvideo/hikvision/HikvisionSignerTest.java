package com.example.hkvideo.hikvision;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用海康官方文档中的完整签名示例来验证签名算法的正确性。
 *
 * 文档示例：
 * - 请求地址：http://www.example.com/artemis/api/example?qa=a&qb=B
 * - Http Method: POST
 * - appKey：29666671
 * - appSecret：empsl21ds3
 * - X-Ca-Timestamp: 1479968678000
 * - 签名结果：JRpUpk1ETjzr5gsbo4qoEA9EiQPejvNz12B837xV5HI=
 */
class HikvisionSignerTest {

    // 验证文档中给出的签名字符串和 HmacSHA256+Base64 结果。
    // 文档中签名字符串由 HTTP METHOD, Accept, Content-Type, 自定义 Headers, Url 组成。
    // 预期签名：JRpUpk1ETjzr5gsbo4qoEA9EiQPejvNz12B837xV5HI=
    @Test
    void verifyOfficialDocumentSignatureExample() throws Exception {
        // 文档中的签名字符串（注意：文档示例中 Content-MD5 不存在于 headers 中，所以不加 \n）
        String stringToSign = "POST\n" +
                "*/*\n" +
                "text/plain;charset=UTF-8\n" +
                "header-a:A\n" +
                "header-b:b\n" +
                "x-ca-key:29666671\n" +
                "x-ca-timestamp:1479968678000\n" +
                "/artemis/api/example?a-body=a&qa=a&qb=B&x-body=x";

        String appSecret = "empsl21ds3";
        String expectedSignature = "JRpUpk1ETjzr5gsbo4qoEA9EiQPejvNz12B837xV5HI=";

        // 使用 HmacSHA256 + Base64 计算签名
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        byte[] keyBytes = appSecret.getBytes(StandardCharsets.UTF_8);
        hmacSha256.init(new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256"));
        String actualSignature = Base64.getEncoder().encodeToString(
                hmacSha256.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expectedSignature, actualSignature,
                "签名结果应与文档示例一致");
    }

    /**
     * 测试 HikvisionSigner 的基本签名流程（无 query 参数的简单 POST 场景）。
     */
    @Test
    void signedHeadersContainsRequiredKeys() {
        HikvisionSigner signer = new HikvisionSigner("29666671", "empsl21ds3");

        var headers = signer.signedHeaders("POST", "/artemis/api/resource/v2/camera/search",
                "{\"pageNo\":1,\"pageSize\":20}");

        // 验证必选系统级 header 存在
        assertHeaderExists(headers, "X-Ca-Key", "29666671");
        assertHeaderExists(headers, "X-Ca-Signature");
        assertHeaderExists(headers, "X-Ca-Signature-Headers");
        assertHeaderExists(headers, "X-Ca-Timestamp");
        assertHeaderExists(headers, "X-Ca-Nonce");

        // 验证 Content-MD5 存在（body 非空时应计算）
        assertHeaderExists(headers, "Content-MD5");

        // 验证 Accept 和 Content-Type
        assertHeaderExists(headers, "Accept", "*/*");
        assertHeaderExists(headers, "Content-Type", "application/json");

        System.out.println("=== 签名 Headers ===");
        headers.forEach((k, v) -> System.out.println(k + ": " + v));
    }

    /**
     * 测试 body 为空时不应生成 Content-MD5。
     */
    @Test
    void signedHeadersWithoutBodyShouldNotHaveContentMD5() {
        HikvisionSigner signer = new HikvisionSigner("testKey", "testSecret");

        var headers = signer.signedHeaders("GET", "/artemis/api/test", null);

        // body 为空时不应有 Content-MD5
        boolean hasContentMD5 = headers.entrySet().stream()
                .anyMatch(e -> e.getKey().equalsIgnoreCase("Content-MD5"));
        assertEquals(false, hasContentMD5,
                "body 为空时不应生成 Content-MD5 header");
    }

    private void assertHeaderExists(java.util.Map<String, String> headers, String key) {
        boolean found = headers.entrySet().stream()
                .anyMatch(e -> e.getKey().equalsIgnoreCase(key));
        assertEquals(true, found, "应包含 header: " + key);
    }

    private void assertHeaderExists(java.util.Map<String, String> headers, String key, String expectedValue) {
        String value = headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(key))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        assertEquals(expectedValue, value, "header " + key + " 的值应为 " + expectedValue);
    }
}
