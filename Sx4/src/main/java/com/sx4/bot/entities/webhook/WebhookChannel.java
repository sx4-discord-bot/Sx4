package com.sx4.bot.entities.webhook;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.*;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import net.dv8tion.jda.api.requests.restaction.*;
import net.dv8tion.jda.internal.entities.channel.mixin.attribute.IWebhookContainerMixin;
import net.dv8tion.jda.internal.requests.restaction.WebhookActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class WebhookChannel implements IWebhookContainerMixin<WebhookChannel> {

	private final GuildMessageChannelUnion channel;

	public WebhookChannel(GuildMessageChannelUnion channel) {
		this.channel = channel;
	}

	public IWebhookContainer getWebhookChannel() {
		return (IWebhookContainer) (this.channel instanceof ThreadChannel ? ((ThreadChannel) this.channel).getParentChannel() : this.channel);
	}

	public CompletableFuture<Message> sendWebhookMessage(WebhookMessageCreateAction<Message> action) {
		return this.modifyWebhookRequest(action);
	}

	public CompletableFuture<Message> editWebhookMessage(WebhookMessageEditAction<Message> action) {
		return this.modifyWebhookRequest(action);
	}

	public CompletableFuture<Void> deleteWebhookMessage(WebhookMessageDeleteAction action) {
		return this.modifyWebhookRequest(action);
	}

	public <T> CompletableFuture<T> modifyWebhookRequest(AbstractWebhookMessageAction<T, ?> action) {
		if (this.channel instanceof ThreadChannel) {
			action = action.setThreadId(this.channel.getIdLong());
		}

		return action.submit();
	}


	@NotNull
	@Override
	public WebhookAction createWebhook(@NotNull String name) {
		Checks.notBlank(name, "Webhook name");
		name = name.trim();
		Checks.notEmpty(name, "Name");
		Checks.notLonger(name, 100, "Name");

		checkPermission(Permission.MANAGE_WEBHOOKS);

		return new WebhookActionImpl(this.getJDA(), this.getWebhookChannel(), name);
	}

	@NotNull
	@Override
	public ThreadChannel asThreadChannel() {
		return (ThreadChannel) this.channel;
	}

	@NotNull
	@Override
	public StageChannel asStageChannel() {
		return (StageChannel) this.channel;
	}

	@NotNull
	@Override
	public Category asCategory() {
		return (Category) this.channel;
	}

	@NotNull
	@Override
	public MessageChannel asMessageChannel() {
		return this.channel;
	}

	@NotNull
	@Override
	public GuildChannel asGuildChannel() {
		return this.channel;
	}

	@NotNull
	@Override
	public AudioChannel asAudioChannel() {
		return (AudioChannel) this.channel;
	}

	@NotNull
	@Override
	public PrivateChannel asPrivateChannel() {
		return (PrivateChannel) this.channel;
	}

	@NotNull
	@Override
	public TextChannel asTextChannel() {
		return (TextChannel) this.channel;
	}

	@NotNull
	@Override
	public NewsChannel asNewsChannel() {
		return (NewsChannel) this.channel;
	}

	@NotNull
	@Override
	public ForumChannel asForumChannel() {
		return (ForumChannel) this.channel;
	}

	@NotNull
	@Override
	public MediaChannel asMediaChannel() {
		return (MediaChannel) this.channel;
	}

	@NotNull
	@Override
	public IThreadContainer asThreadContainer() {
		return (IThreadContainer) this.channel;
	}

	@NotNull
	@Override
	public VoiceChannel asVoiceChannel() {
		return (VoiceChannel) this.channel;
	}

	@NotNull
	@Override
	public GuildMessageChannel asGuildMessageChannel() {
		return this.channel;
	}

	@NotNull
	@Override
	public StandardGuildChannel asStandardGuildChannel() {
		return (StandardGuildChannel) this.channel;
	}

	@NotNull
	@Override
	public StandardGuildMessageChannel asStandardGuildMessageChannel() {
		return (StandardGuildMessageChannel) this.channel;
	}

	@NotNull
	@Override
	public Guild getGuild() {
		return this.channel.getGuild();
	}

	@NotNull
	@Override
	public ChannelManager<?, ?> getManager() {
		return this.channel.getManager();
	}

	@Override
	public IPermissionContainer getPermissionContainer() {
		return this.channel.getPermissionContainer();
	}

	@Override
	public int compareTo(@NotNull GuildChannel o) {
		return this.channel.compareTo(o);
	}

	@NotNull
	@Override
	public String getName() {
		return this.channel.getName();
	}

	@NotNull
	@Override
	public ChannelType getType() {
		return this.channel.getType();
	}

	@NotNull
	@Override
	public JDA getJDA() {
		return this.channel.getJDA();
	}

	@Override
	public long getIdLong() {
		return this.channel.getIdLong();
	}

	@Override
	public WebhookChannel setName(String name) {
		return null;
	}

}
