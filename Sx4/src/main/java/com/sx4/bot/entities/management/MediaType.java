package com.sx4.bot.entities.management;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public enum MediaType {

	JPEG(0, "jpeg"),
	JPG(1, "jpg"),
	PNG(2, "png"),
	GIF(3, "gif"),
	MP4(4, "mp4"),
	WEBP(5, "webp"),
	MOV(6, "quicktime");

	public static final long ALL = MediaType.getRaw(MediaType.values());

	private final long raw;

	private final String extension;

	private MediaType(int offset, String extension) {
		this.raw = 1L << offset;
		this.extension = extension;
	}

	public String getExtension() {
		return this.extension;
	}

	public long getRaw() {
		return this.raw;
	}

	public static EnumSet<MediaType> getMediaTypes(long raw) {
		EnumSet<MediaType> types = EnumSet.noneOf(MediaType.class);
		for (MediaType type : MediaType.values()) {
			if ((raw & type.getRaw()) == type.getRaw()) {
				types.add(type);
			}
		}

		return types;
	}

	public static long getRaw(MediaType... mediaTypes) {
		return MediaType.getRaw(Arrays.asList(mediaTypes));
	}

	public static long getRaw(Collection<MediaType> mediaTypes) {
		long raw = 0L;
		for (MediaType type : mediaTypes) {
			raw |= type.getRaw();
		}

		return raw;
	}

}
