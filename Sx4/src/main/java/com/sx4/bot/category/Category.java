package com.sx4.bot.category;

import com.sx4.bot.core.Sx4Category;

public class Category {

	public static final Sx4Category TASK = new Sx4Category("Tasks", "This module has commands which will keep you up to date with things");
	public static final Sx4Category LOGGING = new Sx4Category("Logging", "This module has commands which will help you log things which happen in your server");
	public static final Sx4Category MISC = new Sx4Category("Miscellaneous", "This module has a mix of commands which are useful in their own way", "Misc");
	
	public static final Sx4Category[] ALL = {TASK, LOGGING, MISC};
	
}
