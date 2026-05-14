package com.bodrumaquapark.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AccessControlService;
import com.bodrumaquapark.web.dto.AssignPassRequest;
import com.bodrumaquapark.web.dto.AssignPassResponse;
import com.bodrumaquapark.web.dto.RfidCardStatusResponse;

import jakarta.validation.Valid;

/**
 * RFID / günlük pas satışı. Mevcut cüzdan kartı ucu {@code GET /api/cards/{uid}} ile çakışmaması için
 * pas durumu {@code GET /api/cards/access/{cardId}} altında sunulur.
 */
@RestController
@RequestMapping("/api/cards")
public class RfidPassCardController {

	private final AccessControlService accessControlService;

	public RfidPassCardController(AccessControlService accessControlService) {
		this.accessControlService = accessControlService;
	}

	@PostMapping("/assign-pass")
	public AssignPassResponse assignPass(
			@Valid @RequestBody AssignPassRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String operatorUserId) {
		return accessControlService.assignPass(operatorUserId, request);
	}

	@GetMapping("/access/{cardId}")
	public RfidCardStatusResponse getAccessCard(@PathVariable("cardId") String cardId) {
		return accessControlService.getCardStatus(cardId);
	}
}
