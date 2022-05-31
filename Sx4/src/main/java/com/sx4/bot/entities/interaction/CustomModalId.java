package com.sx4.bot.entities.interaction;

public class CustomModalId extends CustomInteractionId {

	private CustomModalId(int type, String[] arguments) {
		super(type, arguments);
	}

	public String getId() {
		return this.getType() + (this.arguments.length == 0 ? "" : ":" + String.join(":", this.arguments));
	}

	public static CustomModalId fromId(String id) {
		String[] split = id.split(":");

		int type = Integer.parseInt(split[0]);

		String[] arguments = new String[split.length - 1];
		System.arraycopy(split, 1, arguments, 0, split.length - 1);

		return new CustomModalId(type, arguments);
	}

	public static class Builder extends CustomInteractionId.Builder {

		public CustomModalId.Builder setType(ModalType type) {
			this.type = type.getId();

			return this;
		}

		public CustomModalId build() {
			return new CustomModalId(this.type, this.arguments == null ? new String[0] : this.arguments);
		}

	}

}
