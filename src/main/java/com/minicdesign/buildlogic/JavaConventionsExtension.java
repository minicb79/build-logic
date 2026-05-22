package com.minicdesign.buildlogic;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.Arrays;

public class JavaConventionsExtension {
    private final Property<Integer> javaVersion;
    private final ListProperty<String> jacocoExclusionPatterns;
    private final Property<Double> coverageThreshold;

    public JavaConventionsExtension(Project project) {
        this.javaVersion = project.getObjects().property(Integer.class).convention(25);
        this.jacocoExclusionPatterns = project.getObjects().listProperty(String.class).convention(
            Arrays.asList(
                "**/model/*.*",
                "**/beans/*",
                "**/config/*",
                "**/api/**",
                "**/*Application*"
            )
        );
        this.coverageThreshold = project.getObjects().property(Double.class).convention(0.90);
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
}
