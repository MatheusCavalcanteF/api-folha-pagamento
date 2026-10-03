package com.payroll.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class WebConfig {

    private static final int MAX_PAGE_SIZE = 100;

    // Protege contra ?size=100000. Declarado aqui porque @EnableSpringDataWebSupport
    // desativa a autoconfiguração do Boot, que leria spring.data.web.pageable.max-page-size.
    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }
}
