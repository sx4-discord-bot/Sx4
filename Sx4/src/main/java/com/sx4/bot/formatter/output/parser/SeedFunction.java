package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.Random;

public class SeedFunction extends FormatterFunction<Random> {

	public SeedFunction() {
		super(Random.class, "seed", "Sets the seed for the random instance");
	}

	public Random parse(FormatterEvent<Random> event, Long seed) {
		Random random = event.getObject();
		random.setSeed(seed);
		return random;
	}

}
