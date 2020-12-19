package com.sx4.bot.utility;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class MessageUtility {

	public static Message fromJson(Document json) {
		MessageBuilder builder = new MessageBuilder();
		if (json.containsKey("embed")) {
			Object embedJson = json.get("embed");
			if (!(embedJson instanceof Document)) {
				throw new IllegalArgumentException("`embed` value has to be a json object");
			}

			Document embedData = (Document) embedJson;

			String title = null, url = null;
			if (embedData.containsKey("title")) {
				Object titleJson = embedData.get("title");
				if (!(titleJson instanceof String)) {
					throw new IllegalArgumentException("`embed.title` value has to be a string");
				}

				Object urlJson = embedData.get("url");
				if (urlJson != null && !(urlJson instanceof String)) {
					throw new IllegalArgumentException("`embed.url` value has to be a string");
				}

				url = urlJson == null ? null : (String) urlJson;

				if (urlJson != null && url.length() > MessageEmbed.URL_MAX_LENGTH) {
					throw new IllegalArgumentException("`embed.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
				}

				if (urlJson != null && !EmbedBuilder.URL_PATTERN.matcher(url).matches()) {
					throw new IllegalArgumentException("`embed.url` is not a valid url");
				}

				title = (String) titleJson;
				if (title.length() > MessageEmbed.TITLE_MAX_LENGTH) {
					throw new IllegalArgumentException("`embed.title` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
				}
			}

			String description = null;
			if (embedData.containsKey("description")) {
				Object descriptionJson = embedData.get("description");
				if (!(descriptionJson instanceof String)) {
					throw new IllegalArgumentException("`embed.description` value has to be a string");
				}

				description = (String) descriptionJson;
				if (description.length() > MessageEmbed.TEXT_MAX_LENGTH) {
					throw new IllegalArgumentException("`embed.description` value cannot be more than " + MessageEmbed.TEXT_MAX_LENGTH + " characters");
				}
			}

			int colour = Role.DEFAULT_COLOR_RAW;
			if (embedData.containsKey("color")) {
				Object colourJson = embedData.get("color");
				if (!(colourJson instanceof Integer)) {
					throw new IllegalArgumentException("`embed.color` value has to be an integer");
				}

				colour = (int) colourJson;
			}

			OffsetDateTime timestamp = null;
			if (embedData.containsKey("timestamp")) {
				Object timestampJson = embedData.get("timestamp");
				if (!(timestampJson instanceof String)) {
					throw new IllegalArgumentException("`embed.timestamp` value has to be a string");
				}

				try {
					timestamp = OffsetDateTime.parse((String) timestampJson);
				} catch (DateTimeParseException e) {
					throw new IllegalArgumentException("`embed.timestamp` value was an invalid date time");
				}
			}

			MessageEmbed.Footer footer = null;
			if (embedData.containsKey("footer")) {
				Object footerJson = embedData.get("footer");
				if (!(footerJson instanceof Document)) {
					throw new IllegalArgumentException("`embed.footer` value has to be a json object");
				}

				Document footerData = (Document) footerJson;

				String text = null, iconUrl = null;
				if (footerData.containsKey("icon_url")) {
					Object iconUrlJson = footerData.get("icon_url");
					if (!(iconUrlJson instanceof String)) {
						throw new IllegalArgumentException("`embed.footer.icon_url` value has to be a string");
					}

					iconUrl = (String) iconUrlJson;

					if (iconUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.footer.icon_url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
					}

					if (!EmbedBuilder.URL_PATTERN.matcher(iconUrl).matches()) {
						throw new IllegalArgumentException("`embed.footer.icon_url` is not a valid url");
					}
				}

				if (footerData.containsKey("text")) {
					Object textJson = footerData.get("text");
					if (!(textJson instanceof String)) {
						throw new IllegalArgumentException("`embed.footer.text` value has to be a string");
					}

					text = (String) textJson;

					if (text.length() > MessageEmbed.TEXT_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.footer.text` value cannot be more than " + MessageEmbed.TEXT_MAX_LENGTH + " characters");
					}
				}

				if (text != null) {
					footer = new MessageEmbed.Footer(text, iconUrl, null);
				}
			}

			MessageEmbed.Thumbnail thumbnail = null;
			if (embedData.containsKey("thumbnail")) {
				Object thumbnailJson = embedData.get("thumbnail");
				if (!(thumbnailJson instanceof Document)) {
					throw new IllegalArgumentException("`embed.thumbnail` value has to be a json object");
				}

				Document thumbnailData = (Document) thumbnailJson;
				if (thumbnailData.containsKey("url")) {
					Object urlJson = thumbnailData.get("url");
					if (!(urlJson instanceof String)) {
						throw new IllegalArgumentException("`embed.thumbnail.url` value has to be a string");
					}

					String thumbnailUrl = (String) urlJson;
					if (thumbnailUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.thumbnail.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
					}

					if (!EmbedBuilder.URL_PATTERN.matcher(thumbnailUrl).matches()) {
						throw new IllegalArgumentException("`embed.thumbnail.url` is not a valid url");
					}

					thumbnail = new MessageEmbed.Thumbnail(thumbnailUrl, null, 0, 0);
				}
			}

			MessageEmbed.ImageInfo image = null;
			if (embedData.containsKey("image")) {
				Object imageJson = embedData.get("image");
				if (!(imageJson instanceof Document)) {
					throw new IllegalArgumentException("`embed.image` value has to be a json object");
				}

				Document imageData = (Document) imageJson;
				if (imageData.containsKey("url")) {
					Object urlJson = imageData.get("url");
					if (!(urlJson instanceof String)) {
						throw new IllegalArgumentException("`embed.image.url` value has to be a string");
					}

					String imageUrl = (String) urlJson;
					if (imageUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.image.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
					}

					if (!EmbedBuilder.URL_PATTERN.matcher(imageUrl).matches()) {
						throw new IllegalArgumentException("`embed.image.url` is not a valid url");
					}

					image = new MessageEmbed.ImageInfo(imageUrl, null, 0, 0);
				}
			}

			MessageEmbed.AuthorInfo author = null;
			if (embedData.containsKey("author")) {
				Object authorJson = embedData.get("author");
				if (!(authorJson instanceof Document)) {
					throw new IllegalArgumentException("`embed.author` value has to be a json object");
				}

				Document authorData = (Document) authorJson;

				String name = null, iconUrl = null, authorUrl = null;
				if (authorData.containsKey("icon_url")) {
					Object iconUrlJson = authorData.get("icon_url");
					if (!(iconUrlJson instanceof String)) {
						throw new IllegalArgumentException("`embed.author.icon_url` value has to be a string");
					}

					iconUrl = (String) iconUrlJson;

					if (iconUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.author.icon_url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
					}

					if (!EmbedBuilder.URL_PATTERN.matcher(iconUrl).matches()) {
						throw new IllegalArgumentException("`embed.author.icon_url` is not a valid url");
					}
				}

				if (authorData.containsKey("name")) {
					Object nameJson = authorData.get("name");
					if (!(nameJson instanceof String)) {
						throw new IllegalArgumentException("`embed.author.name` value has to be a string");
					}

					name = (String) nameJson;

					if (name.length() > MessageEmbed.TITLE_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.author.name` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
					}
				}

				if (authorData.containsKey("url")) {
					Object urlJson = authorData.get("url");
					if (!(urlJson instanceof String)) {
						throw new IllegalArgumentException("`embed.author.url` value has to be a string");
					}

					authorUrl = (String) urlJson;

					if (authorUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`embed.author.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
					}

					if (!EmbedBuilder.URL_PATTERN.matcher(authorUrl).matches()) {
						throw new IllegalArgumentException("`embed.author.url` is not a valid url");
					}
				}

				if (name != null) {
					author = new MessageEmbed.AuthorInfo(name, authorUrl, iconUrl, null);
				}
			}

			List<MessageEmbed.Field> fields = new ArrayList<>();
			if (embedData.containsKey("fields")) {
				Object fieldsJson = embedData.get("fields");
				if (!(fieldsJson instanceof List)) {
					throw new IllegalArgumentException("`embed.fields` value has to be an array");
				}

				List<?> fieldsData = (List<?>) fieldsJson;

				int length = fieldsData.size();
				if (length > 25) {
					throw new IllegalArgumentException("You can only have 25 fields per embed");
				}

				for (int i = 0; i < length; i++) {
					Object fieldJson = fieldsData.get(i);
					if (!(fieldJson instanceof Document)) {
						throw new IllegalArgumentException("`embed.fields." + i + "` value has to be a json object");
					}

					Document field = (Document) fieldJson;

					String name = null, value = null;
					boolean inline = true;
					if (field.containsKey("value")) {
						Object valueJson = field.get("value");
						if (!(valueJson instanceof String)) {
							throw new IllegalArgumentException("`embed.fields." + i + ".value` value has to be a string");
						}

						value = (String) valueJson;

						if (value.length() > MessageEmbed.TEXT_MAX_LENGTH) {
							throw new IllegalArgumentException("`embed.fields." + i + ".value` value cannot be more than " + MessageEmbed.TEXT_MAX_LENGTH + " characters");
						}
					}

					if (field.containsKey("name")) {
						Object nameJson = field.get("name");
						if (!(nameJson instanceof String)) {
							throw new IllegalArgumentException("`embed.fields." + i + ".name` value has to be a string");
						}

						name = (String) nameJson;

						if (name.length() > MessageEmbed.TITLE_MAX_LENGTH) {
							throw new IllegalArgumentException("`embed.fields." + i + ".name` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
						}
					}

					if (field.containsKey("inline")) {
						Object inlineJson = field.get("inline");
						if (!(inlineJson instanceof Boolean)) {
							throw new IllegalArgumentException("`embed.fields." + i + ".inline` value has to be a boolean");
						}

						inline = (Boolean) inlineJson;
					}

					if (name != null && value != null) {
						fields.add(new MessageEmbed.Field(name, value, inline));
					}
				}
			}

			MessageEmbed embed = new MessageEmbed(url, title, description, EmbedType.RICH, timestamp, colour, thumbnail, null, author, null, footer, image, fields);
			if (embed.isEmpty()) {
				throw new IllegalArgumentException("The embed cannot be empty");
			}

			if (embed.getLength() > 6000) {
				throw new IllegalArgumentException("The embeds total length cannot be more than 6000 characters");
			}

			builder.setEmbed(embed);
		}

		if (json.containsKey("content")) {
			Object contentJson = json.get("content");
			if (!(contentJson instanceof String)) {
				throw new IllegalArgumentException("`content` value has to be a string");
			}

			String content = (String) contentJson;

			if (content.length() > 2000) {
				throw new IllegalArgumentException("`content` value cannot be more than 2000 characters");
			}

			builder.setContent(content);
		}

		if (builder.isEmpty()) {
			throw new IllegalArgumentException("Message cannot be empty");
		}

		return builder.build();
	}

	public static void removeFields(Document json) {
		json.keySet().removeIf(key -> !key.equals("embed") && !key.equals("content"));

		Object embedJson = json.get("embed");
		if (embedJson instanceof Document) {
			Document embed = ((Document) embedJson);

			embed.keySet().removeIf(key ->
				!key.equals("author") &&
				!key.equals("title") &&
				!key.equals("color") &&
				!key.equals("fields") &&
				!key.equals("description") &&
				!key.equals("image") &&
				!key.equals("thumbnail") &&
				!key.equals("footer") &&
				!key.equals("url")
			);

			Object author = embed.get("author");
			if (author instanceof Document) {
				((Document) author).keySet().removeIf(key -> !key.equals("name") && !key.equals("icon_url") && !key.equals("url"));
			}

			Object footer = embed.get("footer");
			if (footer instanceof Document) {
				((Document) footer).keySet().removeIf(key -> !key.equals("text") && !key.equals("icon_url"));
			}

			Object thumbnail = embed.get("thumbnail");
			if (thumbnail instanceof Document) {
				((Document) thumbnail).keySet().removeIf(key -> !key.equals("url"));
			}

			Object image = embed.get("image");
			if (image instanceof Document) {
				((Document) image).keySet().removeIf(key -> !key.equals("url"));
			}
		}
	}

}
