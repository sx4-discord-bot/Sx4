package com.sx4.bot.entities.mod.action;

import org.bson.Document;

import com.sx4.bot.entities.mod.warn.Warn;
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

	public Document toData() {
		Document action = new Document("type", this.getModAction().getType());
		if (this instanceof TimeAction) {
			action.append("duration", ((TimeAction) this).getDuration());
		} else if (this instanceof WarnAction) {
			action.append("warning", ((WarnAction) this).getWarning().getAction().toData());
		}

		return new Document("action", action);
	}
	
	public static Action fromData(Document data) {
		Document action = data.get("action", Document.class);
		ModAction modAction = ModAction.getFromType(action.getInteger("type"));
		
		if (action.containsKey("duration")) {
			return new TimeAction(modAction, action.getLong("duration"));
		} else if (action.containsKey("warning")) {
			return new WarnAction(new Warn(action.get("warning", Document.class)));
		} else {
			return new Action(modAction);
		}
	}
	
}
