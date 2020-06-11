package com.sx4.bot.utility;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacUtility {
	
	public static final String HMAC_MD5 = "HmacMD5";

	@SuppressWarnings("resource")
	public static String toHexString(byte[] bytes) {
		Formatter formatter = new Formatter();
		
		for (byte b : bytes) {
			formatter.format("%02x", b);
		}

		return formatter.toString();
	}
	
	public static String getMD5Signature(String key, String data) throws NoSuchAlgorithmException, InvalidKeyException {
		SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HmacUtility.HMAC_MD5);
		
		Mac mac = Mac.getInstance(HmacUtility.HMAC_MD5);
		mac.init(signingKey);
		
		return HmacUtility.toHexString(mac.doFinal(data.getBytes()));
	}
	
}
