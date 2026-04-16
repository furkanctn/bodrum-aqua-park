package com.bodrumaquapark.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * POS tek sayfa kabuğu; her modül ayrı URL ile açılır, yanıt her zaman pos.html içeriğidir.
 */
@Controller
public class PosPageController {

	@GetMapping({ "/pos", "/pos/", "/pos/kart", "/pos/bakiye", "/pos/urun", "/pos/sorgu" })
	public String posShell() {
		return "forward:/pos.html";
	}

	@GetMapping("/admin")
	public String adminAlias() {
		return "forward:/admin.html";
	}
}
