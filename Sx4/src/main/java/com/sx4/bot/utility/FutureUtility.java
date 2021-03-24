package com.sx4.bot.utility;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class FutureUtility {

	public static <Type> CompletableFuture<Type> anyOf(Collection<? extends CompletableFuture<? extends Type>> futures, Predicate<Type> predicate) {
		CompletableFuture<Type> future = new CompletableFuture<>();

		CompletableFuture<?>[] array = futures.stream()
			.map(f -> f.thenAccept(object -> {
				if (predicate.test(object)) {
					future.complete(object);
				}
			})).toArray(CompletableFuture<?>[]::new);

		CompletableFuture.allOf(array).whenComplete((result, exception) -> future.complete(null));

		return future;
	}

	public static <Type> CompletableFuture<Type> anyOf(Collection<? extends CompletableFuture<? extends Type>> futures) {
		return FutureUtility.anyOf(futures, object -> true);
	}

}
