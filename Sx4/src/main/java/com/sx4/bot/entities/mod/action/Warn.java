package com.sx4.bot.entities.mod.action;

import org.bson.Document;

import java.util.List;

public class Warn {
	
	public static final List<Document> DEFAULT_CONFIG = List.of(
		new Warn(new TimeAction(ModAction.MUTE, 1800L), 2).toData(),
		new Warn(new Action(ModAction.KICK), 3).toData(),
		new Warn(new Action(ModAction.BAN), 4).toData()
	);

	private final int number;
	private final Action action;

	public Warn(Action action, int number) {
		this.action = action;
		this.number = number;
	}
	
	public Warn(int type, int number) {
		this(new Action(ModAction.fromType(type)), number);
	}
	
	public Warn(int type, int number, long duration) {
		this(new TimeAction(ModAction.fromType(type), duration), number);
	}

	public Action getAction() {
		return this.action;
	}
	
	public int getNumber() {
		return this.number;
	}

	public Document toData() {
		return new Document("action", this.action.toData()).append("number", this.number);
	}

	public static Warn fromData(Document data) {
		Document action = data.get("action", Document.class);
		ModAction modAction = ModAction.fromType(action.getInteger("type"));

		return new Warn(action.containsKey("duration") ? new TimeAction(modAction, action.getLong("duration")) : new Action(modAction), data.getInteger("number"));
	}
	
}
