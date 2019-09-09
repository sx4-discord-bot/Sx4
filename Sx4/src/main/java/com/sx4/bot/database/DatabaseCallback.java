package com.sx4.bot.database;

public interface DatabaseCallback<Type> {

	public void onResult(Type result, Throwable throwable);
	
}
