package com.sx4.bot.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.economy.item.ItemStack;

public class EconomyUtility {

	public static Bson getItemUpdate(List<ItemStack<?>> add, List<ItemStack<?>> remove) {
		List<Bson> and = new ArrayList<>(), concat = new ArrayList<>(), check = new ArrayList<>();
		for (ItemStack<?> item : remove) {
			Bson newAmount = Operators.subtract(Operators.first(Operators.map(Operators.filter("$economy.items", Operators.eq("$$this.name", item.getName())), "$$this.amount")), item.getAmount());
		    concat.add(Operators.cond(Operators.or(Operators.extinct("$economy.items"), Operators.isEmpty(Operators.filter("$economy.items", Operators.eq("$$this.name", item.getName())))), List.of(new Document("name", item.getName()).append("amount", item.getAmount())), Operators.cond(Operators.eq(newAmount, 0L), Collections.EMPTY_LIST, List.of(new Document("name", item.getName()).append("amount", newAmount)))));
		    
		    check.add(Operators.lt(newAmount, 0L));
		    and.add(Operators.ne("$$this.name", item.getName()));
		}
		
		for (ItemStack<?> item : add) {
			concat.add(Operators.cond(Operators.or(Operators.extinct("$economy.items"), Operators.isEmpty(Operators.filter("$economy.items", Operators.eq("$$this.name", item.getName())))), List.of(new Document("name", item.getName()).append("amount", item.getAmount())), List.of(new Document("name", item.getName()).append("amount", Operators.add(item.getAmount(), Operators.first(Operators.map(Operators.filter("$economy.items", Operators.eq("$$this.name", item.getName())), "$$this.amount")))))));
		    
		    and.add(Operators.ne("$$this.name", item.getName()));
		}
		
		concat.add(Operators.cond(Operators.exists("$economy.items"), Operators.filter("$economy.items", Operators.and(and)), Collections.EMPTY_LIST));
		
		return Operators.set("economy.items", Operators.cond(Operators.or(check), "$economy.items", Operators.concatArrays(concat)));
	}
	
	public static Bson getBalanceUpdate(long amount) {
		return Operators.set("economy.balance", Operators.cond(Operators.or(Operators.extinct("$economy.balance"), Operators.lt("$economy.balance", amount)), "$economy.balance", Operators.subtract("$economy.balance", amount)));
	}
	
	// When the user gives a percentage as an argument eg: 50%
	public static Bson getBalanceUpdate(double decimal) {
		return Operators.set("economy.balance", Operators.subtract("$economy.balance", Operators.toLong(Operators.floor(Operators.multiply(decimal, "$economy.balance")))));
	}
	
}
