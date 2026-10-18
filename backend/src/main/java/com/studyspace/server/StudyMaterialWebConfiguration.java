package com.studyspace.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class StudyMaterialWebConfiguration implements WebMvcConfigurer {
    private final StudyMaterialStorage storage;
    StudyMaterialWebConfiguration(StudyMaterialStorage storage) { this.storage=storage; }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/materials/**")
            .addResourceLocations(storage.root.toUri().toString().replaceAll("/?$","/"));
    }
}
