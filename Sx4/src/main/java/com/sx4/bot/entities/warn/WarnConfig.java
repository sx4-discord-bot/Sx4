package com.sx4.bot.entities.warn;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.hooks.mod.ModAction;

public class WarnConfig {
	
	public static final List<WarnConfig> DEFAULT = List.of(
		new WarnConfig(ModAction.MUTE, 2, 1800L),
		new WarnConfig(ModAction.KICK, 3),
		new WarnConfig(ModAction.BAN, 4)
	);

	private final ModAction action;
	private final int number;
	
	private final long duration;
	
	public WarnConfig(Document data) {
		this(data.getInteger("type"), data.getInteger("number"), data.get("duration", 0L));
	}
	
	public WarnConfig(int type, int number) {
		this(type, number, 0L);
	}
	
	public WarnConfig(ModAction action, int number) {
		this(action, number, 0L);
	}
	
	public WarnConfig(int type, int number, long duration) {
		this(ModAction.getFromType(type), number, duration);
	}
	
	public WarnConfig(ModAction action, int number, long duration) {
		this.action = action;
		this.number = number;
		this.duration = duration;
	}
	
	public ModAction getAction() {
		return this.action;
	}
	
	public int getNumber() {
		return this.number;
	}
	
	public boolean hasDuration() {
		return this.duration != 0L;
	}
	
	public long getDuration() {
		return this.duration;
	}
	
	public static List<WarnConfig> fromData(List<Document> data) {
		return data.stream().map(WarnConfig::new).collect(Collectors.toList());
	}
	
}
