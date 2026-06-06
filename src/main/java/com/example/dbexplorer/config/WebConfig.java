package com.example.dbexplorer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular SPA from classpath:/static and falls back to index.html for
 * client-side routes (e.g. /table/EMP, /admin/users) so deep links and refreshes work.
 *
 * Real static files (*.js, *.css, favicon, …) resolve to their actual resource;
 * anything else that isn't an API call returns index.html. REST controllers under
 * /api/** are matched before this resource handler, so the API is unaffected.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Resource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws java.io.IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Let unmatched API paths 404 normally; everything else is an SPA route.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return INDEX;
                    }
                });
    }
}
