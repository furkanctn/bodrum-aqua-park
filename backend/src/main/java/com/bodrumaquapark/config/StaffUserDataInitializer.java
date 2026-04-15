package com.bodrumaquapark.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.entity.StaffUser;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.repository.StaffUserRepository;

@Configuration
public class StaffUserDataInitializer {

	@Bean
	@Order(2)
	ApplicationRunner seedStaffUsers(StaffUserRepository repository, PasswordEncoder passwordEncoder,
			SaleAreaRepository saleAreaRepository) {
		return args -> {
			if (repository.count() > 0) {
				return;
			}
			StaffUser admin = new StaffUser("0000", passwordEncoder.encode("0000"), "Yönetici", RoleCode.ADMIN);
			saleAreaRepository.findAll().forEach(a -> admin.getSaleAreas().add(a));
			repository.save(admin);

			StaffUser cashier = new StaffUser("1001", passwordEncoder.encode("1001"), "Kasiyer içecek", RoleCode.CASHIER);
			saleAreaRepository.findByCode("BEVERAGE").ifPresent(a -> cashier.getSaleAreas().add(a));
			repository.save(cashier);

			StaffUser bakery = new StaffUser("1002", passwordEncoder.encode("1002"), "Fırın kasa", RoleCode.CASHIER);
			saleAreaRepository.findByCode("BAKERY").ifPresent(a -> bakery.getSaleAreas().add(a));
			bakery.setTicketSalesAllowed(false);
			bakery.setBalanceLoadAllowed(false);
			repository.save(bakery);
		};
	}
}
