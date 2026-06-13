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
		String uid = "04A1B2C3";
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
				.content("{\"cardUid\":\"" + uid + "\",\"productId\":" + productId + ",\"saleAreaCode\":\"BEVERAGE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").exists()).andExpect(jsonPath("$.balanceAfter").exists());
	}

	@Test
	void ticketEntryGrant_thenTurnstile_usesGateWithoutBalance() throws Exception {
		String uid = "04DEAD01";
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

	@Test
	void balanceLoad_rejectsMixedPaymentMethodAfterFirstCashLoad() throws Exception {
		String uid = "BAL-LOCK-CASH-001";
		mockMvc.perform(post("/api/cards/" + uid + "/balance-load").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":50,\"paymentMethod\":\"cash\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/cards/" + uid + "/balance-load").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":25,\"paymentMethod\":\"card\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void agencyComplimentaryTicketGrant_recordsZeroAmountAndTurnstileGate() throws Exception {
		String uid = "04AGENCY01";
		long adultId = findAgencyTicketAgeGroupId("Acenta Yetişkin");
		long childId = findAgencyTicketAgeGroupId("Acenta Çocuk");
		mockMvc.perform(post("/api/cards/" + uid + "/ticket-entry-grant").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":0,\"paymentMethod\":\"credit\",\"lines\":["
						+ "{\"ticketAgeGroupId\":" + adultId + ",\"quantity\":2},"
						+ "{\"ticketAgeGroupId\":" + childId + ",\"quantity\":1}"
						+ "]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entryGate").value(1));

		mockMvc.perform(get("/api/admin/reports/payment-sales?from=2020-01-01&to=2099-12-31")
				.header(HttpHeaders.AUTHORIZATION, bearerAuth()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.agencyTicketTotalCount").value(3))
				.andExpect(jsonPath("$.agencyTicketCounts[0].name").value("Acenta Yetişkin"))
				.andExpect(jsonPath("$.agencyTicketCounts[0].count").value(2))
				.andExpect(jsonPath("$.agencyTicketCounts[1].name").value("Acenta Çocuk"))
				.andExpect(jsonPath("$.agencyTicketCounts[1].count").value(1));
	}

	private long findAgencyTicketAgeGroupId(String name) throws Exception {
		MvcResult res = mockMvc.perform(get("/api/admin/ticket-age-groups").header(HttpHeaders.AUTHORIZATION, bearerAuth()))
				.andExpect(status().isOk()).andReturn();
		JsonNode list = objectMapper.readTree(res.getResponse().getContentAsString());
		for (JsonNode node : list) {
			if (name.equals(node.path("name").asText())) {
				return node.path("id").asLong();
			}
		}
		throw new IllegalStateException("Ticket age group not found: " + name);
	}

	@Test
	void balanceLoad_rejectsMixedPaymentMethodAfterFirstCardLoad() throws Exception {
		String uid = "BAL-LOCK-CARD-001";
		mockMvc.perform(post("/api/cards/" + uid + "/balance-load").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":40,\"paymentMethod\":\"card\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/cards/" + uid + "/balance-load").header(HttpHeaders.AUTHORIZATION, bearerAuth())
				.contentType(APPLICATION_JSON).content("{\"amount\":10,\"paymentMethod\":\"cash\"}"))
				.andExpect(status().isConflict());
	}
}
