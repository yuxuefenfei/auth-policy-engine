package com.example.authengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring MVC 全局消息转换器与内容协商拓展配置
 * <p>
 * 允许前端及各类调试工具 (Postman/Apifox/cURL) 使用 {@code text/plain} 媒体类型传输 JSON 负载，
 * 避免因未正确指定 {@code Content-Type: application/json} 请求头导致的 415/500 错误。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                List<MediaType> supported = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supported.add(MediaType.TEXT_PLAIN);
                supported.add(new MediaType("text", "plain", StandardCharsets.UTF_8));
                supported.add(MediaType.APPLICATION_OCTET_STREAM);
                jacksonConverter.setSupportedMediaTypes(supported);
            }
        }
    }
}
