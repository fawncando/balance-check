package com.bzd.balance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bzd.balance.auth.AuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOriginPatterns;

    public WebConfig(
            AuthInterceptor authInterceptor,
            @Value("${app.security.allowed-origin-patterns:"
                    + "http://localhost:5173,http://127.0.0.1:5173,"
                    + "http://10.*:5173,http://192.168.*:5173,http://172.*:5173}")
            String allowedOriginPatterns) {
        this.authInterceptor = authInterceptor;
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/system/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
