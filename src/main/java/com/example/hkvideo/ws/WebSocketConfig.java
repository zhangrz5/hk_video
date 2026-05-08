package com.example.hkvideo.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置。
 * <p>
 * 注册 /ws/proxy 端点用于代理海康视频流。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsProxyHandler(), "/ws/proxy")
                .setAllowedOrigins("*");
    }

    @Bean
    public WsProxyHandler wsProxyHandler() {
        return new WsProxyHandler();
    }

    /**
     * 配置 WebSocket 容器参数。
     * 视频流数据量大，需要增大缓冲区。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024);   // 1MB
        container.setMaxTextMessageBufferSize(64 * 1024);        // 64KB
        container.setMaxSessionIdleTimeout(300000L);             // 5 分钟
        return container;
    }
}
