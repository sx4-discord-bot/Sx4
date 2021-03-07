package com.sx4.bot.utility;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MathUtility {
	
	public static final SecureRandom RANDOM = new SecureRandom();

	public static double sigma(int start, int end, Function<Integer, Double> function) {
		double value = 0D;
		for (int i = start; i <= end; i++) {
			value += function.apply(i);
		}
		
		return value;
	}
	
	public static double log(double value, int power) {
		return Math.log(value) / Math.log(power);
	}
	
	public static <Type> Set<Type> randomSample(List<Type> list, int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount cannot be less than 0");
		}
		
		Set<Type> copy = new HashSet<>(list);
		if (amount > copy.size()) {
			throw new IllegalArgumentException("Amounts cannot be more than distinct version of list");
		}

		int size = list.size();
		
		boolean remove = amount > Math.floorDiv(size, 2);
		Set<Type> sample = remove ? copy : new HashSet<>(amount);
		
		while (sample.size() != amount) {
			Type object = list.get(MathUtility.RANDOM.nextInt(size));
			if (remove) {
				sample.remove(object);
			} else {
				sample.add(object);
			}
		}
		
		return sample;
	}
	
}
