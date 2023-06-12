package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.Random;

public class RandomIntFunction extends FormatterFunction<Random> {

	public RandomIntFunction() {
		super(Random.class, "int", "Gives a random number between 0 and the limit given");
	}

	public Integer parse(FormatterEvent<Random> event, Integer limit) {
		return event.getObject().nextInt(limit + 1);
	}

}
