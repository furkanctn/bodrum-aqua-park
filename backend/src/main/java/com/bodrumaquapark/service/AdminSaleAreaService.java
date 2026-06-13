package com.bodrumaquapark.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.util.SlugCodes;
import com.bodrumaquapark.web.dto.CreateSaleAreaRequest;
import com.bodrumaquapark.web.dto.SaleAreaAdminResponse;
import com.bodrumaquapark.web.dto.UpdateSaleAreaRequest;

@Service
public class AdminSaleAreaService {

	private final SaleAreaRepository saleAreaRepository;
	private final ProductRepository productRepository;
	private final MenuPageRepository menuPageRepository;

	public AdminSaleAreaService(SaleAreaRepository saleAreaRepository, ProductRepository productRepository,
			MenuPageRepository menuPageRepository) {
		this.saleAreaRepository = saleAreaRepository;
		this.productRepository = productRepository;
		this.menuPageRepository = menuPageRepository;
	}

	@Transactional(readOnly = true)
	public List<SaleAreaAdminResponse> listAll() {
		return saleAreaRepository.findAllWithMenuPages().stream()
				.sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
				.map(this::toAdminResponse)
				.toList();
	}

	private SaleAreaAdminResponse toAdminResponse(SaleArea a) {
		long total = productRepository.countBySaleAreaMenus(a.getId());
		long active = productRepository.countActiveBySaleAreaMenus(a.getId());
		return SaleAreaAdminResponse.from(a, active, total);
	}

	@Transactional
	public SaleAreaAdminResponse create(CreateSaleAreaRequest req) {
		String name = req.name().trim();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Satış alanı adı boş olamaz");
		}
		String code = req.code() != null && !req.code().isBlank()
				? normalizeCode(req.code())
				: SlugCodes.uniqueCode(SlugCodes.slugFromDisplayName(name),
						c -> saleAreaRepository.findByCode(c).isPresent());
		if (saleAreaRepository.findByCode(code).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu kodda bir satış alanı zaten var");
		}
		SaleArea saved = saleAreaRepository.save(new SaleArea(code, name));
		if (req.menuPageIds() != null && !req.menuPageIds().isEmpty()) {
			applyMenuPages(saved, req.menuPageIds());
			saved = saleAreaRepository.findWithMenuPagesById(saved.getId()).orElseThrow();
		}
		return toAdminResponse(saved);
	}

	@Transactional
	public SaleAreaAdminResponse update(Long id, UpdateSaleAreaRequest req) {
		SaleArea a = saleAreaRepository.findWithMenuPagesById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Satış alanı bulunamadı"));
		String name = req.name().trim();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Satış alanı adı boş olamaz");
		}
		a.setName(name);
		if (req.menuPageIds() != null) {
			applyMenuPages(a, req.menuPageIds());
		}
		saleAreaRepository.save(a);
		return toAdminResponse(a);
	}

	@Transactional
	public void delete(Long id) {
		SaleArea a = saleAreaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Satış alanı bulunamadı"));
		saleAreaRepository.deleteStaffUserAssignmentsBySaleAreaId(id);
		saleAreaRepository.delete(a);
	}

	private void applyMenuPages(SaleArea area, List<Long> menuPageIds) {
		Set<MenuPage> pages = new HashSet<>();
		for (Long mpId : menuPageIds) {
			if (mpId == null) {
				continue;
			}
			MenuPage mp = menuPageRepository.findById(mpId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Menü sayfası bulunamadı: " + mpId));
			pages.add(mp);
		}
		area.setMenuPages(pages);
	}

	private static String normalizeCode(String raw) {
		String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
		if (s.isEmpty() || !s.matches("[A-Z0-9_]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Kod yalnızca harf, rakam ve alt çizgi içermeli (örn. KULE1 veya ICE_CREAM)");
		}
		return s;
	}
}
