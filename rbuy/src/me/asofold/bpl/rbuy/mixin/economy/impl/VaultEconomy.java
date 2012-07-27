package me.asofold.bpl.rbuy.mixin.economy.impl;

public class VaultEconomy extends SimpleEconomy{
	net.milkbowl.vault.economy.Economy eco;
	
	public VaultEconomy(net.milkbowl.vault.economy.Economy eco){
		this.eco = eco;
		this.setCurrenciesFromFormats(new String[]{eco.format(1), eco.format(2)});
		this.addCurrencyAliases();
	}
	
	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		return eco.getBalance(playerName);
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return false;
		return eco.hasAccount(playerName);
	}

	@Override
	public String getImplementationName() {
		return "VaultEconomy(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.0.0";
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		try{
			net.milkbowl.vault.economy.EconomyResponse resp = eco.depositPlayer(playerName, amount);
			return resp.transactionSuccess();
		} catch(Throwable t){
			return false;
		}
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		try{
			net.milkbowl.vault.economy.EconomyResponse resp = eco.withdrawPlayer(playerName, amount);
			return resp.transactionSuccess();
		} catch(Throwable t){
			return false;
		}
	}
}
