package com.sx4.bot.formatter.output.function;

import com.sx4.bot.formatter.output.annotation.AcceptNull;
import com.sx4.bot.formatter.output.annotation.UsePrevious;
import com.sx4.bot.utility.ClassUtility;

import java.lang.reflect.Parameter;
import java.util.Optional;

public class FormatterArgument {

	private final Class<?> type;

	private final boolean optional;
	private final boolean usePrevious;
	private final boolean acceptNull;

	public FormatterArgument(Parameter parameter) {
		boolean usePrevious = parameter.isAnnotationPresent(UsePrevious.class);
		boolean acceptNull = parameter.isAnnotationPresent(AcceptNull.class);

		Class<?> type = parameter.getType();
		boolean optional = type == Optional.class;
		if (optional) {
			type = (Class<?>) ClassUtility.getParameterTypes(parameter)[0];
		}

		this.type = type;
		this.optional = optional;
		this.usePrevious = usePrevious;
		this.acceptNull = acceptNull;
	}

	public Class<?> getType() {
		return this.type;
	}

	public boolean isOptional() {
		return this.optional;
	}

	public boolean isUsePrevious() {
		return this.usePrevious;
	}

	public boolean isAcceptNull() {
		return this.acceptNull;
	}

}
