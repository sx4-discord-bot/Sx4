package com.sx4.translations;

public enum TranslationType {
	
	EN_GB("en-GB", ":flag_gb:");

	private String ISOCode = null;
	private String flag = null;
	
	private TranslationType(String ISOCode, String flag) {
		this.ISOCode = ISOCode;
		this.flag = flag;
	}
	
	public String getISOCode() {
		return this.ISOCode;
	}
	
	public String getFlag() {
		return this.flag;
	}
	
	public static TranslationType getByISOCode(String ISOCode) {
		ISOCode = ISOCode.toLowerCase();
		for (TranslationType translationType : TranslationType.values()) {
			if (translationType.getISOCode().toLowerCase().equals(ISOCode)) {
				return translationType;
			}
		}
		
		return null;
	}
	
}
