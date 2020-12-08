package com.sx4.bot.hooks;

import com.sx4.bot.events.mod.*;

import java.util.EventListener;

public interface ModActionListener extends EventListener {

	default void onAction(ModActionEvent event) {}
	
	default void onBan(BanEvent event) {}
	
	default void onTemporaryBan(TemporaryBanEvent event) {}
	
	default void onKick(KickEvent event) {}
	
	default void onMute(MuteEvent event) {}
	
	default void onMuteExtend(MuteExtendEvent event) {}
	
	default void onWarn(WarnEvent event) {}
	
	default void onUnmute(UnmuteEvent event) {}
	
	default void onUnban(UnbanEvent event) {}
	
}
