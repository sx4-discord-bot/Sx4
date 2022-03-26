package com.sx4.bot.utility;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ClassUtility {

	public static Type[] getParameterTypes(ParameterizedType firstType, Class<?> start) {
		if (firstType.getRawType() == start) {
			start = null;
		}

		Type[] typeArguments = firstType.getActualTypeArguments();

		Type[] types = new Type[typeArguments.length];
		for (int i = 0; i < typeArguments.length; i++) {
			Type type = typeArguments[i];

			Type finalType;
			if (type instanceof ParameterizedType parameterizedType) {
				Type rawType = parameterizedType.getRawType();
				if (rawType == start) {
					return ClassUtility.getParameterTypes(parameterizedType, start);
				}

				finalType = rawType;
			} else {
				finalType = type;
			}

			types[i] = finalType;
		}

		return types;
	}

	public static Type[] getParameterTypes(Parameter parameter, Class<?> start) {
		return ClassUtility.getParameterTypes((ParameterizedType) parameter.getParameterizedType(), start);
	}

}
