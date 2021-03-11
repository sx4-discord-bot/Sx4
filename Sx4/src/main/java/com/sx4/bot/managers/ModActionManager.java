package com.sx4.bot.managers;

import com.sx4.bot.events.mod.*;
import com.sx4.bot.hooks.ModActionListener;

import java.util.ArrayList;
import java.util.List;

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

				if (event instanceof TemporaryBanEvent) {
					listener.onTemporaryBan((TemporaryBanEvent) event);
				}
			}

			if (event instanceof KickEvent) {
				listener.onKick((KickEvent) event);
			}

			if (event instanceof MuteEvent) {
				listener.onMute((MuteEvent) event);

				if (event instanceof MuteExtendEvent) {
					listener.onMuteExtend((MuteExtendEvent) event);
				}
			}

			if (event instanceof WarnEvent) {
				listener.onWarn((WarnEvent) event);
			}

			if (event instanceof UnmuteEvent) {
				listener.onUnmute((UnmuteEvent) event);
			}

			if (event instanceof UnbanEvent) {
				listener.onUnban((UnbanEvent) event);
			}
		}
	}
	
}
