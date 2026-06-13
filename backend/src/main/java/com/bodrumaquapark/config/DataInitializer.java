package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.service.MenuPageBootstrapService;

@Configuration
public class DataInitializer {

	private static MenuPage resolveGenelMenuPage(MenuPageRepository menuPages) {
		return menuPages.findByCode("GENEL")
				.orElseGet(() -> menuPages.save(new MenuPage("GENEL", "Genel", 0)));
	}

	private static void seedExtraBakeryProducts(ProductRepository products, MenuPage bakeryMenu) {
		ensureProduct(products, bakeryMenu, "Simit", new BigDecimal("40.00"), 120);
		ensureProduct(products, bakeryMenu, "Peynirli poğaça", new BigDecimal("50.00"), 100);
		ensureProduct(products, bakeryMenu, "Kruvasan (çikolatalı)", new BigDecimal("95.00"), 60);
		ensureProduct(products, bakeryMenu, "Zeytinli ekmek dilim", new BigDecimal("55.00"), 80);
		ensureProduct(products, bakeryMenu, "Lahmacun", new BigDecimal("160.00"), 40);
	}

	private static void ensureProduct(ProductRepository products, MenuPage menu, String name, BigDecimal price,
			Integer stock) {
		if (!products.existsByMenuPage_IdAndName(menu.getId(), name)) {
			products.save(new Product(menu, name, price, stock));
		}
	}

	@Bean
	@Order(0)
	ApplicationRunner backfillMenuPages(MenuPageBootstrapService menuPageBootstrapService) {
		return BootstrapResilience.safely("backfillMenuPages",
				args -> menuPageBootstrapService.ensureMenuPagesAndOrphanProducts());
	}

	@Bean
	@Order(1)
	ApplicationRunner seedSaleAreasAndProducts(SaleAreaRepository saleAreas, ProductRepository products,
			MenuPageRepository menuPages) {
		return BootstrapResilience.safely("seedSaleAreasAndProducts", args -> {
			if (saleAreas.count() > 0) {
				return;
			}
			MenuPage genelMenu = resolveGenelMenuPage(menuPages);
			MenuPage beverageMenu = menuPages.findByCode("ICecek")
					.orElseGet(() -> menuPages.save(new MenuPage("ICecek", "İçecek", 0)));
			MenuPage bakeryMenu = menuPages.findByCode("FIRIN")
					.orElseGet(() -> menuPages.save(new MenuPage("FIRIN", "Fırın", 0)));
			MenuPage alcoholMenu = menuPages.findByCode("ALKOL")
					.orElseGet(() -> menuPages.save(new MenuPage("ALKOL", "Alkollü içecekler", 0)));
			MenuPage iceMenu = menuPages.findByCode("DONDURMA")
					.orElseGet(() -> menuPages.save(new MenuPage("DONDURMA", "Dondurmalar", 0)));

			SaleArea beverage = saleAreas.save(new SaleArea("BEVERAGE", "İçecek"));
			beverage.getMenuPages().add(beverageMenu);
			beverage.getMenuPages().add(genelMenu);
			saleAreas.save(beverage);

			SaleArea bakery = saleAreas.save(new SaleArea("BAKERY", "Fırın"));
			bakery.getMenuPages().add(bakeryMenu);
			bakery.getMenuPages().add(genelMenu);
			saleAreas.save(bakery);

			SaleArea alcohol = saleAreas.save(new SaleArea("ALCOHOL", "Alkollü içecekler"));
			alcohol.getMenuPages().add(alcoholMenu);
			saleAreas.save(alcohol);

			SaleArea iceCream = saleAreas.save(new SaleArea("ICE_CREAM", "Dondurmalar"));
			iceCream.getMenuPages().add(iceMenu);
			saleAreas.save(iceCream);

			products.save(new Product(beverageMenu, "Ayran", new BigDecimal("35.00"), null));
			products.save(new Product(beverageMenu, "Kola 33cl", new BigDecimal("45.00"), 200));
			products.save(new Product(beverageMenu, "Su 50cl", new BigDecimal("20.00"), null));

			products.save(new Product(bakeryMenu, "Margherita dilim", new BigDecimal("180.00"), 50));
			products.save(new Product(bakeryMenu, "Karışık dilim", new BigDecimal("220.00"), 40));
			seedExtraBakeryProducts(products, bakeryMenu);

			products.save(new Product(alcoholMenu, "Bira 50cl", new BigDecimal("120.00"), 80));
			products.save(new Product(alcoholMenu, "Şarap kadeh", new BigDecimal("150.00"), 40));

			products.save(new Product(iceMenu, "Dondurma külah", new BigDecimal("85.00"), 60));
			products.save(new Product(iceMenu, "Dondurma kutu", new BigDecimal("95.00"), 45));
		});
	}

	@Bean
	@Order(2)
	ApplicationRunner ensureBakeryCatalog(ProductRepository products, MenuPageRepository menuPages) {
		return BootstrapResilience.safely("ensureBakeryCatalog", args -> menuPages.findByCode("FIRIN").ifPresent(
				bakeryMenu -> seedExtraBakeryProducts(products, bakeryMenu)));
	}
}
