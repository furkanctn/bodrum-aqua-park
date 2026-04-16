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

	@Bean
	@Order(0)
	ApplicationRunner backfillMenuPages(MenuPageBootstrapService menuPageBootstrapService) {
		return args -> menuPageBootstrapService.ensureMenuPagesAndOrphanProducts();
	}

	@Bean
	@Order(1)
	ApplicationRunner seedSaleAreasAndProducts(SaleAreaRepository saleAreas, ProductRepository products,
			MenuPageRepository menuPages) {
		return args -> {
			if (saleAreas.count() > 0) {
				return;
			}
			SaleArea beverage = saleAreas.save(new SaleArea("BEVERAGE", "İçecek"));
			SaleArea bakery = saleAreas.save(new SaleArea("BAKERY", "Fırın"));
			SaleArea alcohol = saleAreas.save(new SaleArea("ALCOHOL", "Alkollü içecekler"));
			SaleArea iceCream = saleAreas.save(new SaleArea("ICE_CREAM", "Dondurmalar"));

			MenuPage bevGen = menuPages.save(new MenuPage(beverage, "GENEL", "Genel", 0));
			MenuPage bakGen = menuPages.save(new MenuPage(bakery, "GENEL", "Genel", 0));
			MenuPage alcGen = menuPages.save(new MenuPage(alcohol, "GENEL", "Genel", 0));
			MenuPage iceGen = menuPages.save(new MenuPage(iceCream, "GENEL", "Genel", 0));

			products.save(new Product(beverage, bevGen, "Ayran", new BigDecimal("35.00"), null));
			products.save(new Product(beverage, bevGen, "Kola 33cl", new BigDecimal("45.00"), 200));
			products.save(new Product(beverage, bevGen, "Su 50cl", new BigDecimal("20.00"), null));

			products.save(new Product(bakery, bakGen, "Margherita dilim", new BigDecimal("180.00"), 50));
			products.save(new Product(bakery, bakGen, "Karışık dilim", new BigDecimal("220.00"), 40));
			seedExtraBakeryProducts(products, menuPages, bakery);

			products.save(new Product(alcohol, alcGen, "Bira 50cl", new BigDecimal("120.00"), 80));
			products.save(new Product(alcohol, alcGen, "Şarap kadeh", new BigDecimal("150.00"), 40));

			products.save(new Product(iceCream, iceGen, "Dondurma külah", new BigDecimal("85.00"), 60));
			products.save(new Product(iceCream, iceGen, "Dondurma kutu", new BigDecimal("95.00"), 45));
		};
	}

	/**
	 * Mevcut veritabanlarında fırın ürünleri eksikse tamamlar (yeniden çalıştırmada çoğaltmaz).
	 */
	@Bean
	@Order(2)
	ApplicationRunner ensureBakeryCatalog(ProductRepository products, SaleAreaRepository saleAreas,
			MenuPageRepository menuPages) {
		return args -> saleAreas.findByCode("BAKERY")
				.ifPresent(bakery -> seedExtraBakeryProducts(products, menuPages, bakery));
	}

	private static void seedExtraBakeryProducts(ProductRepository products, MenuPageRepository menuPages,
			SaleArea bakery) {
		ensureProduct(products, menuPages, bakery, "Simit", new BigDecimal("40.00"), 120);
		ensureProduct(products, menuPages, bakery, "Peynirli poğaça", new BigDecimal("50.00"), 100);
		ensureProduct(products, menuPages, bakery, "Kruvasan (çikolatalı)", new BigDecimal("95.00"), 60);
		ensureProduct(products, menuPages, bakery, "Zeytinli ekmek dilim", new BigDecimal("55.00"), 80);
		ensureProduct(products, menuPages, bakery, "Lahmacun", new BigDecimal("160.00"), 40);
	}

	private static void ensureProduct(ProductRepository products, MenuPageRepository menuPages, SaleArea bakery,
			String name, BigDecimal price, Integer stock) {
		if (!products.existsBySaleArea_CodeAndName(bakery.getCode(), name)) {
			MenuPage mp = menuPages.findBySaleArea_IdAndCode(bakery.getId(), "GENEL")
					.orElseGet(() -> menuPages.save(new MenuPage(bakery, "GENEL", "Genel", 0)));
			products.save(new Product(bakery, mp, name, price, stock));
		}
	}
}
