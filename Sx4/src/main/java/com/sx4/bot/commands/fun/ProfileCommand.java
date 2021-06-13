package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.cooldown.ICooldown;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.CompactNumber;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ImageUtility;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.*;

public class ProfileCommand extends Sx4Command {

	public ProfileCommand() {
		super("profile", 282);

		super.setDescription("View a users Sx4 profile");
		super.setExamples("profile", "profile @Shea#6653", "profile set");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		List<Bson> marriagePipeline = List.of(
			Aggregates.project(Projections.include("proposerId", "partnerId")),
			Aggregates.match(Filters.or(Filters.eq("proposerId", user.getIdLong()), Filters.eq("partnerId", user.getIdLong()))),
			Aggregates.group(null, Accumulators.push("marriages", Operators.ROOT))
		);

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("balance", "$economy.balance"), Projections.include("profile"), Projections.computed("reputation", "$reputation.amount"))),
			Aggregates.match(Filters.eq("_id", user.getIdLong())),
			Aggregates.unionWith("marriages", marriagePipeline),
			Aggregates.group(null, Accumulators.max("balance", "$balance"), Accumulators.max("reputation", "$reputation"), Accumulators.max("marriages", "$marriages"), Accumulators.max("profile", "$profile"))
		);

		event.getMongo().aggregateUsers(pipeline).thenApply(iterable -> {
			Document data = iterable.first();
			data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;

			List<Document> marriages = data.getList("marriages", Document.class, Collections.emptyList());

			List<String> partners = new ArrayList<>();
			for (Document marriage : marriages) {
				long partnerId = marriage.getLong("partnerId");
				long otherId = partnerId == user.getIdLong() ? marriage.getLong("proposerId") : partnerId;

				User other = event.getShardManager().getUserById(otherId);
				if (other != null) {
					partners.add(other.getName());
				}
			}

			Document profileData = data.get("profile", MongoDatabase.EMPTY_DOCUMENT);
			Document birthdayData = profileData.get("birthday", Document.class);

			String birthday = birthdayData == null ? null : NumberUtility.getZeroPrefixedNumber(birthdayData.getInteger("day")) + "/" + NumberUtility.getZeroPrefixedNumber(birthdayData.getInteger("month")) + (birthdayData.containsKey("year") ? "/" + birthdayData.getInteger("year") : "");

			return new ImageRequest(event.getConfig().getImageWebserverUrl("profile"))
				.addField("birthday", birthday == null ? "Not set" : birthday)
				.addField("description", profileData.get("description", "Nothing to see here"))
				.addField("height", profileData.get("height", 0))
				.addField("balance", CompactNumber.getCompactNumber(data.get("balance", 0L)))
				.addField("reputation", data.get("reputation", 0))
				.addField("married_users", partners)
				.addField("banner_id", profileData.getString("bannerId"))
				.addField("directory", event.getConfig().isCanary() ? "sx4-canary" : "sx4-main")
				.addField("name", user.getAsTag())
				.addField("avatar", user.getEffectiveAvatarUrl())
				.addField("colour", profileData.getInteger("colour"))
				.build(event.getConfig().getImageWebserver());
		}).whenComplete((request, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
		});
	}

	public static class SetCommand extends Sx4Command {

		private final Set<String> types = Set.of("png", "jpeg", "jpg");

		public SetCommand() {
			super("set", 283);

			super.setDescription("Set attributes of your Sx4 profile");
			super.setExamples("profile set height", "profile set description", "profile set colour");
			super.setCategoryAll(ModuleCategory.FUN);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="description", description="Sets the description of your profile")
		@CommandId(284)
		@Examples({"profile set description A short description about me", "profile set description reset"})
		public void description(Sx4CommandEvent event, @Argument(value="description | reset", endless=true) @Options("reset") @Limit(max=300) Alternative<String> option) {
			boolean reset = option.isAlternative();
			String description = option.getValue();

			Bson update = reset ? Updates.unset("profile.description") : Updates.set("profile.description", description);
			event.getMongo().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
					event.replyFailure("Your description was already " + (reset ? "unset" : "set to that")).queue();
					return;
				}

				event.replySuccess("Your description has been " + (reset ? "reset" : "updated")).queue();
			});
		}

		@Command(value="birthday", description="Set the birthday of your profile (EU format)")
		@CommandId(285)
		@Examples({"profile set birthday 01/07", "profile set birthday 01/07/2002"})
		public void birthday(Sx4CommandEvent event, @Argument(value="birthday | reset") @Options("reset") @DateTimePattern("dd/MM[/uuuu][/uu]") @DefaultDateTime(types={"YEAR"}, values={0}) Alternative<LocalDate> option) {
			boolean reset = option.isAlternative();
			LocalDate date = option.getValue();

			Document data = null;
			if (!reset) {
				data = new Document("day", date.getDayOfMonth()).append("month", date.getMonthValue());
				if (date.getYear() != 0) {
					if (date.isAfter(LocalDate.now(ZoneOffset.UTC))) {
						event.replyFailure("Your birthday cannot be in the future").queue();
						return;
					}

					data.append("year", date.getYear());
				}
			}

			Bson update = reset ? Updates.unset("profile.birthday") : Updates.set("profile.birthday", data);
			event.getMongo().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
					event.replyFailure("Your birthday was already " + (reset ? "unset" : "set to that")).queue();
					return;
				}

				event.replySuccess("Your birthday has been " + (reset ? "reset" : "set to **" + NumberUtility.getSuffixed(date.getDayOfMonth()) + " " + date.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + "**")).queue();
			});
		}

		@Command(value="height", description="Set your height on your Sx4 profile")
		@CommandId(287)
		@Examples({"profile set height 5'9", "profile set height 175", "profile set height reset"})
		public void height(Sx4CommandEvent event, @Argument(value="centimetres | feet and inches | reset") String height) {
			boolean reset = height.equalsIgnoreCase("reset");

			int centimeters;
			if (NumberUtility.isNumber(height)) {
				try {
					centimeters = Integer.parseInt(height);
				} catch (NumberFormatException e) {
					event.replyHelp().queue();
					return;
				}
			} else if (height.contains("'")) {
				String[] split = height.split("'");

				int feet, inches;
				try {
					feet = Integer.parseInt(split[0]);
					inches = split.length == 1 ? 0 : Integer.parseInt(split[1]);
				} catch (NumberFormatException e) {
					event.replyFailure("Feet and/or inches was not a number").queue();
					return;
				}

				centimeters = (int) ((feet * 30.48) + (inches * 2.54));
			} else if (!reset) {
				event.replyFailure("Invalid height format").queue();
				return;
			} else {
				centimeters = -1;
			}

			if (centimeters < 0 && !reset) {
				event.replyFailure("Your height cannot be negative").queue();
				return;
			}

			event.getMongo().updateUserById(event.getAuthor().getIdLong(), reset ? Updates.unset("profile.height") : Updates.set("profile.height", centimeters)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
					event.replyFailure("Your height was already " + (reset ? "unset" : "set to that")).queue();
					return;
				}

				event.replySuccess("Your height has been " + (reset ? "unset" : "set to **" + centimeters + "cm**")).queue();
			});
		}

		@Command(value="colour", aliases={"color"}, description="Sets the border colour of your profile on Sx4")
		@CommandId(288)
		@Examples({"profile set colour #ffff00", "profile set colour 255,255,0", "profile set colour reset"})
		public void colour(Sx4CommandEvent event, @Argument(value="colour | reset", endless=true) @Options("reset") @Colour Alternative<Integer> option) {
			boolean reset = option.isAlternative();
			Integer colour = option.getValue();

			Bson update = reset ? Updates.unset("profile.colour") : Updates.set("profile.colour", colour);
			event.getMongo().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
					event.replyFailure("Your profile colour was already " + (reset ? "unset" : "set to that")).queue();
					return;
				}

				event.replySuccess("Your profile colour has been " + (reset ? "unset" : "set to **#" + ColourUtility.toHexString(colour) + "**")).queue();
			});
		}

		@Command(value="banner", aliases={"bg", "background"}, description="Set the banner for your profile on Sx4")
		@CommandId(289)
		@Cooldown(value=30, cooldownScope= ICooldown.Scope.USER)
		@Async
		@Examples({"profile set banner https://i.imgur.com/i87lyNO.png", "profile set banner reset"})
		public void banner(Sx4CommandEvent event, @Argument(value="url | reset") @ImageUrl @Options("reset") Alternative<String> option) {
			if (option.isAlternative()) {
				File file = new File("profile/banners/" + event.getAuthor().getId() + ".png");
				if (file.delete()) {
					event.getMongo().updateUserById(event.getAuthor().getIdLong(), Updates.unset("profile.bannerId")).whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}

						event.replySuccess("Your profile banner has been unset").queue();
					});
				} else {
					event.replyFailure("You do not have a profile banner").queue();
				}
			} else {
				Request request = new Request.Builder()
					.url(option.getValue())
					.build();

				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					String contentType = response.header("Content-Type");
					if (contentType == null) {
						event.replyFailure("That url does not return a content type").queue();
						return;
					}

					String[] contentTypeSplit = contentType.split("/");

					String type = contentTypeSplit[0], subType = contentType.contains("/") ? contentTypeSplit[1] : "png";
					if (!type.equals("image")) {
						event.replyFailure("That url is not an image").queue();
						return;
					}

					if (!this.types.contains(subType)) {
						event.replyFailure("That image is not a supported image type").queue();
						return;
					}

					byte[] bytes = response.body().bytes();
					if (bytes.length > 5_000_000) {
						event.replyFailure("Your profile banner cannot be more than 5MB").queue();
						return;
					}

					String bannerId = event.getAuthor().getId() + "." + subType;

					FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("profile.bannerId")).upsert(true);
					event.getMongo().findAndUpdateUserById(event.getAuthor().getIdLong(), Updates.set("profile.bannerId", bannerId), options).whenComplete((data, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}

						if (data != null)  {
							String banner = data.getEmbedded(List.of("profile", "bannerId"), String.class);
							if (banner != null) {
								new File("profile/banners/" + bannerId).delete();
							}
						}

						try (FileOutputStream stream = new FileOutputStream("profile/banners/" + bannerId)) {
							stream.write(bytes);
						} catch (IOException e) {
							ExceptionUtility.sendExceptionally(event, e);
							return;
						}

						event.replySuccess("Your profile banner has been updated").queue();
					});
				});
			}
		}

	}

}
