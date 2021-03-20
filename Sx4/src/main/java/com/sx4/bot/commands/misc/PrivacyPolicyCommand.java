package com.sx4.bot.commands.misc;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;

public class PrivacyPolicyCommand extends Sx4Command {

    public PrivacyPolicyCommand() {
        super("privacy policy", 104);

        super.setDescription("View the privacy policy of Sx4, required for discord api purposes");
        super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
        super.setCategoryAll(ModuleCategory.MISC);
    }

    public void onCommand(Sx4CommandEvent event) {
        PagedResult<Document> paged = new PagedResult<>(event.getBot(), event.getConfig().getPolicies())
            .setPerPage(1)
            .setCustomFunction(page -> {
                MessageBuilder builder = new MessageBuilder();

                EmbedBuilder embed = new EmbedBuilder();
                embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT, null);

                page.forEach((policy, index) -> {
                    embed.setTitle(String.format("(%d/%d) %s", page.getPage(), page.getMaxPage(), policy.getString("title")));
                    embed.setDescription(policy.getString("description"));
                });

                return builder.setEmbed(embed.build()).build();
            });

        paged.execute(event);
    }

}
