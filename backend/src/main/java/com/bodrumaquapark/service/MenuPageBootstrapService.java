package com.bodrumaquapark.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;

@Service
public class MenuPageBootstrapService {

	private final MenuPageRepository menuPageRepository;
	private final ProductRepository productRepository;
	private final CatalogMigrationService catalogMigrationService;

	public MenuPageBootstrapService(MenuPageRepository menuPageRepository, ProductRepository productRepository,
			CatalogMigrationService catalogMigrationService) {
		this.menuPageRepository = menuPageRepository;
		this.productRepository = productRepository;
		this.catalogMigrationService = catalogMigrationService;
	}

	@Transactional
	public void ensureMenuPagesAndOrphanProducts() {
		catalogMigrationService.migrateLegacyMenuAreaLinksIfNeeded();
		catalogMigrationService.deduplicateMenuPageCodesIfNeeded();
		catalogMigrationService.ensureOrphanProductsHaveMenu();
		for (Product p : productRepository.findByMenuPageIsNull()) {
			MenuPage genel = menuPageRepository.findFirstByCodeOrderByIdAsc("GENEL")
					.orElseGet(() -> menuPageRepository.save(new MenuPage("GENEL", "Genel", 0)));
			p.setMenuPage(genel);
			productRepository.save(p);
		}
	}
}
