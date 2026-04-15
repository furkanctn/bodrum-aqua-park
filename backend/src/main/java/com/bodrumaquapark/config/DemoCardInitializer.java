package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bodrumaquapark.service.CardService;

@Configuration
public class DemoCardInitializer {

	@Bean
	@Order(3)
	ApplicationRunner ensureDemoCard(CardService cardService, CardProperties cardProperties) {
		return args -> {
			String uid = cardProperties.getDemoUid() != null ? cardProperties.getDemoUid().trim() : "";
			if (uid.isEmpty()) {
				return;
			}
			BigDecimal bal = cardProperties.getDemoBalance() != null ? cardProperties.getDemoBalance() : BigDecimal.ZERO;
			cardService.ensureDemoCard(uid, bal);
		};
	}
}
