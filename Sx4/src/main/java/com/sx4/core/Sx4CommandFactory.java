package com.sx4.core;

import java.lang.reflect.Method;

import com.jockie.bot.core.command.factory.IMethodCommandFactory;

public class Sx4CommandFactory implements IMethodCommandFactory<Sx4Command> {
	
	public Sx4Command create(Method method, String name, Object invoker) {
		return new Sx4Command(IMethodCommandFactory.getName(name, method), method, invoker);
	}
	
}
