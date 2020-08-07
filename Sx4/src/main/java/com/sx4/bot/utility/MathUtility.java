package com.sx4.bot.utility;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public class MathUtility {
	
	private static final Random RANDOM = new Random();

	public static double getHeatIndex(double temperature, double humidity) {
		return -8.78469475556
			+ 1.61139411 * temperature
			+ 2.33854883889 * humidity
			+ -0.14611605 * temperature * humidity
			+ -0.012308094 * Math.pow(temperature, 2)
			+ -0.0164248277778 * Math.pow(humidity, 2)
			+ 0.002211732 * Math.pow(temperature, 2) * humidity
			+ 0.00072546 * temperature * Math.pow(humidity, 2)
			+ -0.000003582 * Math.pow(temperature, 2) * Math.pow(humidity, 2);
	}

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
