package com.sx4.database;

public interface DatabaseCallback<Type> {

	public void onResult(Type result, Throwable throwable);
	
}
