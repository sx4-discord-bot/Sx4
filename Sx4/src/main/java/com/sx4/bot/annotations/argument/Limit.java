package com.sx4.bot.annotations.argument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Limit {

	int max() default Integer.MAX_VALUE;
	
	int min() default Integer.MIN_VALUE;

	boolean error() default true;
	
}
