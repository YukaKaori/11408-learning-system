package com.yuka.learning.ai.config;

import com.yuka.learning.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infrastructure beans for the {@code ai} package. Kept deliberately small —
 * business wiring (prompt assembly, persistence) never lives here.
 */
@Configuration
public class AiConfig {

    /**
     * A single, connection-pooled {@link HttpClient} shared by every provider call.
     * {@link JdkClientHttpRequestFactory} exposes the response body as a live
     * {@code InputStream} instead of buffering it, which is what lets {@code
     * DeepSeekProvider} relay DeepSeek's SSE stream token-by-token instead of
     * waiting for the full response.
     */
    @Bean
    public RestClient aiRestClient(AppProperties properties) {
        Duration timeout = properties.ai() != null && properties.ai().deepseek() != null
                ? properties.ai().deepseek().timeout()
                : Duration.ofSeconds(60);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Backs every streaming chat/generation call. Virtual threads make holding
     * one open for the duration of a slow model response cheap — see {@code
     * spring.threads.virtual.enabled} in application.yml for the servlet side
     * of the same decision.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiStreamingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
