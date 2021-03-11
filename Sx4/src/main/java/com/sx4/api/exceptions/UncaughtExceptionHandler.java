package com.sx4.api.exceptions;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.ExceptionUtility;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;
import java.util.Optional;

@Provider
public class UncaughtExceptionHandler implements ExceptionMapper<Throwable> {

	private final Sx4 bot;

	public UncaughtExceptionHandler(Sx4 bot) {
		this.bot = bot;
	}
	
	public Response toResponse(Throwable exception) {
		ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception);
		
		if (exception instanceof WebApplicationException) {
			return ((WebApplicationException) exception).getResponse();
		}
		
		Optional<StackTraceElement> element = List.of(exception.getStackTrace()).stream()
			.filter(e -> e.getClassName().contains("com.sx4.api"))
			.findFirst();
		
		return Response.status(500)
			.entity(exception.toString() + (element.map(stackTraceElement -> "\n" + stackTraceElement.toString()).orElse("")))
			.type("text/plain")
			.build();
	}
}