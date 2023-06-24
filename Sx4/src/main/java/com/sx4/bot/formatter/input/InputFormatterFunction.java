package com.sx4.bot.formatter.input;

import java.util.Map;

public interface InputFormatterFunction<Type> {

	Type parse(InputFormatterContext context, String text, Map<String, Object> options);

}
