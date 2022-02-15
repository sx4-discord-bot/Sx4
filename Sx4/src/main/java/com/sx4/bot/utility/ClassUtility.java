package com.sx4.bot.utility;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ClassUtility {

	public static Class<?>[] getParameterTypes(ParameterizedType firstType, Class<?> start) {
		if (firstType.getRawType() == start) {
			start = null;
		}

		Type[] types = firstType.getActualTypeArguments();

		Class<?>[] classes = new Class<?>[types.length];
		for (int i = 0; i < types.length; i++) {
			Type type = types[i];

			Class<?> clazz;
			if (type instanceof ParameterizedType parameterizedType) {
				Class<?> rawType = (Class<?>) parameterizedType.getRawType();
				if (rawType == start) {
					return ClassUtility.getParameterTypes(parameterizedType, start);
				}

				clazz = rawType;
			} else {
				clazz = (Class<?>) type;
			}

			classes[i] = clazz;
		}

		return classes;
	}

	public static Class<?>[] getParameterTypes(Parameter parameter, Class<?> start) {
		return ClassUtility.getParameterTypes((ParameterizedType) parameter.getParameterizedType(), start);
	}

}
