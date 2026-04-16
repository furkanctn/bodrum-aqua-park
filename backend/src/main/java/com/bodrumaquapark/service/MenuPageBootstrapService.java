package com.bodrumaquapark.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;

@Service
public class MenuPageBootstrapService {

	private final MenuPageRepository menuPageRepository;
	private final SaleAreaRepository saleAreaRepository;
	private final ProductRepository productRepository;

	public MenuPageBootstrapService(MenuPageRepository menuPageRepository, SaleAreaRepository saleAreaRepository,
			ProductRepository productRepository) {
		this.menuPageRepository = menuPageRepository;
		this.saleAreaRepository = saleAreaRepository;
		this.productRepository = productRepository;
	}

	/**
	 * Her satış alanı için GENEL menü sayfası oluşturur; menü sayfası olmayan ürünleri GENEL’e bağlar.
	 */
	@Transactional
	public void ensureMenuPagesAndOrphanProducts() {
		saleAreaRepository.findAll().forEach(sa -> {
			if (!menuPageRepository.existsBySaleArea_IdAndCode(sa.getId(), "GENEL")) {
				menuPageRepository.save(new MenuPage(sa, "GENEL", "Genel", 0));
			}
		});
		for (Product p : productRepository.findByMenuPageIsNull()) {
			menuPageRepository.findBySaleArea_IdAndCode(p.getSaleArea().getId(), "GENEL").ifPresent(mp -> {
				p.setMenuPage(mp);
				productRepository.save(p);
			});
		}
	}
}
