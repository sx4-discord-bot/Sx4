package com.sx4.bot.entities.utility;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TimeFormatter {

	private static final ChronoUnit[] CHRONO_UNITS = Arrays.copyOf(ChronoUnit.values(), ChronoUnit.values().length);
	static {
		Arrays.sort(CHRONO_UNITS, Comparator.comparing(ChronoUnit::getDuration).reversed());
	}

	private final Map<ChronoUnit, Map<Boolean, String>> units;
	private final int maxUnits;

	private TimeFormatter(Builder builder) {
		this.maxUnits = builder.getMaxUnits();
		this.units = builder.getSuffixes();
	}

	public String parse(Duration duration) {
		long seconds = duration.toSeconds();
		if (seconds < 0) {
			throw new IllegalArgumentException("duration cannot be a negative value");
		}

		if (seconds == 0) {
			Map<Boolean, String> secondsSuffix = this.units.get(ChronoUnit.SECONDS);
			if (secondsSuffix == null) {
				return "0 seconds";
			} else {
				return "0" + secondsSuffix.get(true);
			}
		}

		StringJoiner joiner = new StringJoiner(" ");
		int unitsUsed = 0;
		for (ChronoUnit chronoUnit : CHRONO_UNITS) {
			long secondsInTime = chronoUnit.getDuration().toSeconds();
			int amount = (int) Math.floor((double) seconds / secondsInTime);

			if (amount != 0) {
				Map<Boolean, String> suffix = this.units.get(chronoUnit);
				if (suffix != null) {
					joiner.add(amount + suffix.get(amount != 1));

					if (++unitsUsed == this.maxUnits) {
						return joiner.toString();
					}

					seconds -= amount * secondsInTime;
				}

				if (seconds == 0) {
					break;
				}
			}
		}

		return joiner.toString();
	}

	public String parse(long seconds) {
		return this.parse(Duration.ofSeconds(seconds));
	}

	public static class Builder {

		private final Map<ChronoUnit, Map<Boolean, String>> suffixes;
		private int maxUnits;

		public Builder() {
			this.maxUnits = -1;
			this.suffixes = new HashMap<>();
		}

		public Builder setMaxUnits(int maxUnits) {
			this.maxUnits = maxUnits;

			return this;
		}

		public int getMaxUnits() {
			return this.maxUnits;
		}

		private Builder addSuffix(ChronoUnit unit, String suffix, boolean plural) {
			this.suffixes.compute(unit, (key, value) -> {
				if (value == null) {
					Map<Boolean, String> suffixes = new HashMap<>();
					suffixes.put(plural, suffix);
					return suffixes;
				} else {
					value.put(plural, suffix);
					return value;
				}
			});

			return this;
		}

		public Builder addPluralSuffix(ChronoUnit unit, String suffix) {
			return this.addSuffix(unit, suffix, true);
		}

		public Builder addNonPluralSuffix(ChronoUnit unit, String suffix) {
			return this.addSuffix(unit, suffix, false);
		}

		public Builder addUniSuffix(ChronoUnit unit, String suffix) {
			this.addPluralSuffix(unit, suffix);
			this.addNonPluralSuffix(unit, suffix);

			return this;
		}

		public Map<ChronoUnit, Map<Boolean, String>> getSuffixes() {
			return this.suffixes;
		}

		public TimeFormatter build() {
			return new TimeFormatter(this);
		}

	}

}
