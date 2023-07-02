package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.utility.function.TriConsumer;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.RequestUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

public class EmoteCommand extends Sx4Command {

	public EmoteCommand() {
		super("emote", 129);

		super.setDescription("Create, delete or modify an emote");
		super.setAliases("emoji");
		super.setExamples("emote create", "emote delete", "emote whitelist");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	private void getBytes(OkHttpClient httpClient, String url, TriConsumer<byte[], Boolean, Integer> bytes) {
		Request request = new Request.Builder()
			.url(RequestUtility.getWorkerUrl(url))
			.build();

		httpClient.newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 200) {
				String contentType = response.header("Content-Type"), extension = null;
				if (contentType != null && contentType.contains("/")) {
					extension = contentType.split("/")[1].toLowerCase();
				}

				bytes.accept(response.body().bytes(), extension == null ? null : extension.equals("gif"), 200);
				return;
			} else if (response.code() == 415) {
				int periodIndex = url.lastIndexOf('.');
				if (periodIndex != -1) {
					String extension = url.substring(periodIndex + 1);

					if (extension.equalsIgnoreCase("gif")) {
						this.getBytes(httpClient, url.substring(0, periodIndex + 1) + "png", bytes);
						return;
					}
				}
			}

			bytes.accept(null, null, response.code());
		});
	}

	@Command(value="create", description="Creates an emote from an existing emote or url")
	@CommandId(130)
	@Examples({"emote create <:sx4:637715282995183636>", "emote create sx4", "emote create https://cdn.discordapp.com/emojis/637715282995183636.png"})
	@Cooldown(5)
	@Redirects({"create emote", "ce", "create emoji"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
	@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
	public void create(Sx4CommandEvent event, @Argument(value="emote | image url", acceptEmpty=true) PartialEmote emote, @Argument(value="name", endless=true, nullDefault=true) String name) {
		long animatedEmotes = event.getGuild().getEmojiCache().applyStream(stream -> stream.filter(CustomEmoji::isAnimated).count());
		long nonAnimatedEmotes = event.getGuild().getEmojiCache().applyStream(stream -> stream.filter(Predicate.not(CustomEmoji::isAnimated)).count());
		int maxEmotes = event.getGuild().getMaxEmojis();

		Boolean animated = emote.isAnimated();
		if (animated != null && ((animated && animatedEmotes >= maxEmotes) || (!animated && nonAnimatedEmotes >= maxEmotes))) {
			event.replyFailure("You already have the max" + (animated ? "" : " non") + " animated emotes on this server").queue();
			return;
		}

		this.getBytes(event.getHttpClient(), emote.getUrl(), (bytes, animatedResponse, code) -> {
			if (bytes == null) {
				event.replyFailure("Failed to get url from the emote argument with status code: " + code).queue();
				return;
			}

			if (bytes.length > 256000) {
				event.replyFailure("You cannot create an emote larger than 256KB").queue();
				return;
			}

			if (animatedResponse != null && ((animatedResponse && animatedEmotes >= maxEmotes) || (!animatedResponse && nonAnimatedEmotes >= maxEmotes))) {
				event.replyFailure("You already have the max" + (animatedResponse ? "" : " non") + " animated emotes on this server").queue();
				return;
			}

			event.getGuild().createEmoji(name == null ? emote.hasName() ? emote.getName() : "Unnamed_Emote" : name, Icon.from(bytes)).submit(false)
				.thenCompose(createdEmote -> event.replySuccess(createdEmote.getAsMention() + " has been created").submit())
				.whenComplete((result, exception) -> {
					if (exception instanceof CompletionException) {
						exception = exception.getCause();
					}

					if (exception instanceof ErrorResponseException errorResponseException) {
						if (errorResponseException.getErrorCode() == 400) {
							event.replyFailure("You cannot create an emote larger than 256KB").queue();
						} else if (errorResponseException.getErrorResponse() == ErrorResponse.INVALID_FORM_BODY) {
							event.replyFailure("The url given is not a valid image").queue();
						}

						return;
					}

					if (exception instanceof RateLimitedException) {
						event.replyFailure("Creating emotes in this server is currently rate-limited by discord, try again in " + TimeUtility.LONG_TIME_FORMATTER.parse(((RateLimitedException) exception).getRetryAfter() / 1000)).queue();
						return;
					}

					ExceptionUtility.sendExceptionally(event, exception);
				});
		});
	}

	@Command(value="delete", description="Deletes an emote from the server")
	@CommandId(131)
	@Examples({"emote delete <:sx4:637715282995183636>", "emote delete sx4"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
	@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
	public void delete(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji) {
		if (emoji.isManaged()) {
			event.replyFailure("I cannot delete emotes that are managed").queue();
			return;
		}

		emoji.delete().flatMap($ -> event.replySuccess("I have deleted the emote `" + emoji.getName() + "`")).queue();
	}

	public static class WhitelistCommand extends Sx4Command {

		public WhitelistCommand() {
			super("whitelist", 132);

			super.setDescription("Allows you to set roles which have access to the specified emote");
			super.setExamples("emote whitelist set", "emote whitelist add", "emote whitelist remove");
			super.setCategoryAll(ModuleCategory.MODERATION);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="set", description="Sets what roles should be whitelisted to use the emote")
		@CommandId(133)
		@Examples({"emote whitelist set <:rain:748240799719882762> \"@Emote Role\"", "emote whitelist set rain \"Emote Role\" @Emotes"})
		@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		public void set(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji, @Argument(value="roles") Role... roles) {
			List<Role> currentRoles = emoji.getRoles(), newRoles = Arrays.asList(roles);
			if (newRoles.containsAll(currentRoles)) {
				event.replyFailure("That emote already has all those roles whitelisted").queue();
				return;
			}

			emoji.getManager().setRoles(new HashSet<>(newRoles))
				.flatMap($ -> event.replyFormat("The emote %s is now only whitelisted from those roles %s", emoji.getAsMention(), event.getConfig().getSuccessEmote()))
				.queue();
		}

		@Command(value="add", description="Adds a role to be whitelisted to use the emote")
		@CommandId(134)
		@Examples({"emote whitelist add <:rain:748240799719882762> @Emote Role", "emote whitelist add rain Emote Role"})
		@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		public void add(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji, @Argument(value="role", endless=true) Role role) {
			Set<Role> currentRoles = new HashSet<>(emoji.getRoles());
			if (currentRoles.contains(role)) {
				event.replyFailure("That emote already has that role whitelisted").queue();
				return;
			}

			currentRoles.add(role);

			emoji.getManager().setRoles(currentRoles)
				.flatMap($ -> event.replyFormat("The emote %s is now whitelisted from that role %s", emoji.getAsMention(), event.getConfig().getSuccessEmote()))
				.queue();
		}

		@Command(value="remove", description="Removes a role from being whitelisted to use the emote")
		@CommandId(135)
		@Examples({"emote whitelist remove <:rain:748240799719882762> @Emote Role", "emote whitelist remove rain Emote Role"})
		@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		public void remove(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji, @Argument(value="role", endless=true) Role role) {
			Set<Role> currentRoles = new HashSet<>(emoji.getRoles());
			if (!currentRoles.contains(role)) {
				event.replyFailure("That emote does not have that role whitelisted").queue();
				return;
			}

			currentRoles.remove(role);

			emoji.getManager().setRoles(currentRoles)
				.flatMap($ -> event.replyFormat("The emote %s is no longer whitelisted from that role %s", emoji.getAsMention(), event.getConfig().getSuccessEmote()))
				.queue();
		}

		@Command(value="reset", description="Resets the emote so everyone can use it")
		@CommandId(136)
		@Examples({"emote whitelist reset <:rain:748240799719882762>", "emote whitelist reset rain"})
		@AuthorPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		@BotPermissions(permissions={Permission.MANAGE_EMOJIS_AND_STICKERS})
		public void reset(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji) {
			emoji.getManager().setRoles(null)
				.flatMap($ -> event.replyFormat("The emote %s no longer has any whitelisted roles %s", emoji.getAsMention(), event.getConfig().getSuccessEmote()))
				.queue();
		}

		@Command(value="list", description="Lists the roles able to use the emote")
		@CommandId(137)
		@Examples({"emote whitelist list <:rain:748240799719882762>", "emote whitelist list rain"})
		public void list(Sx4CommandEvent event, @Argument(value="emote") RichCustomEmoji emoji) {
			if (emoji.getRoles().isEmpty()) {
				event.replyFailure("That role does not have any whitelisted role").queue();
				return;
			}

			MessagePagedResult<Role> paged = new MessagePagedResult.Builder<>(event.getBot(), emoji.getRoles())
				.setSelect()
				.setAuthor("Roles Whitelisted", null, event.getGuild().getIconUrl())
				.setDisplayFunction(Role::getAsMention)
				.setIndexed(false)
				.build();

			paged.execute(event);
		}

	}


}
