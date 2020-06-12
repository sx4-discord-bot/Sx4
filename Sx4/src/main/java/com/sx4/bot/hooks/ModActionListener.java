package com.sx4.bot.hooks;

import java.util.EventListener;

import com.sx4.bot.events.mod.BanEvent;
import com.sx4.bot.events.mod.KickEvent;
import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.events.mod.MuteEvent;
import com.sx4.bot.events.mod.MuteExtendEvent;
import com.sx4.bot.events.mod.TempBanEvent;
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.events.mod.UnmuteEvent;
import com.sx4.bot.events.mod.WarnEvent;

public interface ModActionListener extends EventListener {

	default void onAction(ModActionEvent event) {}
	
	default void onBan(BanEvent event) {}
	
	default void onTempBan(TempBanEvent event) {}
	
	default void onKick(KickEvent event) {}
	
	default void onMute(MuteEvent event) {}
	
	default void onMuteExtend(MuteExtendEvent event) {}
	
	default void onWarn(WarnEvent event) {}
	
	default void onUnmute(UnmuteEvent event) {}
	
	default void onUnban(UnbanEvent event) {}
	
}
