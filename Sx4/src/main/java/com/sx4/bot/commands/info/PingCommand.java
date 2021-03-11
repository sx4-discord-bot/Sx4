package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class PingCommand extends Sx4Command {

	public PingCommand() {
		super("ping", 263);

		super.setDescription("Get the bots gateway and REST ping");
		super.setExamples("ping");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.getJDA().getRestPing().queue(time -> {
			long gatewayPing = event.getJDA().getGatewayPing();

			event.reply("Pong! :ping_pong:\n\n:stopwatch: **" + time + "ms**\n:heartbeat: **" + gatewayPing + "ms**").queue();
		});
	}

}
