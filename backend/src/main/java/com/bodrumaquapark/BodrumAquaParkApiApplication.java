package com.bodrumaquapark;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.bodrumaquapark.config.PrinterProperties;
import com.bodrumaquapark.desktop.DesktopApp;

@SpringBootApplication
@EnableConfigurationProperties(PrinterProperties.class)
public class BodrumAquaParkApiApplication {

	public static void main(String[] args) {
		if (shouldLaunchDesktop(args)) {
			DesktopApp.launch(args);
			return;
		}
		SpringApplication.run(BodrumAquaParkApiApplication.class, args);
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
