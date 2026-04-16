package com.bodrumaquapark.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.entity.StaffUser;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.repository.StaffUserRepository;

@Configuration
public class StaffUserDataInitializer {

	private static final String ADMIN_USER_ID = "0000";
	private static final String ADMIN_PLAIN_PASSWORD = "0000";

	@Bean
	@Order(2)
	ApplicationRunner seedStaffUsers(StaffUserRepository repository, PasswordEncoder passwordEncoder,
			SaleAreaRepository saleAreaRepository, Environment env) {
		return args -> {
			long countBefore = repository.count();
			ensureDefaultAdmin(repository, passwordEncoder, saleAreaRepository);
			if (Boolean.parseBoolean(env.getProperty("app.bootstrap.repair-admin0000", "false"))) {
				repository.loadWithSaleAreasByUserId(ADMIN_USER_ID).ifPresent(u -> {
					u.setPasswordHash(passwordEncoder.encode(ADMIN_PLAIN_PASSWORD));
					u.setActive(true);
					u.setAdminPanelAccess(true);
					repository.save(u);
				});
			}
			if (countBefore == 0) {
				seedDemoCashiers(repository, passwordEncoder, saleAreaRepository);
			}
		};
	}

	private static void ensureDefaultAdmin(StaffUserRepository repository, PasswordEncoder passwordEncoder,
			SaleAreaRepository saleAreaRepository) {
		if (repository.existsStaffUserByUserId(ADMIN_USER_ID)) {
			return;
		}
		StaffUser admin = new StaffUser(ADMIN_USER_ID, passwordEncoder.encode(ADMIN_PLAIN_PASSWORD), "Yönetici",
				RoleCode.ADMIN);
		admin.setActive(true);
		admin.setAdminPanelAccess(true);
		saleAreaRepository.findAll().forEach(a -> admin.getSaleAreas().add(a));
		repository.save(admin);
	}

	private static void seedDemoCashiers(StaffUserRepository repository, PasswordEncoder passwordEncoder,
			SaleAreaRepository saleAreaRepository) {
		StaffUser cashier = new StaffUser("1001", passwordEncoder.encode("1001"), "Kasiyer içecek", RoleCode.CASHIER);
		saleAreaRepository.findByCode("BEVERAGE").ifPresent(a -> cashier.getSaleAreas().add(a));
		repository.save(cashier);

		StaffUser bakery = new StaffUser("1002", passwordEncoder.encode("1002"), "Fırın kasa", RoleCode.CASHIER);
		saleAreaRepository.findByCode("BAKERY").ifPresent(a -> bakery.getSaleAreas().add(a));
		bakery.setTicketSalesAllowed(false);
		bakery.setBalanceLoadAllowed(false);
		repository.save(bakery);
	}
}
