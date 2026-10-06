package com.parvez.task.config.stub;

import com.parvez.task.config.TaskJwtProperties;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("local-stub")
public class LocalStubSecurityConfiguration {
    private static final Set<String> FORBIDDEN_PROFILES = Set.of("docker", "prod", "production");

    @Bean
    static BeanFactoryPostProcessor localStubProfileGuard(Environment environment) {
        return beanFactory -> {
            for (String profile : environment.getActiveProfiles()) {
                if (FORBIDDEN_PROFILES.contains(profile)) {
                    throw new IllegalStateException("The local-stub profile cannot be combined with " + profile);
                }
            }
        };
    }

    @Bean
    JwtDecoder localStubJwtDecoder(TaskJwtProperties properties) {
        return new LocalStubJwtDecoder(properties.audience());
    }
}