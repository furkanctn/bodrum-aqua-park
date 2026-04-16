package com.bodrumaquapark.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.TicketAgeGroup;
import com.bodrumaquapark.repository.TicketAgeGroupRepository;
import com.bodrumaquapark.web.dto.TicketAgeGroupCreateRequest;
import com.bodrumaquapark.web.dto.TicketAgeGroupResponse;
import com.bodrumaquapark.web.dto.TicketAgeGroupUpdateRequest;

@Service
public class AdminTicketAgeGroupService {

	private final TicketAgeGroupRepository repository;

	public AdminTicketAgeGroupService(TicketAgeGroupRepository repository) {
		this.repository = repository;
	}

	public List<TicketAgeGroupResponse> listAll() {
		return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(TicketAgeGroupResponse::from).toList();
	}

	@Transactional
	public TicketAgeGroupResponse create(TicketAgeGroupCreateRequest req) {
		int sort = req.sortOrder() != null ? req.sortOrder() : nextSortOrder();
		boolean active = req.active() == null || Boolean.TRUE.equals(req.active());
		TicketAgeGroup e = new TicketAgeGroup(req.name().trim(), req.price(), sort, active);
		return TicketAgeGroupResponse.from(repository.save(e));
	}

	@Transactional
	public TicketAgeGroupResponse update(long id, TicketAgeGroupUpdateRequest req) {
		TicketAgeGroup e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kayıt bulunamadı"));
		e.setName(req.name().trim());
		e.setPrice(req.price());
		e.setSortOrder(req.sortOrder());
		e.setActive(req.active());
		return TicketAgeGroupResponse.from(repository.save(e));
	}

	@Transactional
	public void delete(long id) {
		if (!repository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kayıt bulunamadı");
		}
		repository.deleteById(id);
	}

	private int nextSortOrder() {
		return repository.findAllByOrderBySortOrderAscIdAsc().stream().mapToInt(TicketAgeGroup::getSortOrder).max()
				.orElse(0) + 10;
	}
}
