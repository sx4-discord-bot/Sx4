package com.sx4.bot.annotations.argument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Limit {

	public int max() default Integer.MAX_VALUE;
	
	public int min() default Integer.MIN_VALUE;
	
}
