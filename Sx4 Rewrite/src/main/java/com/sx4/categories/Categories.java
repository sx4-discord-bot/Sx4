package com.sx4.categories;

import com.jockie.bot.core.category.impl.CategoryImpl;

public class Categories {
		
	public static final CategoryImpl GENERAL = new CategoryImpl("General", null);
	public static final CategoryImpl MOD = new CategoryImpl("Mod", null);
	public static final CategoryImpl HELP = new CategoryImpl("Help", null);
	public static final CategoryImpl DEVELOPER = new CategoryImpl("Developer", null);
	public static final CategoryImpl FUN = new CategoryImpl("Fun", null);
	public static final CategoryImpl IMAGE = new CategoryImpl("Image", null);
	public static final CategoryImpl LOGS = new CategoryImpl("Logs", null);
	public static final CategoryImpl ANIMALS = new CategoryImpl("Animals", null);
	public static final CategoryImpl GIVEAWAY = new CategoryImpl("Giveaway", null);
	public static final CategoryImpl SELF_ROLES = new CategoryImpl("Selfroles", null);
	public static final CategoryImpl AUTO_ROLE = new CategoryImpl("Autorole", null);
	public static final CategoryImpl ECONOMY = new CategoryImpl("Economy", null);
	public static final CategoryImpl WELCOMER = new CategoryImpl("Welcomer", null);
	public static final CategoryImpl ANTI_INVITE = new CategoryImpl("Antiinivte", null);
	public static final CategoryImpl ANTI_LINK = new CategoryImpl("Antilink", null);
	
	public static final CategoryImpl[] ALL = {GENERAL, MOD, HELP, DEVELOPER, FUN, IMAGE, LOGS, ANIMALS, GIVEAWAY, SELF_ROLES, AUTO_ROLE, ECONOMY, WELCOMER, ANTI_INVITE, ANTI_LINK};
	public static final CategoryImpl[] ALL_PUBLIC = {GENERAL, MOD, FUN, IMAGE, LOGS, ANIMALS, GIVEAWAY, SELF_ROLES, AUTO_ROLE, ECONOMY, WELCOMER, ANTI_INVITE, ANTI_LINK};
	
}
