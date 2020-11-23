package com.sx4.bot.entities.image;

public enum ImageError {

	INVALID_URL(0),
	INVALID_IMAGE_URL(1),
	URL_TIMEOUT(2),
	INVALID_BODY_JSON(3),
	FIELD_MISSING(4),
	INVALID_FIELD_VALUE(5),
	QUERY_MISSING(6),
	INVALID_QUERY_VALUE(7);

	private final int code;

	private ImageError(int code) {
		this.code = code;
	}

	public int getCode() {
		return this.code;
	}

	public boolean isUrlError() {
		return this.code == 0 || this.code == 1 || this.code == 2;
	}

	public static ImageError fromCode(int code) {
		for (ImageError error : ImageError.values()) {
			if (error.getCode() == code) {
				return error;
			}
		}

		return null;
	}

}
