package com.sx4.bot.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Limit {

	public long max() default Long.MAX_VALUE;
	
	public long min() default Long.MIN_VALUE;
	
}
