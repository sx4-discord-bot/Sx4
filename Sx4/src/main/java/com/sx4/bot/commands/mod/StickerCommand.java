package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.StickerArgument;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionException;

public class StickerCommand extends Sx4Command {

	private final HashMap<Guild.BoostTier, Integer> maxStickers = new HashMap<>();
	{
		this.maxStickers.put(Guild.BoostTier.NONE, 5);
		this.maxStickers.put(Guild.BoostTier.TIER_1, 15);
		this.maxStickers.put(Guild.BoostTier.TIER_2, 30);
		this.maxStickers.put(Guild.BoostTier.TIER_3, 60);
	}

	public StickerCommand() {
		super("sticker", 507);

		super.setDescription("Create or delete a sticker");
		super.setExamples("sticker create", "sticker delete");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="create", description="Creates a sticker from an already existing sticker or an image")
	@CommandId(508)
	@Redirects({"create sticker"})
	@Examples({"sticker create", "sticker create dog", "sticker create https://example.com/sticker.png"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES_AND_STICKERS})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES_AND_STICKERS})
	public void create(Sx4CommandEvent event, @Argument(value="sticker | image url", acceptEmpty=true) StickerArgument sticker, @Argument(value="name", endless=true, nullDefault=true) @Limit(min=2, max=30) String name) {
		int maxStickers = this.maxStickers.get(event.getGuild().getBoostTier());
		if (event.getGuild().getStickerCache().size() >= maxStickers) {
			event.replyFailure("You already have the max amount of stickers on this server").queue();
			return;
		}

		sticker.getBytes(event.getHttpClient(), (bytes, extension, code) -> {
			if (bytes == null) {
				event.replyFailure("Failed to get url from the sticker argument with status code: " + code).queue();
				return;
			}

			if (bytes.length > 512000) {
				event.replyFailure("You cannot create an emote larger than 512KB").queue();
				return;
			}

			String effectiveName = name == null ? sticker.hasName() ? sticker.getName() : "Unnamed_Emote" : name;

			event.getGuild().createSticker(effectiveName, "", FileUpload.fromData(bytes, effectiveName + "." + extension), List.of("sticker")).submit(false)
				.thenCompose(createdSticker -> event.replySuccess("`" + createdSticker.getName() + "` has been created").setStickers(createdSticker).submit())
				.whenComplete((result, exception) -> {
					if (exception instanceof CompletionException) {
						exception = exception.getCause();
					}

					if (exception instanceof ErrorResponseException errorResponseException) {
						if (errorResponseException.getErrorCode() == 400) {
							event.replyFailure("You cannot create a sticker larger than 512KB").queue();
						} else if (errorResponseException.getErrorResponse() == ErrorResponse.INVALID_FORM_BODY) {
							event.replyFailure("The url given is not a valid image").queue();
						}

						return;
					}

					if (exception instanceof RateLimitedException) {
						event.replyFailure("Creating stickers in this server is currently rate-limited by discord, try again in " + TimeUtility.LONG_TIME_FORMATTER.parse(((RateLimitedException) exception).getRetryAfter() / 1000)).queue();
						return;
					}

					ExceptionUtility.sendExceptionally(event, exception);
				});
		});
	}

	@Command(value="delete", description="Deletes a sticker from the current server")
	@CommandId(509)
	@Redirects({"delete sticker"})
	@Examples({"sticker delete", "sticker delete dog"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES_AND_STICKERS})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES_AND_STICKERS})
	public void delete(Sx4CommandEvent event, @Argument(value="sticker", endless=true, acceptEmpty=true) GuildSticker sticker) {
		sticker.delete().flatMap($ -> event.replySuccess("I have deleted the sticker `" + sticker.getName() + "`")).queue();
	}

}
