package com.bodrumaquapark.service;

import java.util.List;
import java.util.Locale;

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
		return saleAreaRepository.findAll().stream()
				.sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
				.map(this::toAdminResponse)
				.toList();
	}

	private SaleAreaAdminResponse toAdminResponse(SaleArea a) {
		long total = productRepository.countBySaleArea_Id(a.getId());
		long active = productRepository.countBySaleArea_IdAndActiveTrue(a.getId());
		return SaleAreaAdminResponse.from(a, active, total);
	}

	@Transactional
	public SaleAreaAdminResponse create(CreateSaleAreaRequest req) {
		String name = req.name().trim();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori adı boş olamaz");
		}
		String code = req.code() != null && !req.code().isBlank()
				? normalizeCode(req.code())
				: SlugCodes.uniqueCode(SlugCodes.slugFromDisplayName(name),
						c -> saleAreaRepository.findByCode(c).isPresent());
		if (saleAreaRepository.findByCode(code).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu kodda bir kategori zaten var");
		}
		SaleArea saved = saleAreaRepository.save(new SaleArea(code, name));
		if (!menuPageRepository.existsBySaleArea_IdAndCode(saved.getId(), "GENEL")) {
			menuPageRepository.save(new MenuPage(saved, "GENEL", "Genel", 0));
		}
		return toAdminResponse(saved);
	}

	@Transactional
	public SaleAreaAdminResponse update(Long id, UpdateSaleAreaRequest req) {
		SaleArea a = saleAreaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadı"));
		String name = req.name().trim();
		if (name.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori adı boş olamaz");
		}
		a.setName(name);
		saleAreaRepository.save(a);
		return toAdminResponse(a);
	}

	@Transactional
	public void delete(Long id) {
		SaleArea a = saleAreaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategori bulunamadı"));
		long total = productRepository.countBySaleArea_Id(id);
		if (total > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Bu kategoride ürün var. Önce ürünleri silin, pasifleştirin veya başka kategoriye taşıyın.");
		}
		menuPageRepository.deleteBySaleArea_Id(id);
		saleAreaRepository.deleteStaffUserAssignmentsBySaleAreaId(id);
		saleAreaRepository.delete(a);
	}

	private static String normalizeCode(String raw) {
		String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
		if (s.isEmpty() || !s.matches("[A-Z0-9_]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Kod yalnızca harf, rakam ve alt çizgi içermeli (örn. ATISTIRMALIK veya ICE_CREAM)");
		}
		return s;
	}
}
