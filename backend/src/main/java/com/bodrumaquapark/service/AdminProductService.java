package com.bodrumaquapark.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.util.Money;
import com.bodrumaquapark.web.dto.CreateProductRequest;
import com.bodrumaquapark.web.dto.ProductResponse;
import com.bodrumaquapark.web.dto.UpdateProductRequest;

@Service
public class AdminProductService {

	private final ProductRepository productRepository;
	private final MenuPageRepository menuPageRepository;

	public AdminProductService(ProductRepository productRepository, MenuPageRepository menuPageRepository) {
		this.productRepository = productRepository;
		this.menuPageRepository = menuPageRepository;
	}

	@Transactional(readOnly = true)
	public List<ProductResponse> listAll() {
		return productRepository.findAllForAdmin().stream().map(ProductResponse::from).toList();
	}

	@Transactional
	public ProductResponse create(CreateProductRequest req) {
		MenuPage mp = menuPageRepository.findById(req.menuPageId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menü sayfası bulunamadı"));
		Product p = new Product(mp, req.name().trim(), Money.normalize(req.price()), req.stockQuantity());
		p = productRepository.save(p);
		return ProductResponse.from(productRepository.findById(p.getId()).orElseThrow());
	}

	@Transactional
	public ProductResponse update(Long id, UpdateProductRequest req) {
		Product p = productRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
		if (req.name() != null) {
			String n = req.name().trim();
			if (n.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün adı boş olamaz");
			}
			p.setName(n);
		}
		if (req.price() != null) {
			p.setPrice(Money.normalize(req.price()));
		}
		if (req.stockQuantity() != null) {
			p.setStockQuantity(req.stockQuantity());
		}
		if (req.active() != null) {
			p.setActive(req.active());
		}
		if (req.menuPageId() != null) {
			MenuPage mp = menuPageRepository.findById(req.menuPageId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menü sayfası bulunamadı"));
			p.setMenuPage(mp);
		}
		productRepository.save(p);
		return ProductResponse.from(productRepository.findById(id).orElseThrow());
	}

	@Transactional
	public void softDelete(Long id) {
		Product p = productRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
		p.setActive(false);
	}
}
