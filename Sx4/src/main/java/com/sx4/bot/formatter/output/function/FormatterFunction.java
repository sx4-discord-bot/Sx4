package com.sx4.bot.formatter.output.function;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class FormatterFunction<Type> {

	private final Class<Type> type;
	private final String name, description;
	private final Method method;
	private final boolean usePrevious;

	public FormatterFunction(Class<Type> type, String name, String description, boolean usePrevious) {
		this.name = name;
		this.type = type;
		this.description = description;
		this.usePrevious = usePrevious;

		Method[] methods = this.getClass().getMethods();

		for (Method method : methods) {
			if (method.getName().equalsIgnoreCase("parse")) {
				boolean optional = false, event = false;
				Class<?>[] parameters = method.getParameterTypes();
				for (int i = 0; i < parameters.length; i++) {
					Class<?> clazz = parameters[i];
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
					return;
				} else {
					throw new IllegalArgumentException("parse method should have a FormatterEvent parameter");
				}
			}
		}

		throw new IllegalStateException("FormatterFunction doesn't have a parse method");
	}

	public FormatterFunction(Class<Type> type, String name, String description) {
		this(type, name, description, false);
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isUsePrevious() {
		return this.usePrevious;
	}

	public Class<Type> getType() {
		return this.type;
	}

	public String getName() {
		return this.name;
	}

	public Method getMethod() {
		return this.method;
	}

	public Object parse(List<Object> arguments) throws InvocationTargetException, IllegalAccessException {
		return this.method.invoke(this, arguments.toArray());
	}

}
