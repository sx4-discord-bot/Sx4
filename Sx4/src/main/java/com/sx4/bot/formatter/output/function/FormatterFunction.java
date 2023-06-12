package com.sx4.bot.formatter.output.function;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

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
				for (Class<?> parameter : method.getParameterTypes()) {
					if (parameter == FormatterEvent.class) {
						this.method = method;
						return;
					}
				}

				throw new IllegalArgumentException("parse method should have a FormatterEvent parameter");
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
