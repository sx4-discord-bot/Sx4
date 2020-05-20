package com.sx4.bot.managers;

import java.util.ArrayList;
import java.util.List;

import com.sx4.bot.events.mod.BanEvent;
import com.sx4.bot.events.mod.KickEvent;
import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.events.mod.MuteEvent;
import com.sx4.bot.events.mod.MuteExtendEvent;
import com.sx4.bot.events.mod.TempBanEvent;
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.events.mod.UnmuteEvent;
import com.sx4.bot.events.mod.WarnEvent;
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
			listener.onAction(event);
			
			if (event instanceof BanEvent) {
				listener.onBan((BanEvent) event);
			} else if (event instanceof TempBanEvent) {
				listener.onTempBan((TempBanEvent) event);
			} else if (event instanceof KickEvent) {
				listener.onKick((KickEvent) event);
			} else if (event instanceof MuteEvent) {
				listener.onMute((MuteEvent) event);
			} else if (event instanceof MuteExtendEvent) {
				listener.onMuteExtend((MuteExtendEvent) event);
			} else if (event instanceof WarnEvent) {
				listener.onWarn((WarnEvent) event);
			} else if (event instanceof UnmuteEvent) {
				listener.onUnmute((UnmuteEvent) event);
			} else if (event instanceof UnbanEvent) {
				listener.onUnban((UnbanEvent) event);
			}
		}
	}
	
}
