package com.sx4.bot.annotations.argument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sx4.bot.entities.argument.UpdateType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ExcludeUpdate {

	public UpdateType[] value() default {};
	
}
