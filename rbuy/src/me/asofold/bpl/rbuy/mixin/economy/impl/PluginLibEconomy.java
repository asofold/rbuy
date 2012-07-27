package me.asofold.bpl.rbuy.mixin.economy.impl;




/**
 * Extends SimpleEconomy for the messaging of the transfer method.
 * Better would be to use transaction, though.
 * @author dummy
 *
 */
public class PluginLibEconomy extends SimpleEconomy {
	
	public PluginLibEconomy(){
//		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
//		manager.
		// TODO: getAllAcceptedCurrencies
	}

	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		if (!this.isAcceptedCurrency(currency)) return false;
		if (this.hasAccount(playerName, currency)){
			double balance = manager.getBalance(playerName, currency);
			return balance >= amount;
		} else return false;
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		// TODO: hasAccount does not work properly
		return manager.hasAccount(playerName, currency);
		// return manager.isAcceptedCurrency(currency);
	}

	@Override
	public boolean isAcceptedCurrency(String currency) {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		return manager.isAcceptedCurrency(currency);
	}

	@Override
	public String getDefaultCurrency() {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		return manager.getDefaultCurrency();
	}
	


	@Override
	public String getImplementationName() {
		return "PluginLib (default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.0.2";
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		return manager.deposit(playerName, currency, amount);
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		return manager.withdraw(playerName, currency, amount, 0.0);
	}

	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		me.asofold.bpl.plshared.economy.manager.ConsumerEconomyManager manager = me.asofold.bpl.plshared.Shared.getConsumerEconomyManager();
		return manager.getBalance(playerName, currency);
	}

}
