package com.sx4.bot.paged;

public class PagedSelectEvent<Type> {

	private final Type selected;
	private final int index;
	private final int page;

	public PagedSelectEvent(Type selected, int index, int page) {
		this.selected = selected;
		this.index = index;
		this.page = page;
	}

	public Type getSelected() {
		return this.selected;
	}

	public int getIndex() {
		return this.index;
	}

	public int getPage() {
		return this.page;
	}

}
