package com.parvez.task.config.stub;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class LocalStubProfileGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {"docker", "prod", "production"})
    void failsDuringStartupWhenStubIsCombinedWithDeploymentProfiles(String deploymentProfile) {
        assertThatThrownBy(() -> new SpringApplicationBuilder(LocalStubSecurityConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("local-stub", deploymentProfile)
                .run())
                .hasStackTraceContaining("local-stub profile cannot be combined with " + deploymentProfile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker", "prod", "production"})
    void allowsDeploymentProfilesWhenLocalStubIsNotActive(String deploymentProfile) {
        new SpringApplicationBuilder(LocalStubSecurityConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles(deploymentProfile)
                .run()
                .close();
    }
}