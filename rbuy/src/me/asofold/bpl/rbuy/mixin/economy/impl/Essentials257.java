package me.asofold.bpl.rbuy.mixin.economy.impl;


public class Essentials257 extends SimpleEconomy {
	
	
	public Essentials257(){
		String[] formats = new String[]{com.earth2me.essentials.api.Economy.format(1), com.earth2me.essentials.api.Economy.format(2)};
		this.setCurrenciesFromFormats(formats);
		this.addCurrencyAliases();
	}
	
	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		try {
			return com.earth2me.essentials.api.Economy.hasEnough(playerName, amount);
		} catch (com.earth2me.essentials.api.UserDoesNotExistException e) {
			return false;
		}
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		return com.earth2me.essentials.api.Economy.playerExists(playerName);
	}

	@Override
	public String getImplementationName() {
		return "Essentials 2.5.7(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.0.2";
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		try {
			com.earth2me.essentials.api.Economy.add(playerName, amount);
			return true;
		} catch (com.earth2me.essentials.api.UserDoesNotExistException e) {
			return false;
		} catch (com.earth2me.essentials.api.NoLoanPermittedException e) {
			return false;
		}
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		try {
			com.earth2me.essentials.api.Economy.subtract(playerName, amount);
			return true;
		} catch (com.earth2me.essentials.api.UserDoesNotExistException e) {
			return false;
		} catch (com.earth2me.essentials.api.NoLoanPermittedException e) {
			return false;
		}	}

	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		try {
			return com.earth2me.essentials.api.Economy.getMoney(playerName);
		} catch (com.earth2me.essentials.api.UserDoesNotExistException e) {
			return 0.0;
		}
	}
	
	

}
