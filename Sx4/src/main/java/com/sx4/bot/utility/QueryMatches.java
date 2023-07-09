package com.sx4.bot.utility;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryMatches<Type> {

	private final TreeSet<QueryMatch<Type>> set;
	private final String query;
	private final int minScore;
	private int maxScore;

	private QueryMatches(TreeSet<QueryMatch<Type>> set, String query, int maxScore, int minScore) {
		this.set = set;
		this.query = query;
		this.maxScore = maxScore;
		this.minScore = minScore;
	}

	public QueryMatches(String query, int minScore) {
		this(new TreeSet<>(Collections.reverseOrder(Comparator.comparingInt(QueryMatch::score))), query, 0, minScore);
	}

	public int getMaxScore() {
		return this.maxScore;
	}

	public boolean addValue(Type value, Function<Type, String> mapping) {
		return this.addValue(value, mapping.apply(value));
	}

	public boolean addValue(Type value, String compare) {
		int score = StringUtility.getScore(compare, this.query);
		if (score < this.minScore) {
			return false;
		}

		if (this.set.add(new QueryMatch<>(value, score))) {
			this.maxScore = Math.max(this.maxScore, score);
			return true;
		}

		return false;
	}

	public boolean isEmpty() {
		return this.set.isEmpty();
	}

	public QueryMatch<Type> first() {
		return this.set.first();
	}

	public List<Type> toList() {
		return this.set.stream().filter(query -> this.maxScore != 100 || query.score == 100).map(QueryMatch::value).collect(Collectors.toCollection(LinkedList::new));
	}

	public record QueryMatch<T>(T value, int score) implements Comparable<QueryMatch<T>> {

		public int hashCode() {
			return this.value.hashCode();
		}

		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}

			if (other == null || this.getClass() != other.getClass()) {
				return false;
			}

			QueryMatch<?> that = (QueryMatch<?>) other;
			return Objects.equals(value, that.value);
		}

		public int compareTo(QueryMatch<T> other) {
			return Integer.compare(this.score, other.score);
		}

	}

}
