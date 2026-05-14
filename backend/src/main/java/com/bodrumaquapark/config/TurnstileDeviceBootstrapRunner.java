package com.bodrumaquapark.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.bodrumaquapark.entity.TurnstileDevice;
import com.bodrumaquapark.repository.TurnstileDeviceRepository;

/**
 * İsteğe bağlı: {@code app.access.bootstrap.device-id} ve {@code app.access.bootstrap.device-token}
 * doluysa ilk turnike cihaz kaydını oluşturur.
 */
@Component
public class TurnstileDeviceBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TurnstileDeviceBootstrapRunner.class);

	private final AccessBootstrapProperties properties;
	private final TurnstileDeviceRepository turnstileDeviceRepository;
	private final PasswordEncoder passwordEncoder;

	public TurnstileDeviceBootstrapRunner(
			AccessBootstrapProperties properties,
			TurnstileDeviceRepository turnstileDeviceRepository,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.turnstileDeviceRepository = turnstileDeviceRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		String id = properties.getDeviceId() != null ? properties.getDeviceId().trim() : "";
		String token = properties.getDeviceToken() != null ? properties.getDeviceToken().trim() : "";
		if (id.isEmpty() || token.isEmpty()) {
			return;
		}
		if (turnstileDeviceRepository.findByDeviceId(id).isPresent()) {
			return;
		}
		String hash = passwordEncoder.encode(token);
		turnstileDeviceRepository.save(new TurnstileDevice(id, hash, "Bootstrap cihazı", true));
		log.warn(
				"Turnike cihazı otomatik oluşturuldu: {}. Üretimde app.access.bootstrap.* değerlerini kapatın ve token'ı güvenli şekilde yönetin.",
				id);
	}
}
