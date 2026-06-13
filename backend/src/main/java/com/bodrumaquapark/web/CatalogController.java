package com.bodrumaquapark.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.web.dto.MenuPageResponse;
import com.bodrumaquapark.web.dto.ProductResponse;
import com.bodrumaquapark.web.dto.SaleAreaResponse;

@RestController
@RequestMapping("/api")
public class CatalogController {

	private final SaleAreaRepository saleAreaRepository;
	private final ProductRepository productRepository;
	private final MenuPageRepository menuPageRepository;

	public CatalogController(SaleAreaRepository saleAreaRepository, ProductRepository productRepository,
			MenuPageRepository menuPageRepository) {
		this.saleAreaRepository = saleAreaRepository;
		this.productRepository = productRepository;
		this.menuPageRepository = menuPageRepository;
	}

	@GetMapping("/sale-areas")
	public List<SaleAreaResponse> saleAreas(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes) {
		if (allowedCodes.isEmpty()) {
			return List.of();
		}
		return saleAreaRepository.findAllByCodeIn(new ArrayList<>(allowedCodes)).stream()
				.sorted(Comparator.comparing(SaleArea::getCode))
				.map(SaleAreaResponse::from)
				.toList();
	}

	@GetMapping("/menu-pages")
	public List<MenuPageResponse> menuPages(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes) {
		if (allowedCodes.isEmpty()) {
			return List.of();
		}
		List<MenuPageResponse> out = new ArrayList<>();
		for (SaleArea area : saleAreaRepository.findAllByCodeIn(allowedCodes)) {
			area.getMenuPages().stream()
					.sorted(Comparator.comparingInt(MenuPage::getSortOrder).thenComparingLong(MenuPage::getId))
					.forEach(mp -> out.add(MenuPageResponse.from(mp, area)));
		}
		out.sort(Comparator.comparing(MenuPageResponse::saleAreaCode).thenComparingInt(MenuPageResponse::sortOrder)
				.thenComparingLong(MenuPageResponse::id));
		return out;
	}

	@GetMapping("/products")
	public List<ProductResponse> products(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes,
			@RequestParam(value = "saleAreaCode", required = false) String saleAreaCode,
			@RequestParam(value = "menuPageId", required = false) Long menuPageId) {
		if (allowedCodes.isEmpty()) {
			return List.of();
		}
		if (menuPageId != null) {
			MenuPage mp = menuPageRepository.findById(menuPageId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü sayfası bulunamadı"));
			if (saleAreaCode == null || saleAreaCode.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saleAreaCode gerekli");
			}
			String areaCode = saleAreaCode.trim();
			if (!allowedCodes.contains(areaCode)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu satış alanına erişim yetkiniz yok");
			}
			SaleArea area = saleAreaRepository.findWithMenuPagesByCode(areaCode)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz satış alanı"));
			boolean linked = area.getMenuPages().stream().anyMatch(m -> m.getId().equals(mp.getId()));
			if (!linked) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menü bu satış alanında tanımlı değil");
			}
			return productRepository.findByActiveTrueAndMenuPage_IdOrderByNameAsc(menuPageId).stream()
					.map(p -> ProductResponse.from(p, area.getCode(), area.getName()))
					.toList();
		}
		if (saleAreaCode != null && !saleAreaCode.isBlank()) {
			String code = saleAreaCode.trim();
			if (!allowedCodes.contains(code)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu satış alanına erişim yetkiniz yok");
			}
			SaleArea area = saleAreaRepository.findWithMenuPagesByCode(code)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz satış alanı"));
			List<Long> menuIds = area.getMenuPages().stream().map(MenuPage::getId).toList();
			if (menuIds.isEmpty()) {
				return List.of();
			}
			return productRepository.findByActiveTrueAndMenuPage_IdInOrderByMenuPage_SortOrderAscNameAsc(menuIds)
					.stream()
					.map(p -> ProductResponse.from(p, area.getCode(), area.getName()))
					.toList();
		}
		List<Long> allMenuIds = saleAreaRepository.findAllByCodeIn(allowedCodes).stream()
				.flatMap(sa -> sa.getMenuPages().stream())
				.map(MenuPage::getId)
				.distinct()
				.toList();
		if (allMenuIds.isEmpty()) {
			return List.of();
		}
		return productRepository.findByActiveTrueAndMenuPage_IdInOrderByMenuPage_SortOrderAscNameAsc(allMenuIds)
				.stream()
				.map(ProductResponse::from)
				.toList();
	}
}
