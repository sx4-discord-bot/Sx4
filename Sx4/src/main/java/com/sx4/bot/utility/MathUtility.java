package com.sx4.bot.utility;

import java.util.function.Function;

public class MathUtility {

	public static double sigma(int start, int end, Function<Integer, Double> function) {
		double value = 0D;
		for (int i = start; i <= end; i++) {
			value += function.apply(i);
		}
		
		return value;
	}
	
	public static double log(int power, double value) {
		return Math.log(value) / Math.log(power);
	}
	
}
