package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.MessageAttachment;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MessageUtility {

	private static String titleFromJson(Document json, String field) {
		Object titleJson = json.get("title");
		if (!(titleJson instanceof String title)) {
			throw new IllegalArgumentException("`" + field + ".title` value has to be a string");
		}

		if (title.length() > MessageEmbed.TITLE_MAX_LENGTH) {
			throw new IllegalArgumentException("`embed.title` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
		}

		return title;
	}

	private static String urlFromJson(Document json, boolean checkUrl, String field) {
		Object urlJson = json.get("url");
		if (urlJson != null && !(urlJson instanceof String)) {
			throw new IllegalArgumentException("`" + field + ".url` value has to be a string");
		}

		String url = urlJson == null ? null : (String) urlJson;

		if (urlJson != null && url.length() > MessageEmbed.URL_MAX_LENGTH) {
			throw new IllegalArgumentException("`" + field + ".url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
		}

		if (checkUrl && urlJson != null && !EmbedBuilder.URL_PATTERN.matcher(url).matches()) {
			throw new IllegalArgumentException("`" + field + ".url` is not a valid url");
		}

		return url;
	}

	private static String descriptionFromJson(Document json, String field) {
		Object descriptionJson = json.get("description");
		if (!(descriptionJson instanceof String description)) {
			throw new IllegalArgumentException("`" + field + ".description` value has to be a string");
		}

		if (description.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
			throw new IllegalArgumentException("`" + field + ".description` value cannot be more than " + MessageEmbed.DESCRIPTION_MAX_LENGTH + " characters");
		}

		return description;
	}

	private static int colourFromJson(Document json, String field) {
		Object colourJson = json.get("color");

		int colour;
		if (!(colourJson instanceof Integer)) {
			if (colourJson instanceof String) {
				try {
					colour = Integer.parseInt((String) colourJson);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("`" + field + ".color` value has to be an integer");
				}
			} else {
				throw new IllegalArgumentException("`" + field + ".color` value has to be an integer");
			}
		} else {
			colour = (int) colourJson;
		}

		return colour;
	}

	private static WebhookEmbed.EmbedFooter footerFromJson(Document json, boolean checkUrl, String field) {
		Object footerJson = json.get("footer");
		if (!(footerJson instanceof Document footerData)) {
			throw new IllegalArgumentException("`" + field + ".footer` value has to be a json object");
		}

		String text = null, iconUrl = null;
		if (footerData.containsKey("icon_url")) {
			Object iconUrlJson = footerData.get("icon_url");
			if (!(iconUrlJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + ".footer.icon_url` value has to be a string");
			}

			iconUrl = (String) iconUrlJson;

			if (iconUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".footer.icon_url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
			}

			if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(iconUrl).matches()) {
				throw new IllegalArgumentException("`" + field + ".footer.icon_url` is not a valid url");
			}
		}

		if (footerData.containsKey("text")) {
			Object textJson = footerData.get("text");
			if (!(textJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + ".footer.text` value has to be a string");
			}

			text = (String) textJson;

			if (text.length() > MessageEmbed.TEXT_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".footer.text` value cannot be more than " + MessageEmbed.TEXT_MAX_LENGTH + " characters");
			}
		}

		return text == null ? null : new WebhookEmbed.EmbedFooter(text, iconUrl);
	}

	private static OffsetDateTime timestampFromJson(Document json, boolean checkTimestamp, String field) {
		Object timestampJson = json.get("timestamp");
		if (!(timestampJson instanceof String)) {
			throw new IllegalArgumentException("`" + field + ".timestamp` value has to be a string");
		}

		if (!checkTimestamp) {
			return null;
		}

		OffsetDateTime timestamp;
		try {
			timestamp = OffsetDateTime.parse((String) timestampJson);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("`" + field + ".timestamp` value was an invalid date time");
		}

		return timestamp;
	}

	private static String thumbnailFromJson(Document json, boolean checkUrl, String field) {
		Object thumbnailJson = json.get("thumbnail");
		if (!(thumbnailJson instanceof Document thumbnailData)) {
			throw new IllegalArgumentException("`" + field + ".thumbnail` value has to be a json object");
		}

		if (thumbnailData.containsKey("url")) {
			Object urlJson = thumbnailData.get("url");
			if (!(urlJson instanceof String thumbnailUrl)) {
				throw new IllegalArgumentException("`" + field + ".thumbnail.url` value has to be a string");
			}

			if (thumbnailUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".thumbnail.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
			}

			if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(thumbnailUrl).matches()) {
				throw new IllegalArgumentException("`" + field + ".thumbnail.url` is not a valid url");
			}

			return thumbnailUrl;
		}

		return null;
	}

	private static WebhookEmbed.EmbedAuthor authorFromJson(Document json, boolean checkUrl, String field) {
		Object authorJson = json.get("author");
		if (!(authorJson instanceof Document authorData)) {
			throw new IllegalArgumentException("`" + field + ".author` value has to be a json object");
		}

		String name = null, iconUrl = null, authorUrl = null;
		if (authorData.containsKey("icon_url")) {
			Object iconUrlJson = authorData.get("icon_url");
			if (!(iconUrlJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + ".author.icon_url` value has to be a string");
			}

			iconUrl = (String) iconUrlJson;

			if (iconUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".author.icon_url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
			}

			if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(iconUrl).matches()) {
				throw new IllegalArgumentException("`" + field + ".author.icon_url` is not a valid url");
			}
		}

		if (authorData.containsKey("name")) {
			Object nameJson = authorData.get("name");
			if (!(nameJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + ".author.name` value has to be a string");
			}

			name = (String) nameJson;

			if (name.length() > MessageEmbed.TITLE_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".author.name` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
			}
		}

		if (authorData.containsKey("url")) {
			Object urlJson = authorData.get("url");
			if (!(urlJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + ".author.url` value has to be a string");
			}

			authorUrl = (String) urlJson;

			if (authorUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".author.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
			}

			if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(authorUrl).matches()) {
				throw new IllegalArgumentException("`" + field + ".author.url` is not a valid url");
			}
		}

		return name == null ? null : new WebhookEmbed.EmbedAuthor(name, iconUrl, authorUrl);
	}

	private static String imageFromJson(Document json, boolean checkUrl, String field) {
		Object imageJson = json.get("image");
		if (!(imageJson instanceof Document imageData)) {
			throw new IllegalArgumentException("`" + field + ".image` value has to be a json object");
		}

		if (imageData.containsKey("url")) {
			Object urlJson = imageData.get("url");
			if (!(urlJson instanceof String imageUrl)) {
				throw new IllegalArgumentException("`" + field + ".image.url` value has to be a string");
			}

			if (imageUrl.length() > MessageEmbed.URL_MAX_LENGTH) {
				throw new IllegalArgumentException("`" + field + ".image.url` value cannot be more than " + MessageEmbed.URL_MAX_LENGTH + " characters");
			}

			if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(imageUrl).matches()) {
				throw new IllegalArgumentException("`" + field + ".image.url` is not a valid url");
			}

			return imageUrl;
		}

		return null;
	}

	private static List<WebhookEmbed.EmbedField> fieldsFromJson(Document json, String field) {
		Object fieldsJson = json.get("fields");
		if (!(fieldsJson instanceof List<?> fieldsData)) {
			throw new IllegalArgumentException("`" + field + ".fields` value has to be an array");
		}

		int length = fieldsData.size();
		if (length > 25) {
			throw new IllegalArgumentException("You can only have 25 fields in `" + field + "`");
		}

		List<WebhookEmbed.EmbedField> fields = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			Object fieldJson = fieldsData.get(i);
			if (!(fieldJson instanceof Document fieldData)) {
				throw new IllegalArgumentException("`" + field + ".fields." + i + "` value has to be a json object");
			}

			String name = null, value = null;
			boolean inline = true;
			if (fieldData.containsKey("value")) {
				Object valueJson = fieldData.get("value");
				if (!(valueJson instanceof String)) {
					throw new IllegalArgumentException("`" + field + ".fields." + i + ".value` value has to be a string");
				}

				value = (String) valueJson;

				if (value.length() > MessageEmbed.VALUE_MAX_LENGTH) {
					throw new IllegalArgumentException("`" + field + ".fields." + i + ".value` value cannot be more than " + MessageEmbed.VALUE_MAX_LENGTH + " characters");
				}
			}

			if (fieldData.containsKey("name")) {
				Object nameJson = fieldData.get("name");
				if (!(nameJson instanceof String)) {
					throw new IllegalArgumentException("`" + field + ".fields." + i + ".name` value has to be a string");
				}

				name = (String) nameJson;

				if (name.length() > MessageEmbed.TITLE_MAX_LENGTH) {
					throw new IllegalArgumentException("`" + field + ".fields." + i + ".name` value cannot be more than " + MessageEmbed.TITLE_MAX_LENGTH + " characters");
				}
			}

			if (fieldData.containsKey("inline")) {
				Object inlineJson = fieldData.get("inline");
				if (!(inlineJson instanceof Boolean)) {
					throw new IllegalArgumentException("`" + field + ".fields." + i + ".inline` value has to be a boolean");
				}

				inline = (Boolean) inlineJson;
			}

			if (name != null && value != null) {
				fields.add(new WebhookEmbed.EmbedField(inline, name, value));
			}
		}

		return fields;
	}

	public static WebhookEmbed embedFromJson(Document json, boolean checkValidity, String field) {
		boolean titleExists = json.containsKey("title");

		String title = titleExists ? MessageUtility.titleFromJson(json, field) : null;
		String url = titleExists ? MessageUtility.urlFromJson(json, checkValidity, field) : null;
		String description = json.containsKey("description") ? MessageUtility.descriptionFromJson(json, field) : null;
		Integer colour = json.containsKey("color") ? MessageUtility.colourFromJson(json, field) : null;
		OffsetDateTime timestamp = json.containsKey("timestamp") ? MessageUtility.timestampFromJson(json, checkValidity, field) : null;
		WebhookEmbed.EmbedFooter footer = json.containsKey("footer") ? MessageUtility.footerFromJson(json, checkValidity, field) : null;
		String thumbnail = json.containsKey("thumbnail") ? MessageUtility.thumbnailFromJson(json, checkValidity, field) : null;
		String image = json.containsKey("image") ? MessageUtility.imageFromJson(json, checkValidity, field) : null;
		WebhookEmbed.EmbedAuthor author = json.containsKey("author") ? MessageUtility.authorFromJson(json, checkValidity, field) : null;
		List<WebhookEmbed.EmbedField> fields = json.containsKey("fields") ? MessageUtility.fieldsFromJson(json, field) : Collections.emptyList();

		WebhookEmbed embed = new WebhookEmbed(timestamp, colour, description, thumbnail, image, footer, title == null ? null : new WebhookEmbed.EmbedTitle(title, url), author, fields);
		if (MessageUtility.isWebhookEmbedEmpty(embed)) {
			throw new IllegalArgumentException("`" + field + "` cannot be empty");
		}

		if (MessageUtility.getWebhookEmbedLength(embed) > 6000) {
			throw new IllegalArgumentException("`" + field + "` total length cannot be more than 6000 characters");
		}

		return embed;
	}

	public static WebhookMessageBuilder fromJson(Document json, boolean checkValidity) {
		WebhookMessageBuilder builder = new WebhookMessageBuilder();
		if (json.containsKey("embed")) {
			Object embedJson = json.get("embed");
			if (!(embedJson instanceof Document embedData)) {
				throw new IllegalArgumentException("`embed` value has to be a json object");
			}

			WebhookEmbed embed = MessageUtility.embedFromJson(embedData, checkValidity, "embed");

			builder.addEmbeds(embed);
		} else if (json.containsKey("embeds")) {
			Object embedsField = json.get("embed");
			if (!(embedsField instanceof List embedsData)) {
				throw new IllegalArgumentException("`embeds` value has to be a list object");
			}

			for (int i = 0; i < embedsData.size(); i++) {
				Object embedJson = embedsData.get(i);
				if (!(embedJson instanceof Document embedData)) {
					throw new IllegalArgumentException("`embeds." + i + "` value has to be a json object");
				}

				WebhookEmbed embed = MessageUtility.embedFromJson(embedData, checkValidity, "embeds." + i);

				builder.addEmbeds(embed);
			}
		}

		if (json.containsKey("content")) {
			Object contentJson = json.get("content");
			if (!(contentJson instanceof String content)) {
				throw new IllegalArgumentException("`content` value has to be a string");
			}

			if (content.length() > 2000) {
				throw new IllegalArgumentException("`content` value cannot be more than 2000 characters");
			}

			builder.setContent(content);
		}

		if (builder.isEmpty()) {
			throw new IllegalArgumentException("Message cannot be empty");
		}

		return builder;
	}

	public static boolean isValid(Document json, boolean checkUrl) {
		try {
			MessageUtility.fromJson(json, checkUrl);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static boolean isWebhookEmbedEmpty(WebhookEmbed embed) {
		return MessageUtility.isEmpty(embed.getDescription())
			&& MessageUtility.isEmpty(embed.getImageUrl())
			&& MessageUtility.isEmpty(embed.getThumbnailUrl())
			&& MessageUtility.isFieldsEmpty(embed.getFields())
			&& MessageUtility.isAuthorEmpty(embed.getAuthor())
			&& MessageUtility.isTitleEmpty(embed.getTitle())
			&& MessageUtility.isFooterEmpty(embed.getFooter())
			&& embed.getTimestamp() == null;
	}

	private static boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean isTitleEmpty(WebhookEmbed.EmbedTitle title) {
		return title == null || MessageUtility.isEmpty(title.getText());
	}

	private static boolean isFooterEmpty(WebhookEmbed.EmbedFooter footer) {
		return footer == null || MessageUtility.isEmpty(footer.getText());
	}

	private static boolean isAuthorEmpty(WebhookEmbed.EmbedAuthor author) {
		return author == null || MessageUtility.isEmpty(author.getName());
	}

	private static boolean isFieldsEmpty(List<WebhookEmbed.EmbedField> fields) {
		if (fields.isEmpty()) {
			return true;
		}

		return fields.stream().allMatch(f -> MessageUtility.isEmpty(f.getName()) && MessageUtility.isEmpty(f.getValue()));
	}

	public static int getWebhookEmbedLength(WebhookEmbed embed) {
		int length = 0;

		String title = embed.getTitle() != null ? embed.getTitle().getText() : null;
		if (title != null) {
			length += title.length();
		}

		String description = embed.getDescription();
		if (description != null) {
			length += description.length();
		}

		String author = embed.getAuthor() != null ? embed.getAuthor().getName() : null;
		if (author != null) {
			length += author.length();
		}

		String footer = embed.getFooter() != null ? embed.getFooter().getText() : null;
		if (footer != null) {
			length += footer.length();
		}

		for (WebhookEmbed.EmbedField field : embed.getFields()) {
			length += field.getName().length() + field.getValue().length();
		}

		return length;
	}

	public static int getWebhookEmbedLength(List<WebhookEmbed> embeds) {
		return embeds.stream().mapToInt(MessageUtility::getWebhookEmbedLength).sum();
	}

	public static EmbedBuilder fromWebhookEmbed(WebhookEmbed embed) {
		EmbedBuilder builder = new EmbedBuilder();
		WebhookEmbed.EmbedTitle title = embed.getTitle();
		String description = embed.getDescription();
		String thumbnail = embed.getThumbnailUrl();
		WebhookEmbed.EmbedAuthor author = embed.getAuthor();
		WebhookEmbed.EmbedFooter footer = embed.getFooter();
		String image = embed.getImageUrl();
		List<WebhookEmbed.EmbedField> fields = embed.getFields();
		Integer color = embed.getColor();
		OffsetDateTime timestamp = embed.getTimestamp();

		if (title != null) {
			builder.setTitle(title.getText(), title.getUrl());
		}

		if (description != null) {
			builder.setDescription(description);
		}

		if (thumbnail != null) {
			builder.setThumbnail(thumbnail);
		}

		if (author != null) {
			builder.setAuthor(author.getName(), author.getUrl(), author.getIconUrl());
		}

		if (footer != null) {
			builder.setFooter(footer.getText(), footer.getIconUrl());
		}

		if (image != null) {
			builder.setImage(image);
		}

		if (!fields.isEmpty()) {
			fields.forEach(field -> builder.addField(field.getName(), field.getValue(), field.isInline()));
		}

		if (color != null) {
			builder.setColor(color);
		}

		if (timestamp != null) {
			builder.setTimestamp(timestamp);
		}

		return builder;
	}

	public static MessageCreateBuilder fromWebhookMessageAsBuilder(WebhookMessage message) {
		MessageCreateBuilder builder = new MessageCreateBuilder();

		List<WebhookEmbed> embeds = message.getEmbeds();
		if (!embeds.isEmpty()) {
			builder.setEmbeds(embeds.stream().map(MessageUtility::fromWebhookEmbed).map(EmbedBuilder::build).collect(Collectors.toList()));
		}

		builder.setContent(message.getContent());

		MessageAttachment[] attachments = message.getAttachments();
		if (attachments != null && attachments.length != 0) {
			for (MessageAttachment attachment : attachments) {
				builder.addFiles(FileUpload.fromData(attachment.getData(), attachment.getName()));
			}
		}

		return builder;
	}

	public static MessageCreateData fromWebhookMessage(WebhookMessage message) {
		return MessageUtility.fromWebhookMessageAsBuilder(message).build();
	}


	private static void keepFields(Document json, Set<String> whitelisted) {
		json.keySet().removeIf(key -> !whitelisted.contains(key));
	}

	public static void removeFields(Document json) {
		MessageUtility.keepFields(json, Set.of("embed", "content"));

		Object embedJson = json.get("embed");
		if (embedJson instanceof Document embed) {

			MessageUtility.keepFields(embed, Set.of("author", "title", "color", "fields", "description", "image", "thumbnail", "footer", "timestamp", "url"));

			Object author = embed.get("author");
			if (author instanceof Document) {
				MessageUtility.keepFields((Document) author, Set.of("name", "icon_url", "url"));
			}

			Object footer = embed.get("footer");
			if (footer instanceof Document) {
				MessageUtility.keepFields((Document) footer, Set.of("text", "icon_url"));
			}

			Object thumbnail = embed.get("thumbnail");
			if (thumbnail instanceof Document) {
				MessageUtility.keepFields((Document) thumbnail, Set.of("url"));
			}

			Object image = embed.get("image");
			if (image instanceof Document) {
				MessageUtility.keepFields((Document) image, Set.of("url"));
			}

			Object fields = embed.get("fields");
			if (fields instanceof List) {
				for (Object field : (List<?>) fields) {
					if (field instanceof Document) {
						MessageUtility.keepFields((Document) field, Set.of("name", "value", "inline"));
					}
				}
			}
		}
	}

}
