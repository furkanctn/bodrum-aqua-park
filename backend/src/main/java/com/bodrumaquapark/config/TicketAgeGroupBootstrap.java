package com.bodrumaquapark.config;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bodrumaquapark.entity.TicketAgeGroup;
import com.bodrumaquapark.repository.TicketAgeGroupRepository;
import com.bodrumaquapark.service.TicketSchemaMigrationService;

@Configuration
public class TicketAgeGroupBootstrap {

	private static final String ACENTA_YETISKIN = "Acenta Yetişkin";
	private static final String ACENTA_COCUK = "Acenta Çocuk";

	@Bean
	@Order(3)
	ApplicationRunner seedTicketAgeGroups(TicketAgeGroupRepository repo,
			TicketSchemaMigrationService ticketSchemaMigrationService) {
		return BootstrapResilience.safely("seedTicketAgeGroups", args -> {
			ticketSchemaMigrationService.migrateAgencyTicketSchemaIfNeeded();
			if (repo.count() == 0) {
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
			}
			ensureAgencyGroup(repo, ACENTA_YETISKIN, 80);
			ensureAgencyGroup(repo, ACENTA_COCUK, 90);
		});
	}

	private static void ensureAgencyGroup(TicketAgeGroupRepository repo, String name, int sortOrder) {
		Optional<TicketAgeGroup> existing = repo.findByNameIgnoreCase(name);
		if (existing.isEmpty()) {
			TicketAgeGroup g = new TicketAgeGroup(name, BigDecimal.ZERO, sortOrder, true);
			g.setAgencyComplimentary(true);
			repo.save(g);
			return;
		}
		TicketAgeGroup g = existing.get();
		boolean changed = false;
		if (!g.isAgencyComplimentary()) {
			g.setAgencyComplimentary(true);
			changed = true;
		}
		if (g.getPrice().compareTo(BigDecimal.ZERO) != 0) {
			g.setPrice(BigDecimal.ZERO);
			changed = true;
		}
		if (!g.isActive()) {
			g.setActive(true);
			changed = true;
		}
		if (changed) {
			repo.save(g);
		}
	}
}
