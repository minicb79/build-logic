package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

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
                    import jakarta.annotation.Nullable;
                    import jakarta.servlet.FilterChain;
                    import jakarta.servlet.ServletException;
                    import jakarta.servlet.http.HttpServletRequest;
                    import jakarta.servlet.http.HttpServletResponse;
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

                    import io.micrometer.core.instrument.MeterRegistry;
                    import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
                    import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
                    import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
                    import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
                    import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
                    import jakarta.annotation.PostConstruct;
                    import org.springframework.context.annotation.Configuration;

                    @Configuration(proxyBeanMethods = false)
                    public class OpenTelemetryConfiguration {

                        private final MeterRegistry meterRegistry;

                        public OpenTelemetryConfiguration(MeterRegistry meterRegistry) {
                            this.meterRegistry = meterRegistry;
                        }

                        @PostConstruct
                        public void bindMetrics() {
                            new ClassLoaderMetrics().bindTo(meterRegistry);
                            new JvmMemoryMetrics().bindTo(meterRegistry);
                            new JvmGcMetrics().bindTo(meterRegistry);
                            new JvmThreadMetrics().bindTo(meterRegistry);
                            new ProcessorMetrics().bindTo(meterRegistry);
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))

                // OtelAutoConfiguration.java
                comMinicdesignOtelDir.resolve("OtelAutoConfiguration.java").writeText("""
                    package com.minicdesign.otel;

                    import org.springframework.context.annotation.Configuration;
                    import org.springframework.context.annotation.Import;

                    @Configuration(proxyBeanMethods = false)
                    @Import({
                        InstallOpenTelemetryAppender.class,
                        TraceIdFilter.class,
                        ContextPropagationConfiguration.class,
                        OpenTelemetryConfiguration.class
                    })
                    public class OtelAutoConfiguration {
                    }
                """.trimIndent().replace("\r\n", "\n"))

                // MaskingMessageConverter.java
                comMinicdesignOtelDir.resolve("MaskingMessageConverter.java").writeText("""
                    package com.minicdesign.otel;

                    import ch.qos.logback.classic.pattern.MessageConverter;
                    import ch.qos.logback.classic.spi.ILoggingEvent;
                    import java.util.regex.Matcher;
                    import java.util.regex.Pattern;

                    public class MaskingMessageConverter extends MessageConverter {

                        private Pattern maskingPattern = null;

                        @Override
                        public void start() {
                            String fieldsStr = getContext().getProperty("MASKED_FIELDS");
                            if (fieldsStr != null && !fieldsStr.trim().isEmpty()) {
                                String[] fields = fieldsStr.split(",");
                                StringBuilder regexBuilder = new StringBuilder();
                                for (String field : fields) {
                                    field = field.trim();
                                    if (!field.isEmpty()) {
                                        if (regexBuilder.length() > 0) {
                                            regexBuilder.append("|");
                                        }
                                        regexBuilder.append(Pattern.quote(field));
                                    }
                                }
                                if (regexBuilder.length() > 0) {
                                    String pattern = "(\"(?:" + regexBuilder + ")\"\\s*:\\s*\")([^\"]+)(\")|((?:" + regexBuilder + ")\\s*=\\s*)([^,\\}\\s\\]\\)]+)";
                                    maskingPattern = Pattern.compile(pattern);
                                }
                            }
                            super.start();
                        }

                        @Override
                        public String convert(ILoggingEvent event) {
                            String message = super.convert(event);
                            if (maskingPattern != null && message != null) {
                                Matcher matcher = maskingPattern.matcher(message);
                                if (matcher.find()) {
                                    StringBuffer sb = new StringBuffer(message.length());
                                    do {
                                        if (matcher.group(1) != null) {
                                            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + "***" + matcher.group(3)));
                                        } else if (matcher.group(4) != null) {
                                            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(4) + "***"));
                                        }
                                    } while (matcher.find());
                                    matcher.appendTail(sb);
                                    return sb.toString();
                                }
                            }
                            return message;
                        }
                    }
                """.trimIndent().replace("\r\n", "\n"))
            }
        }

        // 4. Register task to generate default resources (logback-spring.xml and AutoConfiguration imports)
        val generateOtelResourcesTask = project.tasks.register("generateOtelResources") {
            val outputDir = project.layout.buildDirectory.dir("generated/sources/otel/resources")
            outputs.dir(outputDir)
            doLast {
                val resourcesDir = outputDir.get().asFile
                resourcesDir.mkdirs()
                resourcesDir.resolve("logback-spring.xml").writeText("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <configuration>
                        <springProperty scope="context" name="MASKED_FIELDS" source="logging.mask.fields" defaultValue="password,creditCard,ssn,email"/>
                        <conversionRule conversionWord="msg" converterClass="com.minicdesign.otel.MaskingMessageConverter" />
                        <conversionRule conversionWord="m" converterClass="com.minicdesign.otel.MaskingMessageConverter" />
                        
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

                val metaInfSpringDir = resourcesDir.resolve("META-INF/spring")
                metaInfSpringDir.mkdirs()
                metaInfSpringDir.resolve("org.springframework.boot.autoconfigure.AutoConfiguration.imports").writeText("""
                    com.minicdesign.otel.OtelAutoConfiguration
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

            // Register the OTEL generated Java directory as an IDEA generated source root
            // so IntelliJ recognises it without requiring a prior build.
            project.plugins.withType(IdeaPlugin::class.java) {
                val ideaModel = project.extensions.getByType(IdeaModel::class.java)
                val otelJavaDir = project.layout.buildDirectory.dir("generated/sources/otel/java").get().asFile
                ideaModel.module.generatedSourceDirs.add(otelJavaDir)
                ideaModel.module.excludeDirs.remove(otelJavaDir)
            }
        }
    }
}
