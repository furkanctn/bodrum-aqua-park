package com.bodrumaquapark.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.entity.StaffUser;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.repository.StaffUserRepository;
import com.bodrumaquapark.web.dto.CreateUserRequest;
import com.bodrumaquapark.web.dto.UpdateUserRequest;
import com.bodrumaquapark.web.dto.UserResponse;

@Service
public class StaffUserService {

	private final StaffUserRepository repository;
	private final SaleAreaRepository saleAreaRepository;
	private final PasswordEncoder passwordEncoder;

	public StaffUserService(StaffUserRepository repository, SaleAreaRepository saleAreaRepository,
			PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.saleAreaRepository = saleAreaRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public List<UserResponse> listAll() {
		return repository.loadAllWithSaleAreasOrderByUserId().stream().map(UserResponse::from).toList();
	}

	public Optional<UserResponse> findByUserId(String userId) {
		return repository.loadWithSaleAreasByUserId(userId).map(UserResponse::from);
	}

	@Transactional(readOnly = true)
	public UserResponse get(Long id) {
		return repository.loadWithSaleAreasById(id).map(UserResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
	}

	@Transactional
	public UserResponse create(CreateUserRequest req) {
		String uid = req.userId().trim();
		if (repository.existsStaffUserByUserId(uid)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu sicil numarası zaten kayıtlı");
		}
		String hash = passwordEncoder.encode(req.password());
		String name = req.displayName() != null && !req.displayName().isBlank() ? req.displayName().trim() : uid;
		StaffUser u = new StaffUser(uid, hash, name, req.role());
		boolean ticket = req.ticketSalesAllowed() != null ? req.ticketSalesAllowed() : true;
		boolean balance = req.balanceLoadAllowed() != null ? req.balanceLoadAllowed() : true;
		if (req.role() == RoleCode.ADMIN) {
			ticket = true;
			balance = true;
		}
		u.setTicketSalesAllowed(ticket);
		u.setBalanceLoadAllowed(balance);
		u = repository.save(u);
		applySaleAreas(u, req.saleAreaCodes(), req.role());
		repository.save(u);
		u = repository.loadWithSaleAreasById(u.getId()).orElseThrow();
		return UserResponse.from(u);
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest req, String currentUserId) {
		StaffUser u = repository.loadWithSaleAreasById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
		if (req.password() != null && !req.password().isBlank()) {
			u.setPasswordHash(passwordEncoder.encode(req.password()));
		}
		if (req.displayName() != null) {
			u.setDisplayName(req.displayName().trim().isEmpty() ? u.getUserId() : req.displayName().trim());
		}
		if (req.active() != null) {
			if (Boolean.FALSE.equals(req.active()) && u.getUserId().equals(currentUserId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi hesabınızı devre dışı bırakamazsınız");
			}
			u.setActive(req.active());
		}
		if (req.role() != null) {
			if (u.getUserId().equals(currentUserId) && req.role() != RoleCode.ADMIN && u.getRole() == RoleCode.ADMIN) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi yönetici rolünüzü kaldıramazsınız");
			}
			u.setRole(req.role());
		}
		if (req.saleAreaCodes() != null) {
			applySaleAreas(u, req.saleAreaCodes(), u.getRole());
		}
		if (req.ticketSalesAllowed() != null) {
			u.setTicketSalesAllowed(req.ticketSalesAllowed());
		}
		if (req.balanceLoadAllowed() != null) {
			u.setBalanceLoadAllowed(req.balanceLoadAllowed());
		}
		if (u.getRole() == RoleCode.ADMIN) {
			u.setTicketSalesAllowed(true);
			u.setBalanceLoadAllowed(true);
		}
		u = repository.save(u);
		u = repository.loadWithSaleAreasById(u.getId()).orElseThrow();
		return UserResponse.from(u);
	}

	@Transactional
	public void delete(Long id, String currentUserId) {
		StaffUser u = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
		if (u.getUserId().equals(currentUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi hesabınızı silemezsiniz");
		}
		repository.delete(u);
	}

	/**
	 * Boş veya null liste: yalnızca ADMIN için tüm satış alanları atanır; diğer rollerde hata.
	 */
	private void applySaleAreas(StaffUser u, List<String> codes, RoleCode role) {
		u.getSaleAreas().clear();
		if (codes == null || codes.isEmpty()) {
			if (role == RoleCode.ADMIN) {
				saleAreaRepository.findAll().forEach(a -> u.getSaleAreas().add(a));
				return;
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En az bir satış alanı seçin");
		}
		for (String raw : codes) {
			String c = raw != null ? raw.trim() : "";
			if (c.isEmpty()) {
				continue;
			}
			SaleArea sa = saleAreaRepository.findByCode(c)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz satış alanı: " + c));
			u.getSaleAreas().add(sa);
		}
		if (u.getSaleAreas().isEmpty()) {
			if (role == RoleCode.ADMIN) {
				saleAreaRepository.findAll().forEach(a -> u.getSaleAreas().add(a));
			} else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En az bir satış alanı seçin");
			}
		}
	}
}
