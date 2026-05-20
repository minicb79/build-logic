package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension

class SpringOtelLoggingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply base OpenTelemetry dependencies
        project.plugins.apply("com.minicdesign.otel-base")

        // 2. Add Spring-specific tracing and instrumentation dependencies
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
        val libs = catalogs?.find("libs")?.orElse(null)

        val actuatorDep = if (libs != null && libs.findLibrary("spring-boot-starter-actuator").isPresent) {
            libs.findLibrary("spring-boot-starter-actuator").get()
        } else {
            "org.springframework.boot:spring-boot-starter-actuator"
        }

        val bootOtelDep = if (libs != null && libs.findLibrary("spring-boot-starter-opentelemetry").isPresent) {
            libs.findLibrary("spring-boot-starter-opentelemetry").get()
        } else {
            "org.springframework.boot:spring-boot-starter-opentelemetry"
        }

        val micrometerRegistryOtlp = if (libs != null && libs.findLibrary("micrometer-registry-otlp").isPresent) {
            libs.findLibrary("micrometer-registry-otlp").get()
        } else {
            "io.micrometer:micrometer-registry-otlp"
        }

        val micrometerTracingBridgeOtel = if (libs != null && libs.findLibrary("micrometer-tracing-bridge-otel").isPresent) {
            libs.findLibrary("micrometer-tracing-bridge-otel").get()
        } else {
            "io.micrometer:micrometer-tracing-bridge-otel"
        }

        project.dependencies.apply {
            add("implementation", actuatorDep)
            add("implementation", bootOtelDep)
            add("implementation", micrometerRegistryOtlp)
            add("implementation", micrometerTracingBridgeOtel)
        }

        // 3. Register task to generate Java configurations
        val generateOtelConfigTask = project.tasks.register("generateOtelConfig") {
            val outputDir = project.layout.buildDirectory.dir("generated/sources/otel/java")
            outputs.dir(outputDir)
            doLast {
                val comMinicdesignOtelDir = outputDir.get().asFile.resolve("com/minicdesign/otel")
                comMinicdesignOtelDir.mkdirs()

                // InstallOpenTelemetryAppender.java
                comMinicdesignOtelDir.resolve("InstallOpenTelemetryAppender.java").writeText("""
                    package com.minicdesign.otel;

                    import io.opentelemetry.api.OpenTelemetry;
                    import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
                    import org.springframework.beans.factory.InitializingBean;
                    import org.springframework.stereotype.Component;

                    @Component
                    public class InstallOpenTelemetryAppender implements InitializingBean {

                        private final OpenTelemetry openTelemetry;

                        public InstallOpenTelemetryAppender(OpenTelemetry openTelemetry) {
                            this.openTelemetry = openTelemetry;
                        }

                        @Override
                        public void afterPropertiesSet() {
                            OpenTelemetryAppender.install(this.openTelemetry);
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))

                // TraceIdFilter.java
                comMinicdesignOtelDir.resolve("TraceIdFilter.java").writeText("""
                    package com.minicdesign.otel;

                    import io.micrometer.tracing.TraceContext;
                    import io.micrometer.tracing.Tracer;
                    import jakarta.servlet.FilterChain;
                    import jakarta.servlet.ServletException;
                    import jakarta.servlet.http.HttpServletRequest;
                    import jakarta.servlet.http.HttpServletResponse;
                    import org.springframework.lang.Nullable;
                    import org.springframework.stereotype.Component;
                    import org.springframework.web.filter.OncePerRequestFilter;
                    import java.io.IOException;

                    @Component
                    public class TraceIdFilter extends OncePerRequestFilter {

                        private final Tracer tracer;

                        public TraceIdFilter(Tracer tracer) {
                            this.tracer = tracer;
                        }

                        @Override
                        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                                throws ServletException, IOException {
                            String traceId = getTraceId();
                            if (traceId != null) {
                                response.setHeader("X-Trace-Id", traceId);
                            }
                            filterChain.doFilter(request, response);
                        }

                        @Nullable
                        private String getTraceId() {
                            if (this.tracer.currentTraceContext() == null) {
                                return null;
                            }
                            TraceContext context = this.tracer.currentTraceContext().context();
                            return context != null ? context.traceId() : null;
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))

                // ContextPropagationConfiguration.java
                comMinicdesignOtelDir.resolve("ContextPropagationConfiguration.java").writeText("""
                    package com.minicdesign.otel;

                    import org.springframework.context.annotation.Bean;
                    import org.springframework.context.annotation.Configuration;
                    import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

                    @Configuration(proxyBeanMethods = false)
                    public class ContextPropagationConfiguration {

                        @Bean
                        public ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
                            return new ContextPropagatingTaskDecorator();
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))

                // OpenTelemetryConfiguration.java
                comMinicdesignOtelDir.resolve("OpenTelemetryConfiguration.java").writeText("""
                    package com.minicdesign.otel;

                    import org.springframework.context.annotation.Bean;
                    import org.springframework.context.annotation.Configuration;
                    import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
                    import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
                    import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
                    import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
                    import io.micrometer.core.instrument.binder.jvm.OpenTelemetryJvmCpuMeterConventions;
                    import io.micrometer.core.instrument.binder.jvm.OpenTelemetryJvmMemoryMeterConventions;
                    import io.micrometer.core.instrument.binder.jvm.OpenTelemetryJvmThreadMeterConventions;
                    import io.micrometer.core.instrument.binder.jvm.OpenTelemetryJvmClassLoadingMeterConventions;
                    import io.micrometer.core.instrument.Tags;
                    import java.util.List;

                    @Configuration(proxyBeanMethods = false)
                    public class OpenTelemetryConfiguration {

                        @Bean
                        public OpenTelemetryJvmCpuMeterConventions openTelemetryJvmCpuMeterConventions() {
                            return new OpenTelemetryJvmCpuMeterConventions(Tags.empty());
                        }

                        @Bean
                        public ProcessorMetrics processorMetrics() {
                            return new ProcessorMetrics(List.of(), new OpenTelemetryJvmCpuMeterConventions(Tags.empty()));
                        }

                        @Bean
                        public JvmMemoryMetrics jvmMemoryMetrics() {
                            return new JvmMemoryMetrics(List.of(), new OpenTelemetryJvmMemoryMeterConventions(Tags.empty()));
                        }

                        @Bean
                        public JvmThreadMetrics jvmThreadMetrics() {
                            return new JvmThreadMetrics(List.of(), new OpenTelemetryJvmThreadMeterConventions(Tags.empty()));
                        }

                        @Bean
                        public ClassLoaderMetrics classLoaderMetrics() {
                            return new ClassLoaderMetrics(new OpenTelemetryJvmClassLoadingMeterConventions());
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))
            }
        }

        // 4. Register task to generate default resources (logback-spring.xml)
        val generateOtelResourcesTask = project.tasks.register("generateOtelResources") {
            val outputDir = project.layout.buildDirectory.dir("generated/sources/otel/resources")
            outputs.dir(outputDir)
            doLast {
                val resourcesDir = outputDir.get().asFile
                resourcesDir.mkdirs()
                resourcesDir.resolve("logback-spring.xml").writeText("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <configuration>
                        <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
                        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

                        <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
                        </appender>

                        <root level="INFO">
                            <appender-ref ref="CONSOLE"/>
                            <appender-ref ref="OTEL"/>
                        </root>
                    </configuration>
                """.trimIndent().replace("\r\n", "\n"))
            }
        }

        // 5. Integrate source and resource generation directories to Java main source set
        project.afterEvaluate {
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")
                mainSourceSet.java.srcDir(generateOtelConfigTask)
                mainSourceSet.resources.srcDir(generateOtelResourcesTask)

                // Ensure compiler task dependency
                project.tasks.matching { it.name == "compileJava" }.configureEach {
                    dependsOn(generateOtelConfigTask)
                }
                project.tasks.matching { it.name == "compileKotlin" }.configureEach {
                    dependsOn(generateOtelConfigTask)
                }
                project.tasks.matching { it.name == "processResources" }.configureEach {
                    dependsOn(generateOtelResourcesTask)
                }
            }
        }
    }
}
