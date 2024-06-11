package com.sx4.bot.utility;

import com.sx4.bot.entities.interaction.ButtonType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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

	private static MessageEmbed.Footer footerFromJson(Document json, boolean checkUrl, String field) {
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

		return text == null ? null : new MessageEmbed.Footer(text, iconUrl, null);
	}

	private static OffsetDateTime timestampFromJson(Document json, boolean checkTimestamp, String field) {
		Object timestampJson = json.get("timestamp");
		if (timestampJson instanceof OffsetDateTime timestamp) {
			return timestamp;
		}

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

	private static MessageEmbed.AuthorInfo authorFromJson(Document json, boolean checkUrl, String field) {
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

		return name == null ? null : new MessageEmbed.AuthorInfo(name, authorUrl, iconUrl, null);
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

	private static List<MessageEmbed.Field> fieldsFromJson(Document json, String field) {
		Object fieldsJson = json.get("fields");
		if (!(fieldsJson instanceof List<?> fieldsData)) {
			throw new IllegalArgumentException("`" + field + ".fields` value has to be an array");
		}

		int length = fieldsData.size();
		if (length > 25) {
			throw new IllegalArgumentException("You can only have 25 fields in `" + field + "`");
		}

		List<MessageEmbed.Field> fields = new ArrayList<>();
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
				fields.add(new MessageEmbed.Field(name, value, inline));
			}
		}

		return fields;
	}

	private static MessageEmbed embedFromJson(Document json, boolean checkValidity, String field) {
		boolean titleExists = json.containsKey("title");

		String title = titleExists ? MessageUtility.titleFromJson(json, field) : null;
		String url = titleExists ? MessageUtility.urlFromJson(json, checkValidity, field) : null;
		String description = json.containsKey("description") ? MessageUtility.descriptionFromJson(json, field) : null;
		int colour = json.containsKey("color") ? MessageUtility.colourFromJson(json, field) : Role.DEFAULT_COLOR_RAW;
		OffsetDateTime timestamp = json.containsKey("timestamp") ? MessageUtility.timestampFromJson(json, checkValidity, field) : null;
		MessageEmbed.Footer footer = json.containsKey("footer") ? MessageUtility.footerFromJson(json, checkValidity, field) : null;
		String thumbnail = json.containsKey("thumbnail") ? MessageUtility.thumbnailFromJson(json, checkValidity, field) : null;
		String image = json.containsKey("image") ? MessageUtility.imageFromJson(json, checkValidity, field) : null;
		MessageEmbed.AuthorInfo author = json.containsKey("author") ? MessageUtility.authorFromJson(json, checkValidity, field) : null;
		List<MessageEmbed.Field> fields = json.containsKey("fields") ? MessageUtility.fieldsFromJson(json, field) : Collections.emptyList();

		MessageEmbed embed = new MessageEmbed(url, title, description, EmbedType.RICH, timestamp, colour, thumbnail == null ? null : new MessageEmbed.Thumbnail(thumbnail, null, 0, 0), null, author, null, footer, image == null ? null : new MessageEmbed.ImageInfo(image, null, 0, 0), fields);
		if (embed.isEmpty()) {
			throw new IllegalArgumentException("`" + field + "` cannot be empty");
		}

		if (!embed.isSendable()) {
			throw new IllegalArgumentException("`" + field + "` total length cannot be more than 6000 characters");
		}

		return embed;
	}

	private static List<SelectOption> selectOptionsFromJson(List<?> options, String field) {
		if (options.isEmpty()) {
			throw new IllegalArgumentException("`" + field + "` cannot be empty");
		}

		if (options.size() > SelectMenu.OPTIONS_MAX_AMOUNT) {
			throw new IllegalArgumentException("`" + field + "` cannot be more than " + SelectMenu.OPTIONS_MAX_AMOUNT + " options");
		}

		List<SelectOption> optionList = new ArrayList<>();
		for (int i = 0; i < options.size(); i++) {
			Object optionJson = options.get(i);
			if (!(optionJson instanceof Document option)) {
				throw new IllegalArgumentException("`" + field + "." + i + "` value has to be a json object");
			}

			Object valueJson = option.get("value");
			if (!(valueJson instanceof String value)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".value` value has to be a string");
			}

			Object labelJson = option.get("label");
			if (!(labelJson instanceof String label)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".label` value has to be a string");
			}

			Object descriptionJson = option.get("description");
			if (descriptionJson != null && !(descriptionJson instanceof String)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".description` value has to be a string");
			}

			Object defaultJson = option.get("default");
			if (defaultJson != null && !(defaultJson instanceof Boolean)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".default` value has to be a boolean");
			}

			SelectOption optionData = SelectOption.of(label, value);
			if (descriptionJson != null) {
				optionData = optionData.withDescription((String) descriptionJson);
			}

			if (defaultJson != null) {
				optionData = optionData.withDefault((boolean) defaultJson);
			}

			optionList.add(optionData);
		}

		return optionList;
	}

	private static List<ItemComponent> componentsFromJson(List<?> components, String field, boolean checkUrl) {
		List<ItemComponent> componentList = new ArrayList<>();
		for (int i = 0; i < components.size(); i++) {
			Object componentJson = components.get(i);
			if (!(componentJson instanceof Document component)) {
				throw new IllegalArgumentException("`" + field + "." + i + "` value has to be a json object");
			}

			Object typeJson = component.get("type");
			if (!(typeJson instanceof Integer type)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".type` value has to be an integer");
			}

			Component.Type componentType = Component.Type.fromKey(type);
			if (components.size() > componentType.getMaxPerRow()) {
				throw new IllegalArgumentException("`" + field + "` has too many components");
			}

			Object disabledJson = component.get("disabled");
			if (disabledJson != null && !(disabledJson instanceof Boolean)) {
				throw new IllegalArgumentException("`" + field + "." + i + ".disabled` value has to be a boolean");
			}

			boolean disabled = disabledJson != null && (boolean) disabledJson;

			ItemComponent componentObject;
			if (componentType == Component.Type.BUTTON) {
				Object styleJson = component.get("style");
				if (!(styleJson instanceof Integer style)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".style` value has to be an integer");
				}

				ButtonStyle buttonStyle = ButtonStyle.fromKey(style);
				if (buttonStyle == ButtonStyle.UNKNOWN) {
					throw new IllegalArgumentException("`" + field + "." + i + ".style` value is not a valid button style");
				}

				Object labelJson = component.get("label");
				if (!(labelJson instanceof String label)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".label` value has to be a string");
				}

				if (label.length() > Button.LABEL_MAX_LENGTH) {
					throw new IllegalArgumentException("`" + field + "." + i + ".label` can only be " + Button.LABEL_MAX_LENGTH + " characters long");
				}

				String id;
				if (buttonStyle == ButtonStyle.LINK) {
					Object urlJson = component.get("url");
					if (!(urlJson instanceof String url)) {
						throw new IllegalArgumentException("`" + field + "." + i + ".url` value has to be a string");
					}

					if (url.length() > Button.URL_MAX_LENGTH) {
						throw new IllegalArgumentException("`" + field + "." + i + ".url` can only be " + Button.URL_MAX_LENGTH + " characters long");
					}

					if (checkUrl && !EmbedBuilder.URL_PATTERN.matcher(url).matches()) {
						throw new IllegalArgumentException("`" + field + "." + i + ".url` is not a valid url");
					}

					id = url;
				} else {
					Object idJson = component.get("custom_id");
					if (!(idJson instanceof String customId)) {
						throw new IllegalArgumentException("`" + field + "." + i + ".custom_id` value has to be a string");
					}

					if (customId.length() > Button.ID_MAX_LENGTH - 3) {
						throw new IllegalArgumentException("`" + field + "." + i + ".custom_id` can only be " + (Button.ID_MAX_LENGTH - 3) + " characters long");
					}

					id = ButtonType.TRIGGER_COMPONENT_CLICKED.getId() + ":" + customId;
				}

				componentObject = Button.of(buttonStyle, id, label).withDisabled(disabled);
			} else if (componentType == Component.Type.STRING_SELECT) {
				Object idJson = component.get("custom_id");
				if (!(idJson instanceof String id)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".custom_id` value has to be a string");
				}

				if (id.length() > Button.ID_MAX_LENGTH - 3) {
					throw new IllegalArgumentException("`" + field + "." + i + ".custom_id` can only be " + (Button.ID_MAX_LENGTH - 3) + " characters long");
				}

				id = ButtonType.TRIGGER_COMPONENT_CLICKED.getId() + ":" + id;

				Object placeholderJson = component.get("placeholder");
				if (placeholderJson != null && !(placeholderJson instanceof String)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".placeholder` value has to be a string");
				}

				if (placeholderJson != null && ((String) placeholderJson).length() > SelectMenu.PLACEHOLDER_MAX_LENGTH) {
					throw new IllegalArgumentException("`" + field + "." + i + ".placeholder` can only be " + SelectMenu.PLACEHOLDER_MAX_LENGTH + " characters long");
				}

				Object minValuesJson = component.get("min_values");
				if (minValuesJson != null && !(minValuesJson instanceof Integer)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".min_values` value has to be an integer");
				}

				Object maxValuesJson = component.get("max_values");
				if (maxValuesJson != null && !(maxValuesJson instanceof Integer)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".max_values` value has to be an integer");
				}

				Object optionsJson = component.get("options");
				if (!(optionsJson instanceof List options)) {
					throw new IllegalArgumentException("`" + field + "." + i + ".options` value has to be a list");
				}

				List<SelectOption> optionList = MessageUtility.selectOptionsFromJson(options, field + "." + i + ".options");

				StringSelectMenu.Builder menu = StringSelectMenu.create(id)
					.addOptions(optionList);

				if (minValuesJson != null) {
					menu.setMaxValues((int) minValuesJson);
				}

				if (maxValuesJson != null) {
					menu.setMaxValues((int) maxValuesJson);
				}

				if (placeholderJson != null) {
					menu.setPlaceholder((String) placeholderJson);
				}

				componentObject = menu.build();
			} else {
				throw new IllegalArgumentException("`" + field + "." + i + ".type` value can only be 2 (BUTTON) or 3 (STRING_SELECT)");
			}

			componentList.add(componentObject);
		}

		return componentList;
	}

	private static List<ActionRow> actionRowsFromJson(List<?> actionRows, boolean checkUrl) {
		if (actionRows.size() > 5) {
			throw new IllegalArgumentException("`components` can only contain 5 action rows");
		}

		List<ActionRow> actionRowList = new ArrayList<>();
		for (int i = 0; i < actionRows.size(); i++) {
			Object actionRowJson = actionRows.get(i);
			if (!(actionRowJson instanceof Document actionRow)) {
				throw new IllegalArgumentException("`components." + i + "` value has to be a json object");
			}

			Object typeJson = actionRow.get("type");
			if (!(typeJson instanceof Integer type)) {
				throw new IllegalArgumentException("`components." + i + ".type` value has to be an integer");
			}

			if (type != 1) {
				throw new IllegalArgumentException("`components." + i + ".type` value can only be 1 (ACTION_ROW)");
			}

			Object componentsJson = actionRow.get("components");
			if (!(componentsJson instanceof List components)) {
				throw new IllegalArgumentException("`components.`" + i + ".components value has to be a list");
			}

			List<ItemComponent> componentList = MessageUtility.componentsFromJson(components, "components." + i + ".components", checkUrl);
			actionRowList.add(ActionRow.of(componentList));
		}

		return actionRowList;
	}

	public static MessageEditBuilder fromEditJson(Document json, boolean checkValidity) {
		return MessageUtility.fromJson(json, checkValidity, MessageEditBuilder::new);
	}

	public static MessageCreateBuilder fromCreateJson(Document json, boolean checkValidity) {
		return MessageUtility.fromJson(json, checkValidity, MessageCreateBuilder::new);
	}

	public static <Builder extends AbstractMessageBuilder<?, Builder>> Builder fromJson(Document json, boolean checkValidity, Supplier<Builder> supplier) {
		Builder builder = supplier.get();
		if (json.containsKey("embed")) {
			Object embedJson = json.get("embed");
			if (!(embedJson instanceof Document embedData)) {
				throw new IllegalArgumentException("`embed` value has to be a json object");
			}

			MessageEmbed embed = MessageUtility.embedFromJson(embedData, checkValidity, "embed");

			builder.setEmbeds(embed);
		} else if (json.containsKey("embeds")) {
			Object embedsField = json.get("embed");
			if (!(embedsField instanceof List embedsData)) {
				throw new IllegalArgumentException("`embeds` value has to be a list");
			}

			if (embedsData.size() > 10) {
				throw new IllegalArgumentException("`embeds` cannot have more than 10 embeds");
			}

			List<MessageEmbed> embeds = new ArrayList<>();
			for (int i = 0; i < embedsData.size(); i++) {
				Object embedJson = embedsData.get(i);
				if (!(embedJson instanceof Document embedData)) {
					throw new IllegalArgumentException("`embeds." + i + "` value has to be a json object");
				}

				MessageEmbed embed = MessageUtility.embedFromJson(embedData, checkValidity, "embeds." + i);

				embeds.add(embed);
			}

			if (!embeds.isEmpty()) {
				builder.setEmbeds(embeds);
			}
		}

		if (json.containsKey("content")) {
			Object contentJson = json.get("content");
			if (!(contentJson instanceof String content)) {
				throw new IllegalArgumentException("`content` value has to be a string");
			}

			if (content.length() > Message.MAX_CONTENT_LENGTH) {
				throw new IllegalArgumentException("`content` value cannot be more than " + Message.MAX_CONTENT_LENGTH + " characters");
			}

			builder.setContent(content);
		}

		if (json.containsKey("components")) {
			Object actionRowsJson = json.get("components");
			if (!(actionRowsJson instanceof List actionRows)) {
				throw new IllegalArgumentException("`components` value has to be a list");
			}

			List<ActionRow> actionRowList = MessageUtility.actionRowsFromJson(actionRows, checkValidity);
			if (!actionRowList.isEmpty()) {
				builder.setComponents(actionRowList);
			}
		}

		if (json.containsKey("file")) {
			Object fileJson = json.get("file");
			if (!(fileJson instanceof Document file)) {
				throw new IllegalArgumentException("`file` value has to be a json object");
			}

			if (file.isEmpty()) {
				builder.setFiles();
			} else {
				Object nameJson = file.get("name");
				if (!(nameJson instanceof String name)) {
					throw new IllegalArgumentException("`file.name` value has to be a string");
				}

				Object dataJson = file.get("data");
				byte[] data;
				if (dataJson instanceof String dataString) {
					data = dataString.getBytes(StandardCharsets.UTF_8);
				} else if (dataJson instanceof byte[] bytes) {
					data = bytes;
				} else {
					throw new IllegalArgumentException("`file.data` value has to be of types bytes or string");
				}

				builder.setFiles(FileUpload.fromData(data, name));
			}
		}

		if (builder.isEmpty()) {
			throw new IllegalArgumentException("Message cannot be empty");
		}

		return builder;
	}

	public static boolean isValid(Document json, boolean checkValidity) {
		try {
			MessageUtility.fromCreateJson(json, checkValidity);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static MessageEmbed combineEmbeds(MessageEmbed firstEmbed, MessageEmbed secondEmbed) {
		EmbedBuilder builder = new EmbedBuilder(firstEmbed);

		String title = secondEmbed.getTitle();
		if (title != null) {
			builder.setTitle(title);
		}

		String url = secondEmbed.getUrl();
		if (url != null) {
			builder.setUrl(url);
		}

		MessageEmbed.AuthorInfo author = secondEmbed.getAuthor();
		if (author != null) {
			builder.setAuthor(author.getName(), author.getUrl(), author.getIconUrl());
		}

		MessageEmbed.ImageInfo image = secondEmbed.getImage();
		if (image != null) {
			builder.setImage(image.getUrl());
		}

		MessageEmbed.Thumbnail thumbnail = secondEmbed.getThumbnail();
		if (thumbnail != null) {
			builder.setThumbnail(thumbnail.getUrl());
		}

		List<MessageEmbed.Field> fields = secondEmbed.getFields();
		if (!fields.isEmpty()) {
			fields.forEach(builder::addField);
		}

		String description = secondEmbed.getDescription();
		if (description != null) {
			builder.setDescription(description);
		}

		MessageEmbed.Footer footer = secondEmbed.getFooter();
		if (footer != null) {
			builder.setFooter(footer.getText(), footer.getIconUrl());
		}

		OffsetDateTime timestamp = secondEmbed.getTimestamp();
		if (timestamp != null) {
			builder.setTimestamp(timestamp);
		}

		int colour = secondEmbed.getColorRaw();
		if (colour != Role.DEFAULT_COLOR_RAW) {
			builder.setColor(colour);
		}

		return builder.build();
	}

	public static int getEmbedLength(MessageEmbed embed) {
		int length = 0;

		String title = embed.getTitle() != null ? embed.getTitle() : null;
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

		for (MessageEmbed.Field field : embed.getFields()) {
			length += field.getName().length() + field.getValue().length();
		}

		return length;
	}

	public static int getEmbedLength(List<MessageEmbed> embeds) {
		return embeds.stream().mapToInt(MessageUtility::getEmbedLength).sum();
	}

	private static void keepFields(Document json, Set<String> whitelisted) {
		json.keySet().removeIf(key -> !whitelisted.contains(key));
	}

	private static void removeComponentFields(List<?> components) {
		for (Object componentJson : components) {
			if (componentJson instanceof Document component) {
				Object typeJson = component.get("type");
				if (typeJson instanceof Integer type) {
					if (type == Component.Type.ACTION_ROW.getKey()) {
						MessageUtility.keepFields(component, Set.of("type", "components"));
						Object actionRowComponentsJson = component.get("components");
						if (actionRowComponentsJson instanceof List actionRowComponents) {
							MessageUtility.removeComponentFields(actionRowComponents);
						}
					} else if (type == Component.Type.BUTTON.getKey()) {
						MessageUtility.keepFields(component, Set.of("type", "label", "url", "custom_id", "style", "disabled"));
					} else if (type == Component.Type.STRING_SELECT.getKey()) {
						MessageUtility.keepFields(component, Set.of("type", "custom_id", "options", "placeholder", "min_values", "max_values", "disabled"));

						Object optionsJson = component.get("options");
						if (optionsJson instanceof List options) {
							for (Object optionJson : options) {
								if (optionJson instanceof Document option) {
									MessageUtility.keepFields(option, Set.of("label", "value", "default", "description"));
								}
							}
						}
					}
				}
			}
		}
	}

	private static void removeFileFields(Document file) {
		MessageUtility.keepFields(file, Set.of("name", "data"));
	}

	private static void removeEmbedFields(Document embed) {
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

	public static void removeFields(Document json) {
		MessageUtility.keepFields(json, Set.of("embed", "embeds", "content", "components", "file"));

		Object embedJson = json.get("embed");
		Object embedsData = json.get("embeds");
		if (embedJson instanceof Document embed) {
			MessageUtility.removeEmbedFields(embed);
			json.remove("embeds");
		} else if (embedsData instanceof List embeds) {
			for (Object embedData : embeds) {
				if (embedData instanceof Document embed) {
					MessageUtility.removeEmbedFields(embed);
				}
			}
		}

		Object componentsJson = json.get("components");
		if (componentsJson instanceof List components) {
			MessageUtility.removeComponentFields(components);
		}

		Object fileJson = json.get("file");
		if (fileJson instanceof Document file) {
			MessageUtility.removeFileFields(file);
		}
	}

}
