package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;

public class EpochStaticFunction extends FormatterStaticFunction {

	public EpochStaticFunction() {
		super("epoch", "Converts epoch second to a date");
	}

	public Temporal parse(FormatterEvent<Void> event, Double epoch) {
		return Instant.ofEpochSecond(epoch.longValue()).atOffset(ZoneOffset.UTC);
	}

}
