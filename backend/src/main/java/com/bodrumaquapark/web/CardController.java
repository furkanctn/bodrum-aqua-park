package com.bodrumaquapark.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.CardService;
import com.bodrumaquapark.web.dto.BalanceLoadRequest;
import com.bodrumaquapark.web.dto.CardDetailResponse;
import com.bodrumaquapark.web.dto.CardResponse;
import com.bodrumaquapark.web.dto.IssueCardRequest;
import com.bodrumaquapark.web.dto.TicketGrantRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cards")
public class CardController {

	private final CardService cardService;

	public CardController(CardService cardService) {
		this.cardService = cardService;
	}

	@PostMapping
	public ResponseEntity<CardResponse> issue(@Valid @RequestBody IssueCardRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(cardService.issueCard(request.uid(),
				request.initialBalance())));
	}

	@GetMapping("/{uid}")
	public CardResponse get(@PathVariable("uid") String uid) {
		return CardResponse.from(cardService.getByUid(uid));
	}

	@GetMapping("/{uid}/detail")
	public CardDetailResponse getDetail(@PathVariable("uid") String uid) {
		return cardService.getCardDetail(uid);
	}

	/** Bilet satışı sonrası kartta giriş hakkı (1) + deftere tahsilat (ödeme yöntemi) */
	@PostMapping("/{uid}/ticket-entry-grant")
	public ResponseEntity<CardResponse> grantTicketEntry(
			@PathVariable("uid") String uid,
			@Valid @RequestBody TicketGrantRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String operatorUserId) {
		return ResponseEntity.ok(CardResponse.from(
				cardService.grantTicketEntry(uid, operatorUserId, request.paymentMethod(), request.amount())));
	}

	/** POS bakiye yükleme — yetki: balanceLoadAllowed */
	@PostMapping("/{uid}/balance-load")
	public ResponseEntity<CardResponse> loadBalance(
			@PathVariable("uid") String uid,
			@Valid @RequestBody BalanceLoadRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String operatorUserId,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_BALANCE_LOAD_ALLOWED) boolean balanceLoadAllowed) {
		if (!balanceLoadAllowed) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bakiye yükleme yetkisi yok");
		}
		return ResponseEntity.ok(
				CardResponse.from(cardService.loadBalance(uid, request.amount(), request.paymentMethod(), operatorUserId)));
	}
}
