package com.sx4.bot.entities.mod.warn;

import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;
import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;

public class Warn {
	
	public static final List<Document> DEFAULT_CONFIG = List.of(
		new TimeAction(ModAction.MUTE, 1800L).toData().append("number", 2),
		new Action(ModAction.KICK).toData().append("number", 3),
		new Action(ModAction.BAN).toData().append("number", 4)
	);

	private final Action action;
	private final int number;
	
	public Warn(Document data) {
		Document action = data.get("action", Document.class);
		ModAction modAction = ModAction.getFromType(action.getInteger("type"));
		
		this.action = action.containsKey("duration") ? new TimeAction(modAction, action.getLong("duration")) : new Action(modAction);
		this.number = data.getInteger("number");
	}
	
	public Warn(int type, int number) {
		this(new Action(ModAction.getFromType(type)), number);
	}
	
	public Warn(int type, int number, long duration) {
		this(new TimeAction(ModAction.getFromType(type), duration), number);
	}
	
	public Warn(Action action, int number) {
		this.action = action;
		this.number = number;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public int getNumber() {
		return this.number;
	}
	
	public static List<Warn> fromData(List<Document> data) {
		return data.stream().map(Warn::new).collect(Collectors.toList());
	}
	
}
