package com.campanas.salud.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final SeguridadInterceptor seguridad;

    public WebConfig(SeguridadInterceptor seguridad) { this.seguridad = seguridad; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(seguridad).addPathPatterns("/api/**");
    }
}
