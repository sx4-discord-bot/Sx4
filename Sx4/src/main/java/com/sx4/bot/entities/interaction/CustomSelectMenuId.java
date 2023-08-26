package com.sx4.bot.entities.interaction;

import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.StringJoiner;

public class CustomSelectMenuId extends CustomInteractionId {

	private final long[] owners;

	private CustomSelectMenuId(int type, long[] owners, String[] arguments) {
		super(type, arguments);

		this.owners = owners;
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

	public String getId() {
		StringJoiner joiner = new StringJoiner(",").setEmptyValue("0");
		for (long owner : this.owners) {
			joiner.add(Long.toString(owner));
		}

		return this.getType() + ":" + joiner + (this.getArguments().length == 0 ? "" : ":" + String.join(":", this.getArguments()));
	}

	public StringSelectMenu.Builder asMenuBuilder(String placeholder) {
		return StringSelectMenu.create(this.getId()).setPlaceholder(placeholder);
	}

	public static CustomSelectMenuId fromId(String id) {
		String[] split = id.split(":");
		if (split.length < 2) {
			throw new IllegalArgumentException("Invalid custom id");
		}

		String[] splitOwners = split[1].split(",");
		long[] owners = new long[splitOwners.length];
		for (int i = 0; i < splitOwners.length; i++) {
			owners[i] = Long.parseLong(splitOwners[i]);
		}

		int type = Integer.parseInt(split[0]);

		String[] arguments = new String[split.length - 2];
		System.arraycopy(split, 2, arguments, 0, split.length - 2);

		return new CustomSelectMenuId(type, owners, arguments);
	}

	public static class Builder extends CustomInteractionId.Builder<CustomSelectMenuId, CustomSelectMenuId.Builder> {

		private long[] owners;

		public CustomSelectMenuId.Builder setType(SelectMenuType type) {
			return this.setType(type.getId());
		}

		public CustomSelectMenuId.Builder setOwners(long... owners)  {
			this.owners = owners;

			return this;
		}

		public CustomSelectMenuId build() {
			return new CustomSelectMenuId(this.type, this.owners == null ? new long[0] : this.owners, this.arguments == null ? new String[0] : this.arguments);
		}

	}
	
}
