package com.bodrumaquapark;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TurnstileSaleIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	private String bearerAuth() {
		String token = jwtService.createToken("integration-test", RoleCode.ADMIN,
				List.of("BEVERAGE", "BAKERY", "ALCOHOL", "ICE_CREAM"), true, true, true);
		return "Bearer " + token;
	}

	@Test
	void issueCard_turnstile_thenSale() throws Exception {
		String uid = "INT-TEST-001";
		mockMvc.perform(post("/api/cards").header(HttpHeaders.AUTHORIZATION, bearerAuth()).contentType(APPLICATION_JSON)
				.content("{\"uid\":\"" + uid + "\",\"initialBalance\":500}")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.balance").value(500));

		mockMvc.perform(post("/api/turnstile/scan").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"cardUid\":\"" + uid + "\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.code").value("ALLOWED"))
				.andExpect(jsonPath("$.balanceAfter").value(450));

		MvcResult catalog = mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.param("saleAreaCode", "BEVERAGE")).andExpect(status().isOk()).andReturn();
		JsonNode products = objectMapper.readTree(catalog.getResponse().getContentAsString());
		long productId = products.get(0).get("id").asLong();

		mockMvc.perform(post("/api/sales").header(HttpHeaders.AUTHORIZATION, bearerAuth()).contentType(APPLICATION_JSON)
				.content("{\"cardUid\":\"" + uid + "\",\"productId\":" + productId + "}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").exists()).andExpect(jsonPath("$.balanceAfter").exists());
	}

	@Test
	void ticketEntryGrant_thenTurnstile_usesGateWithoutBalance() throws Exception {
		String uid = "RFID-TICKET-001";
		mockMvc.perform(post("/api/cards/" + uid + "/ticket-entry-grant").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":1.00,\"paymentMethod\":\"cash\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entryGate").value(1));

		mockMvc.perform(post("/api/turnstile/scan").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"cardUid\":\"" + uid + "\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.code").value("ALLOWED"));

		mockMvc.perform(get("/api/cards/" + uid).header(HttpHeaders.AUTHORIZATION, bearerAuth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entryGate").value(0));
	}

	@Test
	void balanceLoad_cash_increasesCardBalance() throws Exception {
		String uid = "BAL-LOAD-INT-001";
		mockMvc.perform(post("/api/cards/" + uid + "/balance-load").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":100.50,\"paymentMethod\":\"cash\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.uid").value(uid))
				.andExpect(jsonPath("$.balance").value(100.5));

		mockMvc.perform(get("/api/cards/" + uid).header(HttpHeaders.AUTHORIZATION, bearerAuth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100.5));
	}
}
