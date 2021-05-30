package com.sx4.bot.utility;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ClassUtility {

	public static Class<?>[] getParameterTypes(Parameter parameter, Class<?> start) {
		ParameterizedType firstType = (ParameterizedType) parameter.getParameterizedType();
		if (firstType.getRawType() == start) {
			start = null;
		}
	
		Type[] types = firstType.getActualTypeArguments();
	
		Class<?>[] classes = new Class<?>[types.length];
		for (int i = 0; i < types.length; i++) {
			Type type = types[i];
			if (start == null && type instanceof ParameterizedType) {
				type = ((ParameterizedType) type).getRawType();
			} else {
				while (type instanceof ParameterizedType) {
					ParameterizedType parameterizedType = (ParameterizedType) type;
					type = parameterizedType.getActualTypeArguments()[0];
	
					if (parameterizedType.getRawType() == start) {
						break;
					}
				}
			}
	
			classes[i] = (Class<?>) type;
		}
	
		return classes;
	}

}
