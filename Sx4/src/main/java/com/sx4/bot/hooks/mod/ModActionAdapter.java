package com.sx4.bot.hooks.mod;

import com.sx4.bot.events.mod.BanEvent;
import com.sx4.bot.events.mod.KickEvent;
import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.events.mod.MuteEvent;
import com.sx4.bot.events.mod.MuteExtendEvent;
import com.sx4.bot.events.mod.TempBanEvent;
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.events.mod.UnmuteEvent;
import com.sx4.bot.events.mod.WarnEvent;

public abstract class ModActionAdapter implements ModActionListener {
	
	public void onAction(ModActionEvent event) {}
	public void onBan(BanEvent event) {}
	public void onTempBan(TempBanEvent event) {}
	public void onKick(KickEvent event) {}
	public void onMute(MuteEvent event) {}
	public void onMuteExtend(MuteExtendEvent event) {}
	public void onWarn(WarnEvent event) {}
	public void onUnmute(UnmuteEvent event) {}
	public void onUnban(UnbanEvent event) {}

	public void onModAction(ModActionEvent event) {
		this.onAction(event);
		
		if (event instanceof BanEvent) {
			this.onBan((BanEvent) event);
		} else if (event instanceof TempBanEvent) {
			this.onTempBan((TempBanEvent) event);
		} else if (event instanceof KickEvent) {
			this.onKick((KickEvent) event);
		} else if (event instanceof MuteEvent) {
			this.onMute((MuteEvent) event);
		} else if (event instanceof MuteExtendEvent) {
			this.onMuteExtend((MuteExtendEvent) event);
		} else if (event instanceof WarnEvent) {
			this.onWarn((WarnEvent) event);
		} else if (event instanceof UnmuteEvent) {
			this.onUnmute((UnmuteEvent) event);
		} else if (event instanceof UnbanEvent) {
			this.onUnban((UnbanEvent) event);
		}
	}

}
