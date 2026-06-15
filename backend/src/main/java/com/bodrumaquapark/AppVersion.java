package com.bodrumaquapark;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Uygulama sürümü — login ekranı ve güncelleme karşılaştırması buradan okunur.
 * Değer, "mvn package" sırasında pom.xml &lt;version&gt;'dan üretilen
 * META-INF/build-info.properties dosyasından okunur (tek kaynak: pom.xml).
 * Paketlenmemiş ortamlarda (IDE/test) bu dosya yoktur ve "dev" kullanılır.
 */
public final class AppVersion {

	public static final String VERSION = readBuildVersion().orElse("dev");

	private static Optional<String> readBuildVersion() {
		try (InputStream in = AppVersion.class.getResourceAsStream("/META-INF/build-info.properties")) {
			if (in == null) {
				return Optional.empty();
			}
			Properties props = new Properties();
			props.load(in);
			String version = props.getProperty("build.version", "").trim();
			return version.isEmpty() ? Optional.empty() : Optional.of(version);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private AppVersion() {
	}
}
