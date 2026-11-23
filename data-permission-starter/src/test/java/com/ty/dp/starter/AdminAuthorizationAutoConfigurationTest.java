package com.ty.dp.starter;

import com.ty.dp.starter.admin.DataPermissionAdminAuthorizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AdminAuthorizationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataPermissionAutoConfiguration.class));

    @Test
    void disabledByDefault(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(WebMvcConfigurer.class);
            assertThat(output).doesNotContain("Management API is enabled without");
        });
    }

    @Test
    void enabledWithoutAuthorizerWarns(CapturedOutput output) {
        runner.withPropertyValues("data-permission.admin.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(WebMvcConfigurer.class);
            assertThat(output).contains("Management API is enabled without a DataPermissionAdminAuthorizer");
        });
    }

    @Test
    void enabledWithAuthorizerDoesNotWarn(CapturedOutput output) {
        runner.withPropertyValues("data-permission.admin.enabled=true")
                .withBean(DataPermissionAdminAuthorizer.class, () -> action -> true)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WebMvcConfigurer.class);
                    assertThat(output).doesNotContain("Management API is enabled without");
                });
    }

    @Test
    void noMvcDependencyDoesNotBreakStartup() {
        runner.withPropertyValues("data-permission.admin.enabled=true")
                .withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean("dataPermissionAdminAuthorizationWarning"));
    }

    @Test
    void noWebDependenciesDoesNotBreakStartup() {
        runner.withPropertyValues("data-permission.admin.enabled=true")
                .withClassLoader(new FilteredClassLoader("org.springframework.web", "javax.servlet"))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean("dataPermissionAdminAuthorizationWarning"));
    }

    @Test
    void masterSwitchDisablesAdminAuthorization() {
        runner.withPropertyValues("data-permission.enabled=false", "data-permission.admin.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(WebMvcConfigurer.class));
    }
}
