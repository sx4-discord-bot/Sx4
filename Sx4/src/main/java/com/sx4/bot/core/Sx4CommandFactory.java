package com.sx4.bot.core;

import java.lang.reflect.Method;

import com.jockie.bot.core.command.factory.IMethodCommandFactory;
import org.jetbrains.annotations.NotNull;

public class Sx4CommandFactory implements IMethodCommandFactory<Sx4Command> {
	
	public @NotNull Sx4Command create(@NotNull Method method, String name, Object invoker) {
		return new Sx4Command(IMethodCommandFactory.getName(name, method), method, invoker);
	}
	
}