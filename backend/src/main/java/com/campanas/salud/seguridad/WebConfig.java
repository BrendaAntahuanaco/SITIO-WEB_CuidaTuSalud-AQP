package com.campanas.salud.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AutenticacionInterceptor interceptor;

    public WebConfig(AutenticacionInterceptor interceptor) { this.interceptor = interceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
