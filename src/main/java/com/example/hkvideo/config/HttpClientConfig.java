package com.example.hkvideo.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;

@Configuration
public class HttpClientConfig {

    @Bean
    RestClient restClient(HikvisionProperties properties) throws Exception {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient(properties));
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setConnectionRequestTimeout(properties.getConnectTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private CloseableHttpClient httpClient(HikvisionProperties properties) throws Exception {
        if (!properties.isTrustAllSsl()) {
            return HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig(properties))
                    .evictExpiredConnections()
                    .build();
        }
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                NoopHostnameVerifier.INSTANCE
        );
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build())
                .setDefaultRequestConfig(requestConfig(properties))
                .evictExpiredConnections()
                .build();
    }

    private RequestConfig requestConfig(HikvisionProperties properties) {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeout().toMillis()))
                .build();
    }
}
