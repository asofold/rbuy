package asofold.rbuy.mixin.economy.impl;



/**
 * TODO: remove manager ? add test in constructor to throw, just in case, though.
 * @author dummy
 *
 */
public class IConomy6 extends SimpleEconomy {

	private com.iCo6.iConomy manager;


	public IConomy6(com.iCo6.iConomy manager){
		this.setManager(manager);
		// find out currency (or use '$' as fallback):
		this.setCurrenciesFromFormats(new String[]{ com.iCo6.iConomy.format(1) , com.iCo6.iConomy.format(2)});
		this.addCurrencyAliases();
	}
	
	public com.iCo6.iConomy getManager() {
		return manager;
	}

	public void setManager(com.iCo6.iConomy manager) {
		this.manager = manager;
	}
	

	@Override
	public String getImplementationName() {
		return "iConomy 6(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.1.1";
	}

	
	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		if (!hasAccount(playerName, currency)) return false;
		return  new com.iCo6.system.Accounts().get(playerName).getHoldings().hasEnough(amount);
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		if (!isAcceptedCurrency(currency)) return false;
		return new com.iCo6.system.Accounts().exists(playerName);
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		try{
			new com.iCo6.system.Accounts().get(playerName).getHoldings().add(amount);
			return true;
		} catch (Throwable t){
			return false;
		}
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		try{
			new com.iCo6.system.Accounts().get(playerName).getHoldings().subtract(amount);
			return true;
		} catch (Throwable t){
			return false;
		}
	}

	@Override
	public double getBalance(String playerName, String currency) {
		if ( !isAcceptedCurrency(currency)) return 0.0;
		return new com.iCo6.system.Accounts().get(playerName).getHoldings().getBalance();
	}

}
