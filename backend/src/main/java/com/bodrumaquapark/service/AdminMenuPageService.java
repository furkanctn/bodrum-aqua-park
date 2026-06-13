package com.bodrumaquapark.service;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.util.SlugCodes;
import com.bodrumaquapark.web.dto.CreateMenuPageRequest;
import com.bodrumaquapark.web.dto.MenuPageAdminResponse;
import com.bodrumaquapark.web.dto.UpdateMenuPageRequest;

@Service
public class AdminMenuPageService {

	private final MenuPageRepository menuPageRepository;
	private final ProductRepository productRepository;

	public AdminMenuPageService(MenuPageRepository menuPageRepository, ProductRepository productRepository) {
		this.menuPageRepository = menuPageRepository;
		this.productRepository = productRepository;
	}

	@Transactional(readOnly = true)
	public List<MenuPageAdminResponse> listAll() {
		return menuPageRepository.findAllByOrderBySortOrderAscIdAsc().stream()
				.map(m -> MenuPageAdminResponse.from(m, productRepository.countByMenuPage_Id(m.getId())))
				.toList();
	}

	@Transactional
	public MenuPageAdminResponse create(CreateMenuPageRequest req) {
		String name = req.name().trim();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menü adı boş olamaz");
		}
		String slug = SlugCodes.slugFromDisplayName(name);
		Predicate<String> codeTaken = menuPageRepository::existsByCode;
		String code = SlugCodes.uniqueCode(slug, codeTaken);
		int sort = req.sortOrder() != null ? req.sortOrder() : 0;
		MenuPage saved = menuPageRepository.save(new MenuPage(code, name, sort));
		return MenuPageAdminResponse.from(saved, 0);
	}

	@Transactional
	public MenuPageAdminResponse update(Long id, UpdateMenuPageRequest req) {
		MenuPage m = menuPageRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü sayfası bulunamadı"));
		if (req.name() != null) {
			String n = req.name().trim();
			if (n.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menü adı boş olamaz");
			}
			m.setName(n);
		}
		if (req.sortOrder() != null) {
			m.setSortOrder(req.sortOrder());
		}
		menuPageRepository.save(m);
		return MenuPageAdminResponse.from(m, productRepository.countByMenuPage_Id(m.getId()));
	}

	@Transactional
	public void delete(Long id) {
		MenuPage m = menuPageRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü sayfası bulunamadı"));
		long n = productRepository.countByMenuPage_Id(id);
		if (n > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Bu menüde ürün var. Önce ürünleri taşıyın veya silin.");
		}
		menuPageRepository.delete(m);
	}
}
