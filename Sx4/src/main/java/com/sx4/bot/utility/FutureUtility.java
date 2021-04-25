package com.sx4.bot.utility;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FutureUtility {

	public static <Type> CompletableFuture<Type> anyOf(Collection<? extends CompletableFuture<? extends Type>> futures, Predicate<Type> predicate) {
		CompletableFuture<Type> future = new CompletableFuture<>();

		CompletableFuture<?>[] array = futures.stream()
			.map(f -> f.thenAccept(object -> {
				if (predicate.test(object)) {
					future.complete(object);
				}
			})).toArray(CompletableFuture[]::new);

		CompletableFuture.allOf(array).whenComplete((result, exception) -> future.complete(null));

		return future;
	}

	public static <Type> CompletableFuture<Type> anyOf(Collection<? extends CompletableFuture<? extends Type>> futures) {
		return FutureUtility.anyOf(futures, object -> true);
	}

	public static <Type> CompletableFuture<List<Type>> allOf(Collection<? extends CompletableFuture<? extends Type>> futures, Predicate<Type> predicate) {
		CompletableFuture<List<Type>> future = new CompletableFuture<>();

		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((result, exception) -> future.complete(futures.stream().map(CompletableFuture::join).filter(predicate::test).collect(Collectors.toList())));

		return future;
	}

	public static <Type> CompletableFuture<List<Type>> allOf(Collection<? extends CompletableFuture<? extends Type>> futures) {
		return FutureUtility.allOf(futures, future -> true);
	}

}
