package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ProfileCommand extends Sx4Command {

	public ProfileCommand() {
		super("profile", 282);

		super.setDescription("View a users Sx4 profile");
		super.setExamples("profile", "profile @Shea#6653", "profile set");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		member = member == null ? event.getMember() : member;
		User user = member.getUser();

		Bson filter = Filters.or(Filters.eq("proposerId", member.getIdLong()), Filters.eq("partnerId", member.getIdLong()));
		List<Document> marriages = event.getDatabase().getMarriages(filter, Projections.include("proposerId", "partnerId")).into(new ArrayList<>());

		List<String> partners = new ArrayList<>();
		for (Document marriage : marriages) {
			long partnerId = marriage.getLong("partnerId");
			long otherId = partnerId == member.getIdLong() ? marriage.getLong("proposerId") : partnerId;

			User other = event.getShardManager().getUserById(otherId);
			if (other != null) {
				partners.add(other.getAsTag());
			}
		}

		Document userData = event.getDatabase().getUserById(member.getIdLong(), Projections.include("economy.balance", "profile", "reputation.amount"));
		Document profileData = userData.get("profile", Database.EMPTY_DOCUMENT);
		Document birthdayData = profileData.get("birthday", Document.class);

		String birthday = birthdayData == null ? null : NumberUtility.getZeroPrefixedNumber(birthdayData.getInteger("day")) + "/" + NumberUtility.getZeroPrefixedNumber(birthdayData.getInteger("month")) + (birthdayData.containsKey("year") ? "/" + birthdayData.getInteger("year") : "");

		int centimetres = profileData.get("height", 0);

		int feet = (int) Math.floor(centimetres / 30.48);
		int inches = (int) Math.round(((centimetres / 30.48) - feet) * 12);

		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("profile"))
			.addField("birthday", birthday == null ? "Not set" : birthday)
			.addField("description", profileData.get("description", "Nothing to see here"))
			.addField("height", centimetres == 0 ? "Not set" : feet + "'" + inches + " (" + centimetres + "cm)") // change to an int and let the webserver handle it
			.addField("balance", String.format("%,d", userData.getEmbedded(List.of("economy", "balance"), 0L))) // change to an int and let the webserver handle the commas
			.addField("reputation", userData.getEmbedded(List.of("reputation", "amount"), 0))
			.addField("married_users", partners)
			.addField("badges", Collections.emptyList()) // Badges will be deprecated but it errors for now so just send an empty array
			.addField("name", user.getAsTag())
			.addField("avatar", user.getEffectiveAvatarUrl())
			.addField("colour", profileData.getInteger("colour"))
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

	public static class SetCommand extends Sx4Command {

		public SetCommand() {
			super("set", 283);

			super.setDescription("Set attributes of your Sx4 profile");
			super.setExamples("profile set height", "profile set description", "profile set colour");
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
			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
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
			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
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

			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), reset ? Updates.unset("profile.height") : Updates.set("profile.height", centimeters)).whenComplete((result, exception) -> {
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
			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
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

	}

}
