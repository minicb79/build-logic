package com.minicdesign.buildlogic;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.math.BigDecimal;
import java.util.Arrays;

public class JavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // 1. Apply core plugins
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(JacocoPlugin.class);
        project.getPlugins().apply("com.diffplug.spotless");

        // 2. Create the configuration extension
        JavaConventionsExtension extension = project.getExtensions().create(
            "javaConventions", JavaConventionsExtension.class, project
        );

        // 3. Configure repositories
        project.getRepositories().mavenCentral();

        // 4. Configure Java compilation options (UTF-8, warnings as errors, standard lints)
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Werror"));
            compile.getOptions().setEncoding("UTF-8");
        });

        // 5. Configure testing with JUnit Jupiter
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
        });

        // 6. Configure Spotless formatting for Java
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(java -> {
            java.googleJavaFormat("1.22.0");
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
            java.endWithNewline();
        });

        // 7. Post-evaluation setup (for user-customized extension values)
        project.afterEvaluate(p -> {
            // Configure Java Toolchain version
            JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
            javaExt.getToolchain().getLanguageVersion().set(
                JavaLanguageVersion.of(extension.getJavaVersion().get())
            );

            // Configure Jacoco Tool Version
            JacocoPluginExtension jacocoExt = project.getExtensions().getByType(JacocoPluginExtension.class);
            jacocoExt.setToolVersion("0.8.12");

            // Configure Jacoco Reports
            project.getTasks().withType(JacocoReport.class).configureEach(report -> {
                report.getReports().getXml().getRequired().set(true);
                report.getReports().getHtml().getRequired().set(true);

                report.getClassDirectories().setFrom(project.provider(() ->
                    javaExt.getSourceSets().getByName("main").getOutput().getClassesDirs()
                        .filter(file -> file.exists())
                        .getAsFileTree()
                        .matching(pattern -> pattern.exclude(extension.getJacocoExclusionPatterns().get()))
                ));
            });

            // Configure Jacoco Verification
            project.getTasks().withType(JacocoCoverageVerification.class).configureEach(verify -> {
                verify.getClassDirectories().setFrom(project.provider(() ->
                    javaExt.getSourceSets().getByName("main").getOutput().getClassesDirs()
                        .filter(file -> file.exists())
                        .getAsFileTree()
                        .matching(pattern -> pattern.exclude(extension.getJacocoExclusionPatterns().get()))
                ));

                verify.getViolationRules().rule(rule -> {
                    rule.setElement("CLASS");
                    rule.limit(limit -> {
                        limit.setCounter("LINE");
                        limit.setValue("COVEREDRATIO");
                        limit.setMinimum(BigDecimal.valueOf(extension.getCoverageThreshold().get()));
                    });
                });
            });

            // Connect test task with jacoco report and verification
            project.getTasks().named("test", Test.class).configure(test -> {
                test.finalizedBy(project.getTasks().withType(JacocoReport.class));
            });

            project.getTasks().named("check").configure(check -> {
                check.dependsOn(project.getTasks().withType(JacocoCoverageVerification.class));
            });
        });
    }
}
