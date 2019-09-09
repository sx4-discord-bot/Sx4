package com.sx4.api.exceptions;

import java.util.List;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class UncaughtExceptionHandler implements ExceptionMapper<Throwable> {
	
	public Response toResponse(Throwable exception) {
		if(exception instanceof WebApplicationException) {
			return ((WebApplicationException) exception).getResponse();
		}
		
		Optional<StackTraceElement> element = List.of(exception.getStackTrace()).stream()
			.filter(e -> e.getClassName().contains("com.sx4.api"))
			.findFirst();
		
		return Response.status(500)
			.entity(exception.toString() + (element.isPresent() ? "\n" + element.get().toString() : ""))
			.type("text/plain")
			.build();
	}
}
