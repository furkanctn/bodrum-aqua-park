package com.bodrumaquapark;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.bodrumaquapark.config.PrinterProperties;
import com.bodrumaquapark.desktop.DesktopApp;

@SpringBootApplication
@EnableConfigurationProperties(PrinterProperties.class)
public class BodrumAquaParkApiApplication {

	private static final Logger log = LoggerFactory.getLogger(BodrumAquaParkApiApplication.class);

	public static void main(String[] args) {
		if (shouldLaunchDesktop(args)) {
			DesktopApp.launch(args);
			return;
		}
		SpringApplication application = new SpringApplication(BodrumAquaParkApiApplication.class);
		application.addListeners((ApplicationReadyEvent event) -> {
			var env = event.getApplicationContext().getEnvironment();
			log.warn("Sunucu hazır. Profiller: {}. logging.file.name={}",
					String.join(",", env.getActiveProfiles()),
					env.getProperty("logging.file.name", "(yok)"));
		});
		application.run(args);
	}

	/**
	 * --desktop veya -Dlaunch.desktop=true (jpackage exe icin uygun).
	 */
	private static boolean shouldLaunchDesktop(String[] args) {
		if (Arrays.asList(args).contains("--desktop")) {
			return true;
		}
		return Boolean.parseBoolean(System.getProperty("launch.desktop", "false"));
	}

}
