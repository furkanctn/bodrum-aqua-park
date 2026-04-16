package com.bodrumaquapark.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.repository.TicketAgeGroupRepository;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.web.dto.TicketAgeGroupResponse;

@RestController
public class TicketAgeGroupPosController {

	private final TicketAgeGroupRepository repository;

	public TicketAgeGroupPosController(TicketAgeGroupRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/api/ticket-age-groups")
	public List<TicketAgeGroupResponse> listForPos(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_TICKET_SALES_ALLOWED) boolean ticketSalesAllowed) {
		if (!ticketSalesAllowed) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bilet satışı bu kullanıcı için kapalı");
		}
		return repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(TicketAgeGroupResponse::from).toList();
	}
}
