package com.sx4.bot.utility;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class HmacUtility {
	
	public static final String HMAC_MD5 = "HmacMD5";
	public static final String HMAC_SHA256 = "HmacSHA256";
	public static final String HMAC_SHA1 = "HmacSHA1";

	public static String toHexString(byte[] bytes) {
		try (Formatter formatter = new Formatter()) {
			for (byte b : bytes) {
				formatter.format("%02x", b);
			}

			return formatter.toString();
		}
	}
	
	public static String getSignatureHex(String key, String data, String type) throws NoSuchAlgorithmException, InvalidKeyException {
		SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), type);
		
		Mac mac = Mac.getInstance(type);
		mac.init(signingKey);
		
		return HmacUtility.toHexString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
	}

	public static byte[] getSignature(String key, String data, String type) throws NoSuchAlgorithmException, InvalidKeyException {
		SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), type);

		Mac mac = Mac.getInstance(type);
		mac.init(signingKey);

		return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
	}
	
}
