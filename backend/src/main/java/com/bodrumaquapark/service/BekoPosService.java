package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bodrumaquapark.config.BekoPosProperties;
import com.bodrumaquapark.web.dto.BekoPosSendBasketRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BekoPosService {

	private static final Logger log = LoggerFactory.getLogger(BekoPosService.class);

	private static final int TOKEN_QTY_UNIT = 1000;

	private final BekoPosProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public BekoPosService(BekoPosProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = buildRestClient(properties);
	}

	public Map<String, Object> statusForDisplay() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("enabled", properties.isEnabled());
		out.put("baseUrl", properties.getBaseUrl());
		out.put("sendBasketPath", properties.getSendBasketPath());
		if (!properties.isEnabled()) {
			out.put("reachable", false);
			out.put("message", "Beko POS entegrasyonu kapalı (app.beko-pos.enabled=false)");
			return out;
		}
		String base = normalizeBaseUrl(properties.getBaseUrl());
		if (base.isEmpty()) {
			out.put("reachable", false);
			out.put("message", "app.beko-pos.base-url tanımlı değil");
			return out;
		}
		String statusUrl = joinUrl(base, properties.getStatusPath());
		try {
			var response = restClient.get()
					.uri(URI.create(statusUrl))
					.retrieve()
					.toEntity(String.class);
			out.put("reachable", response.getStatusCode().is2xxSuccessful());
			out.put("message", response.getStatusCode().is2xxSuccessful() ? "Bağlı" : "Yanıt hatası");
			out.put("statusUrl", statusUrl);
			if (response.getBody() != null && !response.getBody().isBlank()) {
				out.put("deviceInfoPreview", truncate(response.getBody(), 240));
			}
		} catch (RestClientException e) {
			out.put("reachable", false);
			out.put("message", friendlyError(e));
			out.put("statusUrl", statusUrl);
			log.debug("Beko POS durum kontrolü başarısız: {}", e.toString());
		}
		return out;
	}

	public Map<String, Object> sendBasket(BekoPosSendBasketRequest request) {
		if (!properties.isEnabled()) {
			return error("Beko POS entegrasyonu kapalı");
		}
		String base = normalizeBaseUrl(properties.getBaseUrl());
		if (base.isEmpty()) {
			return error("app.beko-pos.base-url tanımlı değil");
		}
		if (request == null || request.items() == null || request.items().isEmpty()) {
			return error("Sepet satırı gerekli");
		}

		Map<String, Object> basket = buildTokenBasket(request);
		String url = joinUrl(base, properties.getSendBasketPath());
		try {
			var response = restClient.post()
					.uri(URI.create(url))
					.contentType(MediaType.APPLICATION_JSON)
					.body(basket)
					.retrieve()
					.toEntity(String.class);

			Map<String, Object> out = new LinkedHashMap<>();
			out.put("ok", true);
			out.put("message", "Sepet Beko POS cihazına gönderildi");
			out.put("basketId", basket.get("basketID"));
			out.put("endpoint", url);
			out.put("httpStatus", response.getStatusCode().value());

			Map<String, Object> parsed = parseJsonBody(response.getBody());
			if (!parsed.isEmpty()) {
				out.put("deviceResponse", parsed);
				Integer deviceStatus = readInt(parsed.get("status"));
				if (deviceStatus != null) {
					out.put("deviceStatus", deviceStatus);
					if (deviceStatus == 0) {
						out.put("message", "Ödeme tamamlandı · fiş kesildi");
					} else if (deviceStatus == 99) {
						out.put("ok", false);
						out.put("message", "Fiş iptal edildi");
					} else if (deviceStatus == -1) {
						out.put("ok", false);
						out.put("message", stringOr(parsed.get("message"), "POS ödemesi başarısız"));
					}
				}
				Object msg = parsed.get("message");
				if (msg != null && out.get("message").equals("Sepet Beko POS cihazına gönderildi")) {
					out.put("message", String.valueOf(msg));
				}
			}
			return out;
		} catch (RestClientResponseException e) {
			log.warn("Beko POS sendBasket HTTP {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 400));
			return error("POS yanıt hatası (" + e.getStatusCode().value() + "): "
					+ truncate(e.getResponseBodyAsString(), 200));
		} catch (RestClientException e) {
			log.warn("Beko POS sendBasket bağlantı hatası: {}", e.toString());
			return error(friendlyError(e));
		}
	}

	private Map<String, Object> buildTokenBasket(BekoPosSendBasketRequest request) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (BekoPosSendBasketRequest.Line line : request.items()) {
			if (line == null) {
				continue;
			}
			String name = line.name() != null && !line.name().isBlank()
					? line.name().trim()
					: properties.getDefaultItemName();
			int priceKurus = toKurus(line.unitPrice());
			int qty = line.quantity() != null && line.quantity() > 0 ? line.quantity() : 1;
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("barcode", "");
			item.put("name", name);
			item.put("pluNo", 0);
			item.put("price", priceKurus);
			item.put("sectionNo", properties.getSectionNo());
			item.put("taxPercent", properties.getTaxPercent());
			item.put("type", 0);
			item.put("unit", "Adet");
			item.put("quantity", qty * TOKEN_QTY_UNIT);
			items.add(item);
		}
		if (items.isEmpty()) {
			throw new IllegalArgumentException("Geçerli sepet satırı yok");
		}

		Map<String, Object> basket = new LinkedHashMap<>();
		basket.put("basketID", UUID.randomUUID().toString());
		basket.put("createInvoice", false);
		basket.put("documentType", 0);
		basket.put("isVoid", false);
		basket.put("items", items);

		if (request.note() != null && !request.note().isBlank()) {
			basket.put("note", request.note().trim());
		}

		if (request.discountPercent() != null && request.discountPercent() > 0) {
			Map<String, Object> adjust = new LinkedHashMap<>();
			adjust.put("description", "%" + request.discountPercent() + " indirim");
			adjust.put("discountOrSurcharge", 0);
			adjust.put("type", 1);
			adjust.put("value", request.discountPercent() * 100);
			basket.put("adjust", adjust);
		}

		Integer paymentType = mapPaymentType(request.paymentMethod());
		if (paymentType != null) {
			int totalKurus = items.stream().mapToInt(it -> {
				int price = (Integer) it.get("price");
				int q = (Integer) it.get("quantity");
				return price * q / TOKEN_QTY_UNIT;
			}).sum();
			if (request.discountPercent() != null && request.discountPercent() > 0) {
				totalKurus = totalKurus - (totalKurus * request.discountPercent() / 100);
			}
			if (totalKurus > 0) {
				Map<String, Object> pay = new LinkedHashMap<>();
				pay.put("amount", totalKurus);
				pay.put("type", paymentType);
				basket.put("paymentItems", List.of(pay));
			}
		}

		return basket;
	}

	private static Integer mapPaymentType(String paymentMethod) {
		if (paymentMethod == null || paymentMethod.isBlank()) {
			return null;
		}
		return switch (paymentMethod.trim().toLowerCase()) {
			case "cash", "rate" -> 1;
			case "card" -> 3;
			default -> null;
		};
	}

	private static int toKurus(BigDecimal tl) {
		if (tl == null) {
			return 0;
		}
		return tl.multiply(BigDecimal.valueOf(100))
				.setScale(0, RoundingMode.HALF_UP)
				.intValue();
	}

	private Map<String, Object> parseJsonBody(String body) {
		if (body == null || body.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return Map.of("raw", truncate(body, 500));
		}
	}

	private static RestClient buildRestClient(BekoPosProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())));
		factory.setReadTimeout(Duration.ofMillis(Math.max(5000, properties.getReadTimeoutMs())));
		return RestClient.builder().requestFactory(factory).build();
	}

	private static String normalizeBaseUrl(String base) {
		if (base == null) {
			return "";
		}
		String t = base.trim();
		while (t.endsWith("/")) {
			t = t.substring(0, t.length() - 1);
		}
		return t;
	}

	private static String joinUrl(String base, String path) {
		if (path == null || path.isBlank()) {
			return base;
		}
		String p = path.startsWith("/") ? path : "/" + path;
		return base + p;
	}

	private static Map<String, Object> error(String message) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("ok", false);
		out.put("error", message);
		return out;
	}

	private static String friendlyError(Exception e) {
		String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
		if (msg.contains("Connection refused") || msg.contains("connect timed out")) {
			return "Beko servisine ulaşılamadı. BekoOkcServisi çalışıyor mu? (TokenX Connect, port 9001)";
		}
		return msg;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	private static Integer readInt(Object v) {
		if (v == null) {
			return null;
		}
		if (v instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(v));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String stringOr(Object v, String fallback) {
		return v != null ? String.valueOf(v) : fallback;
	}
}
