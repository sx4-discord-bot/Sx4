package com.sx4.api;

import com.sx4.api.endpoints.PatreonEndpoint;
import com.sx4.api.endpoints.RedirectEndpoint;
import com.sx4.api.endpoints.YouTubeEndpoint;
import com.sx4.api.exceptions.UncaughtExceptionHandler;
import com.sx4.bot.core.Sx4;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;

public class Sx4Server {
	
	public static void initiateWebserver(Sx4 bot) throws Exception {
		ServletContextHandler contextHandler = new ServletContextHandler();
		contextHandler.setContextPath("/");

		ResourceConfig resourceConfig = new ResourceConfig();
		
		resourceConfig.registerInstances(
			new UncaughtExceptionHandler(bot),
			new YouTubeEndpoint(bot),
			new PatreonEndpoint(bot),
			new RedirectEndpoint(bot)
		);
		
		resourceConfig.property(ServerProperties.PROCESSING_RESPONSE_ERRORS_ENABLED, true);

		ServletContainer container = new ServletContainer(resourceConfig);

		ServletHolder holder = new ServletHolder(container);
		holder.setAsyncSupported(true);

		contextHandler.addServlet(holder, "/*");

		Server server = new Server();

		ServerConnector connector = new ServerConnector(server);
		connector.setPort(bot.getConfig().getPort());
		
		server.setErrorHandler(new ErrorHandler() {
			protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
				if (code == 404) {
					writer.write("You've reached a dead end, I suggest you turn around");
				} else {
					super.handleErrorPage(request, writer, code, message);
				}
			}
		});
		
		server.setHandler(contextHandler);
		server.addConnector(connector);

		server.start();
	}
	
}