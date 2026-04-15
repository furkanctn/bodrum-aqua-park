package com.bodrumaquapark.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;

@Configuration
public class DataInitializer {

	@Bean
	@Order(1)
	ApplicationRunner seedSaleAreasAndProducts(SaleAreaRepository saleAreas, ProductRepository products) {
		return args -> {
			if (saleAreas.count() > 0) {
				return;
			}
			SaleArea beverage = saleAreas.save(new SaleArea("BEVERAGE", "İçecek"));
			SaleArea bakery = saleAreas.save(new SaleArea("BAKERY", "Fırın"));
			SaleArea alcohol = saleAreas.save(new SaleArea("ALCOHOL", "Alkollü içecekler"));
			SaleArea iceCream = saleAreas.save(new SaleArea("ICE_CREAM", "Dondurmalar"));

			products.save(new Product(beverage, "Ayran", new BigDecimal("35.00"), null));
			products.save(new Product(beverage, "Kola 33cl", new BigDecimal("45.00"), 200));
			products.save(new Product(beverage, "Su 50cl", new BigDecimal("20.00"), null));

			products.save(new Product(bakery, "Margherita dilim", new BigDecimal("180.00"), 50));
			products.save(new Product(bakery, "Karışık dilim", new BigDecimal("220.00"), 40));
			seedExtraBakeryProducts(products, bakery);

			products.save(new Product(alcohol, "Bira 50cl", new BigDecimal("120.00"), 80));
			products.save(new Product(alcohol, "Şarap kadeh", new BigDecimal("150.00"), 40));

			products.save(new Product(iceCream, "Dondurma külah", new BigDecimal("85.00"), 60));
			products.save(new Product(iceCream, "Dondurma kutu", new BigDecimal("95.00"), 45));
		};
	}

	/**
	 * Mevcut veritabanlarında fırın ürünleri eksikse tamamlar (yeniden çalıştırmada çoğaltmaz).
	 */
	@Bean
	@Order(2)
	ApplicationRunner ensureBakeryCatalog(ProductRepository products, SaleAreaRepository saleAreas) {
		return args -> saleAreas.findByCode("BAKERY").ifPresent(bakery -> seedExtraBakeryProducts(products, bakery));
	}

	private static void seedExtraBakeryProducts(ProductRepository products, SaleArea bakery) {
		ensureProduct(products, bakery, "Simit", new BigDecimal("40.00"), 120);
		ensureProduct(products, bakery, "Peynirli poğaça", new BigDecimal("50.00"), 100);
		ensureProduct(products, bakery, "Kruvasan (çikolatalı)", new BigDecimal("95.00"), 60);
		ensureProduct(products, bakery, "Zeytinli ekmek dilim", new BigDecimal("55.00"), 80);
		ensureProduct(products, bakery, "Lahmacun", new BigDecimal("160.00"), 40);
	}

	private static void ensureProduct(ProductRepository products, SaleArea bakery, String name, BigDecimal price,
			Integer stock) {
		if (!products.existsBySaleArea_CodeAndName(bakery.getCode(), name)) {
			products.save(new Product(bakery, name, price, stock));
		}
	}
}
