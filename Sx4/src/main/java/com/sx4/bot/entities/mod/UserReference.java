package com.sx4.bot.entities.mod;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class UserReference implements User {
	
	private final long id;
	
	private UserReference(long id) {
		this.id = id;
	}

	@NotNull
	@Override
	public String getName() {
		throw new UnsupportedOperationException();
	}

	@Nullable
	@Override
	public String getGlobalName() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public String getDiscriminator() {
		throw new UnsupportedOperationException();
	}

	@Nullable
	@Override
	public String getAvatarId() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public String getDefaultAvatarId() {
		throw new UnsupportedOperationException();
	}

	@Override
	public CacheRestAction<Profile> retrieveProfile() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public String getAsTag() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasPrivateChannel() {
		throw new UnsupportedOperationException();
	}

	@Override
	public CacheRestAction<PrivateChannel> openPrivateChannel() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public List<Guild> getMutualGuilds() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isBot() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isSystem() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public JDA getJDA() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public EnumSet<UserFlag> getFlags() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getFlagsRaw() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public String getAsMention() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getIdLong() {
		return this.id;
	}

	public static User fromId(long id) {
		return new UserReference(id);
	}

}
