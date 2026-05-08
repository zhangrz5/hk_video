package com.example.hkvideo.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 代理处理器。
 * <p>
 * 浏览器连接 ws://本机/ws/proxy?url=ws://海康地址，
 * 后端与海康流媒体建立 WebSocket 连接，将二进制数据双向透传。
 */
public class WsProxyHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WsProxyHandler.class);

    /** 浏览器 sessionId → 海康上游 Session */
    private final Map<String, WebSocketSession> upstreamSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        String targetUrl = extractTargetUrl(browserSession);
        if (targetUrl == null || targetUrl.isBlank()) {
            log.warn("WebSocket 代理缺少 url 参数，关闭连接");
            browserSession.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("WebSocket 代理连接: browser={} -> upstream={}", browserSession.getId(), targetUrl);

        try {
            StandardWebSocketClient client = new StandardWebSocketClient();

            // 上游处理器：把海康发来的数据转发给浏览器
            UpstreamHandler upstreamHandler = new UpstreamHandler(browserSession);

            WebSocketSession upstream = client.execute(upstreamHandler, null, new URI(targetUrl)).get();
            upstream.setBinaryMessageSizeLimit(1024 * 1024);
            upstreamSessions.put(browserSession.getId(), upstream);
            log.info("成功连接海康流媒体: {}", targetUrl);
        } catch (Exception e) {
            log.error("连接海康流媒体失败: {}", e.getMessage(), e);
            browserSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession browserSession, BinaryMessage message) throws Exception {
        // 浏览器 → 海康（一般无需求，保留透传能力）
        WebSocketSession upstream = upstreamSessions.get(browserSession.getId());
        if (upstream != null && upstream.isOpen()) {
            upstream.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) throws Exception {
        log.info("浏览器 WebSocket 关闭: session={}, status={}", browserSession.getId(), status);
        closeUpstream(browserSession.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) throws Exception {
        log.error("浏览器 WebSocket 传输错误: session={}", browserSession.getId(), exception);
        closeUpstream(browserSession.getId());
    }

    private void closeUpstream(String browserSessionId) {
        WebSocketSession upstream = upstreamSessions.remove(browserSessionId);
        if (upstream != null && upstream.isOpen()) {
            try {
                upstream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String extractTargetUrl(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "url".equals(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ===================== 上游（海康）处理器 =====================

    /**
     * 接收海康流媒体的二进制数据，转发给浏览器。
     */
    private static class UpstreamHandler extends BinaryWebSocketHandler {

        private static final Logger log = LoggerFactory.getLogger(UpstreamHandler.class);

        private final WebSocketSession browserSession;

        UpstreamHandler(WebSocketSession browserSession) {
            this.browserSession = browserSession;
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
            if (browserSession.isOpen()) {
                browserSession.sendMessage(message);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            log.info("海康上游 WebSocket 关闭: {}", status);
            if (browserSession.isOpen()) {
                browserSession.close(CloseStatus.NORMAL);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("海康上游 WebSocket 错误: {}", exception.getMessage());
            if (browserSession.isOpen()) {
                browserSession.close(CloseStatus.SERVER_ERROR);
            }
        }
    }
}
