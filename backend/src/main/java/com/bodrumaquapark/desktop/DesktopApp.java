package com.bodrumaquapark.desktop;

import java.io.InputStream;
import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import com.bodrumaquapark.BodrumAquaParkApiApplication;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Chrome/Edge olmadan POS: JavaFX WebView ile yerel pencerede http://127.0.0.1:8081 açılır.
 * Başlatma: {@code java -jar ... --desktop}
 */
public class DesktopApp extends Application {

	private static String[] springArgs = new String[0];
	private static volatile ConfigurableApplicationContext springContext;

	public static void launch(String[] args) {
		springArgs = Arrays.stream(args).filter(a -> !"--desktop".equals(a)).toArray(String[]::new);
		Application.launch(DesktopApp.class, args);
	}

	static String resolveFrontendBaseUrl(ConfigurableApplicationContext ctx) {
		Environment env = ctx.getEnvironment();
		String explicit = env.getProperty("aquapark.frontendBaseUrl");
		if (explicit != null && !explicit.isBlank()) {
			return explicit.trim().replaceAll("/$", "");
		}
		int port = 8081;
		if (ctx instanceof WebServerApplicationContext wac) {
			port = wac.getWebServer().getPort();
		} else {
			Integer p = env.getProperty("server.port", Integer.class);
			if (p != null) {
				port = p;
			}
		}
		String host = env.getProperty("aquapark.desktop.host", "127.0.0.1").trim();
		if (host.isBlank()) {
			host = "127.0.0.1";
		}
		return "http://" + host + ":" + port;
	}

	private static void applyStageIcon(Stage stage) {
		try (InputStream is = DesktopApp.class.getResourceAsStream("/desktop/icon.png")) {
			if (is != null) {
				stage.getIcons().add(new Image(is));
			}
		} catch (Exception ignored) {
		}
	}

	@Override
	public void start(Stage stage) {
		stage.setTitle("Bodrum Aqua Park — Kasa");
		applyStageIcon(stage);
		StackPane root = new StackPane(new Label("Sunucu başlatılıyor…"));
		stage.setScene(new Scene(root, 1280, 800));
		// Tam ekran: dokunmatikte ekran klavyesinden ESC ile çıkılabilsin (varsayılan davranış).
		stage.setFullScreenExitHint("Çıkmak için: ekran klavyesinde ESC veya POS alttaki «Pencere»");
		stage.setFullScreen(true);
		stage.show();

		Thread springThread = new Thread(() -> {
			SpringApplication app = new SpringApplication(BodrumAquaParkApiApplication.class);
			app.setHeadless(false);
			app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> Platform.runLater(() -> {
				WebView webView = new WebView();
				String base = resolveFrontendBaseUrl(event.getApplicationContext());
				// pos.html acilirsa token yokken index'e yonlenir: cift yukleme ve donma. Dogrudan giris.
				webView.getEngine().load(base + "/index.html?posPerf=1");
				stage.setScene(new Scene(webView, 1280, 800));
				stage.setFullScreen(true);
				applyStageIcon(stage);
			}));
			app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> Platform.runLater(() -> {
				Throwable t = event.getException();
				String msg = t != null ? t.getMessage() : "Bilinmeyen hata";
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.setTitle("Sunucu başlatılamadı");
				alert.setHeaderText("Spring Boot başlatılamadı");
				alert.setContentText(msg != null ? msg : "");
				alert.showAndWait();
				Platform.exit();
			}));
			springContext = app.run(springArgs);
		});
		springThread.setName("spring-boot");
		springThread.setDaemon(false);
		springThread.start();
	}

	@Override
	public void stop() {
		if (springContext != null) {
			SpringApplication.exit(springContext, () -> 0);
		}
	}
}
