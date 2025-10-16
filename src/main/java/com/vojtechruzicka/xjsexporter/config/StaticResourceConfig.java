package com.vojtechruzicka.xjsexporter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    /**
     * Filesystem path where generated static site is located.
     * Can be overridden via application.properties: app.out.path=<absolute path>
     */
    @Value("${app.out.path:OUT}")
    private String outDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve absolute filesystem path for OUT directory
        Path outPath = StringUtils.hasText(outDir) ? Paths.get(outDir) : Paths.get("OUT");
        String location = outPath.toAbsolutePath().toUri().toString();

        // Serve any non-controller request from the OUT directory
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Make root URL show the OUT/index.html if present
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
