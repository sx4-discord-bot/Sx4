package com.sx4.bot.managers;

import java.util.ArrayList;
import java.util.List;

import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.hooks.mod.ModActionListener;

public class ModActionManager {

	private final List<ModActionListener> listeners;
	
	public ModActionManager() {
		this.listeners = new ArrayList<>();
	}
	
	public ModActionManager addListener(ModActionListener listener) {
		this.listeners.add(listener);
		
		return this;
	}
	
	public ModActionManager removeListener(ModActionListener listener) {
		this.listeners.remove(listener);
		
		return this;
	}
	
	public List<ModActionListener> getListeners() {
		return this.listeners;
	}
	
	public void onModAction(ModActionEvent event) {
		for (ModActionListener listener : this.listeners) {
			listener.onModAction(event);
		}
	}
	
}
