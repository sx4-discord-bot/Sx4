package com.sx4.bot.entities.mod.action;

import org.bson.Document;

import com.sx4.bot.entities.mod.warn.WarnConfig;
import com.sx4.bot.utility.TimeUtility;

public class Action {
	
	private final ModAction action;

	public Action(ModAction action) {
		this.action = action;
	}
	
	public ModAction getModAction() {
		return this.action;
	}
	
	public String toString() {
		Action action;
		if (this instanceof WarnAction) {
			action = ((WarnAction) this).getWarning().getAction();
		} else {
			action = this;
		}
		
		return action.getModAction().getName() + (action instanceof TimeAction ? " (" + TimeUtility.getTimeString(((TimeAction) action).getDuration()) + ")" : "");
	}
	
	public static Action fromData(Document data) {
		Document action = data.get("action", Document.class);
		ModAction modAction = ModAction.getFromType(action.getInteger("type"));
		
		if (action.containsKey("duration")) {
			return new TimeAction(modAction, action.getLong("duration"));
		} else if (action.containsKey("warning")) {
			return new WarnAction(new WarnConfig(action.get("warning", Document.class)));
		} else {
			return new Action(modAction);
		}
	}
	
}
