package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;
import com.sx4.bot.utility.TimeUtility.OffsetTimeZone;

import java.time.OffsetDateTime;

public class TimeZoneFunction extends FormatterFunction<OffsetDateTime> {

	public TimeZoneFunction() {
		super(OffsetDateTime.class, "timezone", "Sets the time zone for a date");
	}

	public OffsetDateTime parse(FormatterEvent<OffsetDateTime> event, String timeZone) {
		return event.getObject().withOffsetSameInstant(OffsetTimeZone.getTimeZone(timeZone).asZoneOffset());
	}

}
