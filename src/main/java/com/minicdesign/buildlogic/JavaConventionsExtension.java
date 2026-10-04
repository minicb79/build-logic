package com.minicdesign.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.Arrays;

public class JavaConventionsExtension {
    private final Property<Integer> javaVersion;
    private final ListProperty<String> jacocoExclusionPatterns;
    private final Property<Double> coverageThreshold;
    private final Property<Boolean> modular;

    public JavaConventionsExtension(Project project) {
        this.javaVersion = project.getObjects().property(Integer.class).convention(25);
        this.jacocoExclusionPatterns = project.getObjects().listProperty(String.class).convention(
            Arrays.asList(
                "**/model/*.*",
                "**/beans/*",
                "**/config/*",
                "**/api/**",
                "**/*Application*",
                "**/otel/**"
            )
        );
        this.coverageThreshold = project.getObjects().property(Double.class).convention(0.90);
        this.modular = project.getObjects().property(Boolean.class).convention(false);
    }

    public Property<Integer> getJavaVersion() {
        return javaVersion;
    }

    public ListProperty<String> getJacocoExclusionPatterns() {
        return jacocoExclusionPatterns;
    }

    public Property<Double> getCoverageThreshold() {
        return coverageThreshold;
    }

    public Property<Boolean> getModular() {
        return modular;
    }
}
