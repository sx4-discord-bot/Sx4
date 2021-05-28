package com.sx4.bot.entities.argument;

import java.util.HashMap;
import java.util.Map;

public class AmountArgument {

	private static final Map<String, AmountArgument> DECIMAL_MAPPINGS = new HashMap<>();
	static {
		AmountArgument.DECIMAL_MAPPINGS.put("all", new AmountArgument(1D));
		AmountArgument.DECIMAL_MAPPINGS.put("full", new AmountArgument(1D));
		AmountArgument.DECIMAL_MAPPINGS.put("half", new AmountArgument(0.5D));
		AmountArgument.DECIMAL_MAPPINGS.put("quarter", new AmountArgument(0.25D));
	}

	private final Double decimal;
	private final Long amount;

	public AmountArgument(long amount) {
		this.amount = amount;
		this.decimal = null;
	}

	public AmountArgument(double decimal) {
		this.decimal = decimal;
		this.amount = null;
	}

	public Double getDecimal() {
		return this.decimal;
	}

	public Long getAmount() {
		return this.amount;
	}

	public long getEffectiveAmount(long amount) {
		if (this.amount != null) {
			return this.amount;
		} else {
			return (long) Math.ceil(this.decimal * amount);
		}
	}

	public boolean hasDecimal() {
		return this.decimal != null;
	}

	public boolean hasAmount() {
		return this.amount != null;
	}

	public static AmountArgument parse(String content) {
		if (content.charAt(content.length() - 1) == '%') {
			String amount = content.substring(0, content.length() - 1);
			try {
				double decimal = Double.parseDouble(amount);
				if (decimal <= 0) {
					return null;
				}

				return new AmountArgument(decimal / 100D);
			} catch (NumberFormatException e) {
				return null;
			}
		}

		try {
			long amount = Long.parseLong(content);
			if (amount <= 0) {
				return null;
			}

			return new AmountArgument(amount);
		} catch (NumberFormatException e) {
			return AmountArgument.DECIMAL_MAPPINGS.get(content.toLowerCase());
		}
	}

}
