package com.sx4.bot.entities.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class IGDBParser {

	private final List<String> fields;

	private String filter = null;
	private String sort = null;
	private String search = null;

	private int limit = -1;
	private int offset = -1;

	public IGDBParser() {
		this.fields = new ArrayList<>();
	}

	public IGDBParser addFields(Collection<String> fields) {
		this.fields.addAll(fields);

		return this;
	}

	public IGDBParser addFields(String... fields) {
		return this.addFields(Arrays.asList(fields));
	}

	public IGDBParser allFields() {
		this.fields.clear();

		return this;
	}

	public IGDBParser appendFilter(Function<String, String> function) {
		if (this.filter == null) {
			throw new IllegalArgumentException("Use IGDBParser#setFilter if there is no current filter");
		}

		this.filter = function.apply(this.filter);

		return this;
	}

	public IGDBParser setFilter(String filter) {
		this.filter = filter;

		return this;
	}

	public IGDBParser sort(String key, boolean reverse) {
		this.sort = key + ":" + (reverse ? "desc" : "asc");

		return this;
	}

	public IGDBParser search(String query) {
		this.search = query;

		return this;
	}

	public IGDBParser limit(int limit) {
		this.limit = limit;

		return this;
	}

	public IGDBParser offset(int offset) {
		this.offset = offset;

		return this;
	}

	public String parse() {
		StringBuilder builder = new StringBuilder();

		builder.append("fields ")
			.append(this.fields.isEmpty() ? "*" : String.join(",", this.fields))
			.append(";");

		if (this.filter != null) {
			builder.append("where ")
				.append(this.filter)
				.append(";");
		}

		if (this.search != null) {
			builder.append("search ")
				.append(this.search)
				.append(";");
		}

		if (this.sort != null) {
			builder.append("sort ")
				.append(this.sort)
				.append(";");
		}

		if (this.limit != -1) {
			builder.append("limit ")
				.append(this.limit)
				.append(";");
		}

		if (this.offset != -1) {
			builder.append("offset ")
				.append(this.offset)
				.append(";");
		}

		return builder.toString();
	}

}
