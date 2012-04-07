package asofold.rbuy.mixin.economy.impl;



/**
 * TODO: remove manager ? add test in constructor to throw, just in case, though.
 * @author dummy
 *
 */
public class IConomy5 extends SimpleEconomy {

	private com.iConomy.iConomy manager;


	public IConomy5(com.iConomy.iConomy manager){
		this.setManager(manager);
		// find out currency (or use '$' as fallback):
		this.setCurrenciesFromFormats(new String[]{ com.iConomy.iConomy.format(1) , com.iConomy.iConomy.format(2)});
		this.addCurrencyAliases();
	}
	
	public com.iConomy.iConomy getManager() {
		return manager;
	}

	public void setManager(com.iConomy.iConomy manager) {
		this.manager = manager;
	}
	

	@Override
	public String getImplementationName() {
		return "iConomy 5(default)";
	}

	@Override
	public String getImplementationVersion() {
		return "0.1.1";
	}

	
	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		if (!hasAccount(playerName, currency)) return false;
		return  com.iConomy.iConomy.getAccount(playerName).getHoldings().hasEnough(amount);
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		if (!isAcceptedCurrency(currency)) return false;
		return com.iConomy.iConomy.hasAccount(playerName);
	}

	@Override
	public boolean add(String playerName, double amount, String currency) {
		try{
			com.iConomy.iConomy.getAccount(playerName).getHoldings().add(amount);
			return true;
		} catch (Throwable t){
			return false;
		}
	}

	@Override
	public boolean subtract(String playerName, double amount, String currency) {
		try{
			com.iConomy.iConomy.getAccount(playerName).getHoldings().subtract(amount);
			return true;
		} catch (Throwable t){
			return false;
		}
	}

	@Override
	public double getBalance(String playerName, String currency) {
		if (!isAcceptedCurrency(currency)) return 0.0;
		return com.iConomy.iConomy.getAccount(playerName).getHoldings().balance();
	}

}
