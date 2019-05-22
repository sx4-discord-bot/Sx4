package com.sx4.exceptions;

public class ImageProcessingException extends Exception {
	
	private static final long serialVersionUID = 1L;
	private int statusCode;

	public ImageProcessingException(int statusCode, String message) {
		super(message);
		
		this.statusCode = statusCode;
	}
	
	public int getStatusCode() {
		return this.statusCode;
	}
	
}
