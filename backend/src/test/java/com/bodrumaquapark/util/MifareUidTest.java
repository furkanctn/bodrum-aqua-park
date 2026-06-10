package com.bodrumaquapark.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MifareUidTest {

	@Test
	void parse_fourByteHex_canonicalizesToPaddedUppercase() {
		var parsed = MifareUid.parse("4a1b2c3", true, false).orElseThrow();
		assertEquals("04A1B2C3", parsed.canonical());
		assertTrue(parsed.lookupKeys().contains("04A1B2C3"));
	}

	@Test
	void parse_decimalLegacy_mapsToSameCanonical() {
		var hex = MifareUid.parse("04A1B2C3", true, false).orElseThrow();
		var dec = MifareUid.parse("77705923", true, false).orElseThrow();
		assertEquals(hex.canonical(), dec.canonical());
		assertTrue(dec.lookupKeys().contains("77705923"));
	}

	@Test
	void parse_reversedBytes_includedInLookupKeys() {
		var parsed = MifareUid.parse("04A1B2C3", true, true).orElseThrow();
		assertTrue(parsed.lookupKeys().contains("C3B2A104"));
	}

	@Test
	void parse_sevenByteUid_padsTo14Chars() {
		var parsed = MifareUid.parse("04E5F6A1B2C3D4", true, false).orElseThrow();
		assertEquals("04E5F6A1B2C3D4", parsed.canonical());
		assertEquals(14, parsed.canonical().length());
	}

	@Test
	void parse_invalidRejected() {
		assertFalse(MifareUid.parse("ZZZZ", true, true).isPresent());
		assertFalse(MifareUid.parse("ABCDEFGH", true, true).isPresent());
	}

	@Test
	void mask_hidesMiddle() {
		assertEquals("04…C3", MifareUid.mask("04A1B2C3"));
	}
}
