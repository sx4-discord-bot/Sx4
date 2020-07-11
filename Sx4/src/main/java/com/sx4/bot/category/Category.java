package com.sx4.bot.category;

import com.sx4.bot.core.Sx4Category;

public class Category {
	
	public static final Sx4Category ALL = new Sx4Category("All", "This module contains every command on the bot");

	public static final Sx4Category MODERATION = new Sx4Category("Moderation", "This module has commands which will help you keep rules in place in your server", Category.ALL, "Mod");
	public static final Sx4Category AUTO_MODERATION = new Sx4Category("Auto Moderation", "This modules has commands which will moderate your server automatically", Category.ALL, "Auto Mod");
	public static final Sx4Category SETTINGS = new Sx4Category("Settings", "This module has commands which will configure the bot around your server", Category.ALL);
	public static final Sx4Category NOTIFICATIONS = new Sx4Category("Notifications", "This modules has commands which will give you notifications for various things", Category.ALL);
	public static final Sx4Category LOGGING = new Sx4Category("Logging", "This module has commands which will help you log things which happen in your server", Category.ALL);
	public static final Sx4Category INFORMATION = new Sx4Category("Information", "This modules contains commands which give information about different discord entities", Category.ALL, "Info");
	public static final Sx4Category MANAGEMENT = new Sx4Category("Management", "This modules contains commands which give help you manage your server", Category.ALL);
	public static final Sx4Category DEVELOPER = new Sx4Category("Developer", "This modules contains commands which are for developers only", Category.ALL, "Dev");
	public static final Sx4Category MISC = new Sx4Category("Miscellaneous", "This module has a mix of commands which are useful in their own way", Category.ALL, "Misc");
	
	public static final Sx4Category[] ALL_ARRAY = {ALL, MODERATION, AUTO_MODERATION, NOTIFICATIONS, LOGGING, INFORMATION, MANAGEMENT, DEVELOPER, MISC};
	
}
