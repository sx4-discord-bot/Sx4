package com.sx4.bot.formatter.output.function;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;

public class FormatterFunction<Type> {

	private final Class<Type> type;
	private final String name, description;
	private final Method method;
	private final FormatterArgument[] arguments;

	public FormatterFunction(Class<Type> type, String name, String description) {
		this.name = name;
		this.type = type;
		this.description = description;

		Method[] methods = this.getClass().getMethods();

		for (Method method : methods) {
			if (method.getName().equalsIgnoreCase("parse")) {
				boolean optional = false, event = false;
				Parameter[] parameters = method.getParameters();
				for (int i = 0; i < parameters.length; i++) {
					Class<?> clazz = parameters[i].getType();
					if (clazz == FormatterEvent.class) {
						if (i != 0) {
							throw new IllegalArgumentException("FormatterEvent must be the first parameter in the method");
						}

						event = true;
					} else if (clazz == Optional.class) {
						optional = true;
					} else if (optional) {
						throw new IllegalArgumentException("Cannot have a required parameter after an optional parameter");
					}
				}

				if (event) {
					this.method = method;

					this.arguments = new FormatterArgument[parameters.length - 1];
					for (int i = 1; i < parameters.length; i++) {
						this.arguments[i - 1] = new FormatterArgument(parameters[i]);
					}

					return;
				} else {
					throw new IllegalArgumentException("parse method should have a FormatterEvent parameter");
				}
			}
		}

		throw new IllegalStateException("FormatterFunction doesn't have a parse method");
	}

	public String getDescription() {
		return this.description;
	}

	public Class<Type> getType() {
		return this.type;
	}

	public String getName() {
		return this.name;
	}

	public FormatterArgument[] getArguments() {
		return this.arguments;
	}

	public Method getMethod() {
		return this.method;
	}

	public Object parse(Object... arguments) throws InvocationTargetException, IllegalAccessException {
		return this.method.invoke(this, arguments);
	}

	public Object parse(List<Object> arguments) throws InvocationTargetException, IllegalAccessException {
		return this.parse(arguments.toArray());
	}

}
