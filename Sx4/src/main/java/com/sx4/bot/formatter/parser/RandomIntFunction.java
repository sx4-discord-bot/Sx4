package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.Random;

public class RandomIntFunction extends FormatterFunction<Random> {

	public RandomIntFunction() {
		super(Random.class, "int");
	}

	public Integer parse(FormatterEvent event, Integer limit) {
		return ((Random) event.getObject()).nextInt(limit + 1);
	}

}
