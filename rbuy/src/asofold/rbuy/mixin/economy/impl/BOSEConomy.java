package asofold.rbuy.mixin.economy.impl;


public class BOSEConomy extends SimpleEconomy {
	
	cosine.boseconomy.BOSEconomy manager;
	
	public BOSEConomy(cosine.boseconomy.BOSEconomy manager){
		this.manager = manager;
		
		for ( String x : new String[]{
				manager.getMoneyName(),
				manager.getMoneyNameCaps(),
				manager.getMoneyNamePlural(),
				manager.getMoneyNamePluralCaps(),
		}){
			this.addAcceptedCurrency(x);
		}
		String shortest = manager.getMoneyName();
		for ( String x : this.acceptedCurrencies){
			if ( x.length()<shortest.length()) shortest = x;
		}
		this.defaultCurrency = shortest;
		this.addCurrencyAliases();
	}
	
	void checkPlayer(String playerName){
		if (!manager.playerRegistered(playerName, true)){
			manager.registerPlayer(playerName);
		}
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		if ( !this.isAcceptedCurrency(currency) ) return false;
		return manager.playerRegistered(playerName, false);
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		return manager.addPlayerMoney(playerName, amount, false);
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		return manager.addPlayerMoney(playerName, -amount, false);
	}

	public  cosine.boseconomy.BOSEconomy getManager() {
		return manager;
	}

	public void setManager(cosine.boseconomy.BOSEconomy manager) {
		this.manager = manager;
	}



	@Override
	public String getImplementationName() {
		return "BOSEconomy 0.7.2(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.0.2";
	}

	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		return manager.getPlayerMoneyDouble(playerName);
	}

}
