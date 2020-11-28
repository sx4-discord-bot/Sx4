package com.sx4.bot.entities.argument;

import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Range<Type> {

	private final List<Pair<Type, Type>> ranges;
	private final Set<Type> objects;

	private Range(List<Pair<Type, Type>> ranges, Set<Type> objects) {
		this.ranges = ranges;
		this.objects = objects;
	}

	public List<Pair<Type, Type>> getRanges() {
		return this.ranges;
	}

	public Set<Type> getObjects() {
		return this.objects;
	}

	public static <Type> Range<Type> getRange(String string, Function<String, Type> cast) {
		string = string.replaceAll("\\s+", "");

		List<Pair<Type, Type>> ranges = new ArrayList<>();
		Set<Type> objects = new HashSet<>();

		String[] arguments = string.split(",");

		for (String argument : arguments) {
			String[] range = argument.split("-", 2);
			if (range.length == 2) {
				Type rangeOne = cast.apply(range[0]), rangeTwo = cast.apply(range[1]);
				if (rangeOne != null && rangeTwo != null) {
					ranges.add(Pair.of(rangeOne, rangeTwo));
				}
			} else {
				Type object = cast.apply(argument);
				if (object != null) {
					objects.add(object);
				}
			}
		}

		return new Range<>(ranges, objects);
	}

	public static Range<String> getRange(String string) {
		return Range.getRange(string, Function.identity());
	}

}
