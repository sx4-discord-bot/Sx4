package com.sx4.bot.formatter.output.function;

import com.sx4.bot.formatter.output.annotation.AcceptNull;
import com.sx4.bot.formatter.output.annotation.ExcludeFormatting;
import com.sx4.bot.formatter.output.annotation.UsePrevious;
import com.sx4.bot.utility.ClassUtility;

import java.lang.reflect.Parameter;
import java.util.Optional;

public class FormatterArgument {

	private final Class<?> type;

	private final boolean optional;
	private final boolean usePrevious;
	private final boolean acceptNull;
	private final boolean excludeFormatting;

	public FormatterArgument(Parameter parameter) {
		Class<?> type = parameter.getType();
		boolean optional = type == Optional.class;
		if (optional) {
			type = (Class<?>) ClassUtility.getParameterTypes(parameter)[0];
		}

		this.type = type;
		this.optional = optional;
		this.usePrevious = parameter.isAnnotationPresent(UsePrevious.class);
		this.acceptNull = parameter.isAnnotationPresent(AcceptNull.class);
		this.excludeFormatting = parameter.isAnnotationPresent(ExcludeFormatting.class);
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

	public boolean isExcludeFormatting() {
		return this.excludeFormatting;
	}

}
