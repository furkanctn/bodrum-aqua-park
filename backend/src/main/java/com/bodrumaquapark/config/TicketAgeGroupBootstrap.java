package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bodrumaquapark.entity.TicketAgeGroup;
import com.bodrumaquapark.repository.TicketAgeGroupRepository;

@Configuration
public class TicketAgeGroupBootstrap {

	@Bean
	@Order(3)
	ApplicationRunner seedTicketAgeGroups(TicketAgeGroupRepository repo) {
		return args -> {
			if (repo.count() > 0) {
				return;
			}
			int o = 10;
			repo.save(new TicketAgeGroup("0–6 Yaş", new BigDecimal("0.00"), o, true));
			o += 10;
			repo.save(new TicketAgeGroup("7–12 Yaş", new BigDecimal("12.50"), o, true));
			o += 10;
			repo.save(new TicketAgeGroup("Yetişkin", new BigDecimal("15.00"), o, true));
			o += 10;
			repo.save(new TicketAgeGroup("İndirimli yetişkin", new BigDecimal("14.00"), o, true));
			o += 10;
			repo.save(new TicketAgeGroup("Öğrenci", new BigDecimal("11.00"), o, true));
			o += 10;
			repo.save(new TicketAgeGroup("65+ Yaş", new BigDecimal("9.50"), o, true));
		};
	}
}
