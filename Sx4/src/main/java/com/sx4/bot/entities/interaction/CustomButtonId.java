package com.sx4.bot.entities.interaction;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.time.Clock;
import java.util.StringJoiner;

public class CustomButtonId extends CustomInteractionId {

	private final long[] owners;
	private final long expiry;

	private CustomButtonId(int type, long[] owners, long expiry, String[] arguments) {
		super(type, arguments);

		this.owners = owners;
		this.expiry = expiry;
	}

	public long getFirstOwnerId() {
		return this.owners[0];
	}

	public long[] getOwners() {
		return this.owners;
	}

	public boolean isOwner(long ownerId) {
		for (long owner : this.owners) {
			if (owner == ownerId) {
				return true;
			}
		}

		return false;
	}

	public long getExpiry() {
		return this.expiry;
	}

	public boolean isExpired() {
		return this.expiry != 0 && Clock.systemUTC().instant().getEpochSecond() > this.expiry;
	}

	public String getId() {
		StringJoiner joiner = new StringJoiner(",").setEmptyValue("0");
		for (long owner : this.owners) {
			joiner.add(Long.toString(owner));
		}

		return this.getType() + ":" + joiner + ":" + this.getExpiry() + (this.getArguments().length == 0 ? "" : ":" + String.join(":", this.getArguments()));
	}

	public Button asButton(ButtonStyle style, String label) {
		return Button.of(style, this.getId(), label);
	}

	public static CustomButtonId fromId(String id) {
		String[] split = id.split(":");
		if (split.length < 3) {
			return null;
		}

		String[] splitOwners = split[1].split(",");
		long[] owners = new long[splitOwners.length];
		for (int i = 0; i < splitOwners.length; i++) {
			owners[i] = Long.parseLong(splitOwners[i]);
		}

		int type = Integer.parseInt(split[0]);
		long expiry = Long.parseLong(split[2]);

		String[] arguments = new String[split.length - 3];
		System.arraycopy(split, 3, arguments, 0, split.length - 3);

		return new CustomButtonId(type, owners, expiry, arguments);
	}

	public static class Builder extends CustomInteractionId.Builder<CustomButtonId, Builder> {

		private long[] owners;
		private long expiry;

		public Builder setType(ButtonType type) {
			return this.setType(type.getId());
		}

		public Builder setOwners(long... owners)  {
			this.owners = owners;

			return this;
		}

		public Builder setExpiry(long expiry) {
			this.expiry = expiry;
			
			return this;
		}
		
		public Builder setInfinite() {
			return this.setExpiry(0);
		}
		
		public Builder setTimeout(long seconds) {
			return this.setExpiry(Clock.systemUTC().instant().getEpochSecond() + seconds);
		}
		
		public CustomButtonId build() {
			return new CustomButtonId(this.type, this.owners == null ? new long[0] : this.owners, this.expiry, this.arguments == null ? new String[0] : this.arguments);
		}

	}

}
