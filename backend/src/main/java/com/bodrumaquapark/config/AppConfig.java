package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ EntryProperties.class, JwtProperties.class, CardProperties.class,
		AccessBootstrapProperties.class, AccessGatewayProperties.class })
public class AppConfig {
}
