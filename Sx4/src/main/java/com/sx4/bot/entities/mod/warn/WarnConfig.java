package com.sx4.bot.entities.mod.warn;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.hooks.mod.ModAction;

public class WarnConfig {
	
	public static final List<WarnConfig> DEFAULT = List.of(
		new WarnConfig(new TimeAction(ModAction.MUTE, 1800L), 2),
		new WarnConfig(new Action(ModAction.KICK), 3),
		new WarnConfig(new Action(ModAction.BAN), 4)
	);

	private final Action action;
	private final int number;
	
	public WarnConfig(Document data) {
		Document action = data.get("action", Document.class);
		ModAction modAction = ModAction.getFromType(action.getInteger("type"));
		
		this.action = action.containsKey("duration") ? new TimeAction(modAction, action.getLong("duration")) : new Action(modAction);
		this.number = data.getInteger("number");
	}
	
	public WarnConfig(int type, int number) {
		this(new Action(ModAction.getFromType(type)), number);
	}
	
	public WarnConfig(int type, int number, long duration) {
		this(new TimeAction(ModAction.getFromType(type), duration), number);
	}
	
	public WarnConfig(Action action, int number) {
		this.action = action;
		this.number = number;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public int getNumber() {
		return this.number;
	}
	
	public static List<WarnConfig> fromData(List<Document> data) {
		return data.stream().map(WarnConfig::new).collect(Collectors.toList());
	}
	
}
