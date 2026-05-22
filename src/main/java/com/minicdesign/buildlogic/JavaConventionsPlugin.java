package com.minicdesign.buildlogic;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
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
        project.getPlugins().apply(IdeaPlugin.class);

        // 2. Create the configuration extension
        JavaConventionsExtension extension = project.getExtensions().create(
            "javaConventions", JavaConventionsExtension.class, project
        );

        // 3. Configure repositories
        project.getRepositories().mavenCentral();

        // 4. Configure Java compilation options (UTF-8, warnings as errors, standard lints)
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Xlint:-dangling-doc-comments", "-Xlint:-processing", "-Werror"));
            compile.getOptions().setEncoding("UTF-8");
        });

        // 5. Configure testing with JUnit Jupiter & Library Catalog
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
        });

        // 6. Configure separate source set for integration tests (testIntegration)
        JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
        boolean testIntegrationAlreadyExists = javaExt.getSourceSets().findByName("testIntegration") != null;
        SourceSet testIntegration = javaExt.getSourceSets().maybeCreate("testIntegration");
        if (testIntegrationAlreadyExists) return;

        // Set compile and runtime classpaths for integration tests to include main classes and unit test configurations
        testIntegration.setCompileClasspath(
            project.getObjects().fileCollection().from(
                javaExt.getSourceSets().getByName("main").getOutput(),
                project.getConfigurations().getByName("testCompileClasspath")
            )
        );
        testIntegration.setRuntimeClasspath(
            project.getObjects().fileCollection().from(
                testIntegration.getOutput(),
                javaExt.getSourceSets().getByName("main").getOutput(),
                project.getConfigurations().getByName("testRuntimeClasspath")
            )
        );

        // Inherit dependencies from test configurations
        ConfigurationContainer configurations = project.getConfigurations();
        configurations.getByName("testIntegrationImplementation")
            .extendsFrom(configurations.getByName("testImplementation"));
        configurations.getByName("testIntegrationRuntimeOnly")
            .extendsFrom(configurations.getByName("testRuntimeOnly"));

        // Register integrationTest execution task
        project.getTasks().register("integrationTest", Test.class, task -> {
            task.setDescription("Runs integration tests.");
            task.setGroup("verification");
            task.setTestClassesDirs(testIntegration.getOutput().getClassesDirs());
            task.setClasspath(testIntegration.getRuntimeClasspath());
            task.useJUnitPlatform();
        });

        // 7. Configure Spotless formatting for Java
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(java -> {
            java.targetExclude("build/generated/**");
            java.googleJavaFormat("1.24.0");
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
            java.endWithNewline();
        });

        // 8. Configure IntelliJ IDEA.  Source root registration for generated code is handled
        // explicitly by ApiGenerationPlugin and SpringOtelLoggingPlugin, which know the exact
        // output directory structure for each generator type.
        project.getPlugins().withType(IdeaPlugin.class, ideaPlugin -> {
            IdeaModel ideaModel = project.getExtensions().getByType(IdeaModel.class);
            ideaModel.getModule().setDownloadJavadoc(true);
            ideaModel.getModule().setDownloadSources(true);
        });

        // 9. Post-evaluation setup (for user-customized extension values)
        project.afterEvaluate(p -> {
            // Configure library catalog dependencies
            VersionCatalogsExtension catalogs = project.getExtensions().findByType(VersionCatalogsExtension.class);
            if (catalogs != null) {
                VersionCatalog libs = catalogs.find("libs").orElse(null);
                if (libs != null && libs.findLibrary("junit-jupiter").isPresent()) {
                    project.getDependencies().add("testImplementation", libs.findLibrary("junit-jupiter").get());
                } else {
                    project.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2");
                }
                
                if (libs != null && libs.findLibrary("junit-platform-launcher").isPresent()) {
                    project.getDependencies().add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get());
                } else {
                    project.getDependencies().add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2");
                }
            } else {
                project.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2");
                project.getDependencies().add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2");
            }

            // Configure Lombok
            Object lombokDep = "org.projectlombok:lombok:1.18.46";
            if (catalogs != null) {
                VersionCatalog libs = catalogs.find("libs").orElse(null);
                if (libs != null && libs.findLibrary("lombok").isPresent()) {
                    lombokDep = libs.findLibrary("lombok").get();
                }
            }
            project.getDependencies().add("compileOnly", lombokDep);
            project.getDependencies().add("annotationProcessor", lombokDep);
            project.getDependencies().add("testCompileOnly", lombokDep);
            project.getDependencies().add("testAnnotationProcessor", lombokDep);

            // Configure Java Toolchain version
            javaExt.getToolchain().getLanguageVersion().set(
                JavaLanguageVersion.of(extension.getJavaVersion().get())
            );

            // Configure Jacoco Tool Version
            JacocoPluginExtension jacocoExt = project.getExtensions().getByType(JacocoPluginExtension.class);
            jacocoExt.setToolVersion("0.8.14");

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

                // Aggregate execution data from both unit tests and integration tests dynamically from jacoco directory
                report.getExecutionData().setFrom(
                    project.fileTree(project.getLayout().getBuildDirectory().dir("jacoco"))
                        .include("*.exec")
                );
            });

            // Configure Jacoco Verification
            project.getTasks().withType(JacocoCoverageVerification.class).configureEach(verify -> {
                verify.dependsOn(project.getTasks().withType(Test.class));

                verify.getClassDirectories().setFrom(project.provider(() ->
                    javaExt.getSourceSets().getByName("main").getOutput().getClassesDirs()
                        .filter(file -> file.exists())
                        .getAsFileTree()
                        .matching(pattern -> pattern.exclude(extension.getJacocoExclusionPatterns().get()))
                ));

                verify.getExecutionData().setFrom(
                    project.fileTree(project.getLayout().getBuildDirectory().dir("jacoco"))
                        .include("*.exec")
                );

                verify.getViolationRules().rule(rule -> {
                    rule.setElement("CLASS");
                    rule.limit(limit -> {
                        limit.setCounter("LINE");
                        limit.setValue("COVEREDRATIO");
                        limit.setMinimum(BigDecimal.valueOf(extension.getCoverageThreshold().get()));
                    });
                });
            });

            // Finalize unit tests and integration tests by generating report
            project.getTasks().named("test", Test.class).configure(test -> {
                test.finalizedBy(project.getTasks().withType(JacocoReport.class));
            });

            project.getTasks().named("integrationTest", Test.class).configure(it -> {
                it.finalizedBy(project.getTasks().withType(JacocoReport.class));
            });

            project.getTasks().named("check").configure(check -> {
                check.dependsOn(project.getTasks().named("integrationTest"));
                check.dependsOn(project.getTasks().withType(JacocoCoverageVerification.class));
            });
        });
    }
}
