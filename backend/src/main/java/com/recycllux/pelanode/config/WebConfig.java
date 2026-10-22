package com.recycllux.pelanode.config;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Step B-15. Serves media from PN_MEDIA_ROOT, exports from PN_EXPORT_DIR, and the UI
 * bundle from classpath:/static with an index.html fallback so deep links such as
 * /events/EV-0143 work after a page reload. API paths never fall back to the UI.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PelanodeProperties props;

    public WebConfig(PelanodeProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(dirLocation(props.mediaRoot()));
        registry.addResourceHandler("/exports/**")
                .addResourceLocations(dirLocation(props.exportDir()));
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource requested = location.createRelative(path);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (path.startsWith("api/") || path.startsWith("actuator") || path.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (props.corsOrigins() != null && !props.corsOrigins().isBlank()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(props.corsOrigins().split(","))
                    .allowedMethods("GET", "POST")
                    .allowedHeaders("*");
        }
    }

    private static String dirLocation(String dir) {
        return Path.of(dir).toAbsolutePath().normalize().toUri().toString();
    }
}
