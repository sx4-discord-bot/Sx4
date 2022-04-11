package com.sx4.bot.formatter;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.sx4.bot.formatter.function.FormatterFunction;
import com.sx4.bot.formatter.function.FormatterParser;
import com.sx4.bot.formatter.function.FormatterVariable;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class FormatterManager {

	private static FormatterManager defaultManager = new FormatterManager();

	private final Map<Class<?>, Map<String, FormatterFunction<?>>> functions;
	private final Map<Class<?>, Map<String, FormatterVariable<?>>> variables;

	private final Map<Class<?>, FormatterParser<?>> parsers;

	private final Set<Class<?>> handleInheritance;

	public FormatterManager(FormatterManager manager) {
		this.functions = new HashMap<>(manager.getFunctions());
		this.variables = new HashMap<>(manager.getVariables());
		this.parsers = new HashMap<>(manager.getParsers());
		this.handleInheritance = new LinkedHashSet<>(manager.getHandleInheritance());
	}

	public FormatterManager() {
		this.functions = new HashMap<>();
		this.variables = new HashMap<>();
		this.parsers = new HashMap<>();
		this.handleInheritance = new LinkedHashSet<>();
	}

	public FormatterManager addParser(FormatterParser<?> parser) {
		this.parsers.put(parser.getType(), parser);

		return this;
	}

	public <Type> FormatterManager addParser(Class<Type> type, Function<String, Type> function) {
		return this.addParser(new FormatterParser<>(type, function));
	}

	public FormatterManager addFunctions(String packagePath) {
		ClassLoader loader = ClassLoader.getSystemClassLoader();

		ImmutableSet<ClassInfo> classes;
		try {
			classes = ClassPath.from(loader).getTopLevelClasses(packagePath);
		} catch (IOException e) {
			e.printStackTrace();
			return this;
		}

		for (ClassInfo info : classes) {
			Class<?> loadedClass;
			try {
				loadedClass = loader.loadClass(info.getName());
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				return this;
			}

			if (FormatterFunction.class.isAssignableFrom(loadedClass)) {
				try {
					this.addFunction((FormatterFunction<?>) loadedClass.getConstructor().newInstance());
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		return this;
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

	public FormatterManager removeVariable(String name) {
		Map<String, FormatterVariable<?>> staticVariables = this.variables.get(Void.class);
		if (staticVariables == null) {
			return this;
		}

		staticVariables.remove(name);
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

	public FormatterManager addVariable(String name, Object object) {
		return this.addVariable(name, Void.class, object.getClass(), $ -> object);
	}

	public <Type> FormatterManager addVariable(String name, Class<Type> type, Function<Type, Object> function) {
		return this.addVariable(name, type, null, function);
	}

	public <Type> FormatterManager addVariable(String name, Class<Type> type, Class<?> returnType, Function<Type, Object> function) {
		return this.addVariable(new FormatterVariable<>(name, null, type, returnType, function));
	}

	public <Type> FormatterManager addVariable(String name, String description, Class<Type> type, Class<?> returnType, Function<Type, Object> function) {
		return this.addVariable(new FormatterVariable<>(name, description, type, returnType, function));
	}

	public FormatterParser<?> getParser(Class<?> type) {
		return this.parsers.get(type);
	}

	public FormatterFunction<?> getFunction(Class<?> type, String name) {
		for (Class<?> inheritanceType : this.getInheritanceTypes(type)) {
			FormatterFunction<?> function = this.functions.getOrDefault(inheritanceType, new HashMap<>()).get(name);
			if (function != null) {
				return function;
			}
		}

		return null;
	}

	public FormatterVariable<?> getVariable(Class<?> type, String name) {
		for (Class<?> inheritanceType : this.getInheritanceTypes(type)) {
			FormatterVariable<?> variable = this.variables.getOrDefault(inheritanceType, new HashMap<>()).get(name);
			if (variable != null) {
				return variable;
			}
		}

		return null;
	}

	public List<FormatterFunction<?>> getFunctions(Class<?> type) {
		List<FormatterFunction<?>> functions = new ArrayList<>();
		for (Class<?> inheritanceType : this.getInheritanceTypes(type)) {
			Map<String, FormatterFunction<?>> names = this.functions.get(inheritanceType);
			if (names == null) {
				continue;
			}

			for (String name : names.keySet()) {
				functions.add(names.get(name));
			}
		}

		return functions;
	}

	public List<FormatterVariable<?>> getVariables(Class<?> type) {
		List<FormatterVariable<?>> functions = new ArrayList<>();
		for (Class<?> inheritanceType : this.getInheritanceTypes(type)) {
			Map<String, FormatterVariable<?>> names = this.variables.get(inheritanceType);
			if (names == null) {
				continue;
			}

			for (String name : names.keySet()) {
				functions.add(names.get(name));
			}
		}

		return functions;
	}

	public Map<Class<?>, Map<String, FormatterFunction<?>>> getFunctions() {
		return this.functions;
	}

	public Map<Class<?>, Map<String, FormatterVariable<?>>> getVariables() {
		return this.variables;
	}

	public Map<Class<?>, FormatterParser<?>> getParsers() {
		return this.parsers;
	}

	private Set<Class<?>> getInheritanceTypes(Class<?> type) {
		Set<Class<?>> types = new HashSet<>();
		types.add(type);

		for (Class<?> inheritanceType : this.handleInheritance) {
			if (inheritanceType.isAssignableFrom(type)) {
				types.add(inheritanceType);
			}
		}

		return types;
	}

	public Set<Class<?>> getHandleInheritance() {
		return this.handleInheritance;
	}

	public static FormatterManager getDefaultManager() {
		return new FormatterManager(FormatterManager.defaultManager);
	}

	public static void setDefaultManager(FormatterManager manager) {
		FormatterManager.defaultManager = manager;
	}

}
