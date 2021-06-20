package com.sx4.bot.utility;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
import com.jockie.bot.core.command.parser.ParseContext;
import com.jockie.bot.core.parser.IGenericParser;
import com.jockie.bot.core.parser.IParser;
import com.jockie.bot.core.parser.ParsedResult;

public class CommandUtility {

	@SuppressWarnings("rawtypes")
	public static ParsedResult<?> getParsedResult(Class<?> clazz, ArgumentFactoryImpl argumentFactory, ParseContext context, IArgument<?> argument, String content, String fullContent) {
		IParser parser = argumentFactory.getParser(clazz);
		if (parser == null) {
			parser = argumentFactory.getGenericParser(clazz);
		}

		return parser instanceof IGenericParser ? ((IGenericParser) parser).parse(context, clazz, argument, fullContent != null && parser.isHandleAll() ? fullContent : content) : parser.parse(context, argument, fullContent != null && parser.isHandleAll() ? fullContent : content);
	}

}
