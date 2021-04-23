package com.sx4.bot.formatter;

import com.sx4.bot.formatter.function.FormatterFunction;
import com.sx4.bot.formatter.function.FormatterVariable;

import java.util.*;
import java.util.function.Function;

public class FormatterManager {

	private static FormatterManager defaultManager = new FormatterManager();

	private final Map<Class<?>, Map<String, FormatterFunction<?>>> functions;
	private final Map<Class<?>, Map<String, FormatterVariable<?>>> variables;

	private final Set<Class<?>> handleInheritance;

	public FormatterManager() {
		this.functions = new HashMap<>();
		this.variables = new HashMap<>();
		this.handleInheritance = new LinkedHashSet<>();
	}

	public FormatterManager addFunction(FormatterFunction<?> function) {
		this.functions.compute(function.getType(), (key, value) -> {
			if (value == null) {
				Map<String, FormatterFunction<?>> map = new HashMap<>();
				map.put(function.getName(), function);
				return map;
			} else {
				value.put(function.getName(), function);
				return value;
			}
		});

		this.handleInheritance.add(function.getType());

		return this;
	}

	public FormatterManager addVariable(FormatterVariable<?> variable) {
		this.variables.compute(variable.getType(), (key, value) -> {
			if (value == null) {
				Map<String, FormatterVariable<?>> map = new HashMap<>();
				map.put(variable.getName(), variable);
				return map;
			} else {
				value.put(variable.getName(), variable);
				return value;
			}
		});

		this.handleInheritance.add(variable.getType());

		return this;
	}

	public <Type> FormatterManager addVariable(String name, Class<Type> type, Function<Type, Object> function) {
		return this.addVariable(new FormatterVariable<>(name, type, function));
	}

	public FormatterFunction<?> getFunction(Class<?> type, String name) {
		List<Class<?>> types = this.getInheritanceTypes(type);
		for (Class<?> inheritanceType : types) {
			FormatterFunction<?> function = this.functions.getOrDefault(inheritanceType, new HashMap<>()).get(name);
			if (function != null) {
				return function;
			}
		}

		return null;
	}

	public FormatterVariable<?> getVariable(Class<?> type, String name) {
		List<Class<?>> types = this.getInheritanceTypes(type);
		for (Class<?> inheritanceType : types) {
			FormatterVariable<?> variable = this.variables.getOrDefault(inheritanceType, new HashMap<>()).get(name);
			if (variable != null) {
				return variable;
			}
		}

		return null;
	}

	public Map<Class<?>, Map<String, FormatterFunction<?>>> getFunctions() {
		return this.functions;
	}

	public Map<Class<?>, Map<String, FormatterVariable<?>>> getVariables() {
		return this.variables;
	}

	private List<Class<?>> getInheritanceTypes(Class<?> type) {
		List<Class<?>> types = new ArrayList<>();
		types.add(type);

		for (Class<?> inheritanceType : this.handleInheritance) {
			if (inheritanceType.isAssignableFrom(type)) {
				types.add(inheritanceType);
			}
		}

		return types;
	}

	public static FormatterManager getDefaultManager() {
		return FormatterManager.defaultManager;
	}

	public static void setDefaultManager(FormatterManager manager) {
		FormatterManager.defaultManager = manager;
	}

}
