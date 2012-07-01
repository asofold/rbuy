package me.asofold.bukkit.rbuy.mixin.economy.impl;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Server;

public class MultiCurrency extends SimpleEconomy{
	public MultiCurrency(){
		Set<String> all = me.ashtheking.currency.CurrencyList.getAllCurrencys();
		for (String c :all){
			this.addAcceptedCurrency(c);
		}
	}
	@Override
	public String getImplementationName() {
		return "MultiCurrency 0.11(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.0.2";
	}

	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		return me.ashtheking.currency.CurrencyList.hasEnough(playerName, amount, currency);
	}

	/**
	 * This checks Server.getPlayer and Server.getOfflinePlayer (!).
	 */
	@Override
	public boolean hasAccount(String playerName, String currency) {
		Server server = Bukkit.getServer();
		if ( server.getPlayerExact(playerName) != null) return true;
		try{
			if ( server.getOfflinePlayer(playerName) != null) return true;
		} catch (Throwable t){
			
		}
		return false;
	}
	@Override
	public boolean add(String playerName, double amount, String currency) {
		return me.ashtheking.currency.CurrencyList.add(playerName, amount, currency);
	}
	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		return me.ashtheking.currency.CurrencyList.subtract(playerName, amount, currency);
	}
	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		return me.ashtheking.currency.CurrencyList.getValue(currency, playerName);
	}

	

}
