package me.asofold.bpl.rbuy.mixin.economy.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.asofold.bpl.rbuy.mixin.economy.EconomyMixin;
import me.asofold.bpl.rbuy.mixin.economy.MixinEconomyInterface;
import me.asofold.bpl.rbuy.mixin.util.MixinUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


/**
 * Auxiliary methods + full payment structure based on methods add and subtract.
 * Designed to ease transaction.
 * 
 * override/implement: constructor (set default currency, accepted), add and subtract or doTransfer
 * 
 * TODO: more flags for messages.
 * 
 * @author mc_dev
 *
 */
public abstract class SimpleEconomy implements MixinEconomyInterface {

	protected String defaultCurrency = null;
	protected Set<String> acceptedCurrencies = new HashSet<String>();
 	
	/**
	 * Send message to receiver of money.
	 */
	boolean messageReceiver = true;
	
	
	/**
	 * Convenience method, trying to parse number off the string and use next item
	 * Very simple parsing method: do not use fraction (minor) and use values >0. 
	 * Will attempt to split number from something else
	 * @param formatted
	 * @return null or name on success
	 */
	public static String currencyNameFromFormat(String formatted){
		String currencyName = null;
		// TODO: better/ more efficient
		int lastIndex = 0;
		int currentIndex = 0;
		Double amount = null;
		// try to parse the longest possible number, if found a number(!=0) use the next space-separated part as currency. 
		for (currentIndex=1; currentIndex<=formatted.length(); currentIndex++){
			String num = formatted.substring(lastIndex, currentIndex);
			try{
				amount = Double.parseDouble(num+"0");
			} catch( NumberFormatException exc){
				lastIndex = currentIndex-1;
				if ( amount != null ){
					if (amount != 0) break;
				}
				
			}
		}
		if (amount != null){
			// parse on for currency:
			if (lastIndex <  formatted.length()){
				for (String candidate : formatted.substring(lastIndex).split(" ")){
					if (candidate.trim() != ""){
						// first one is space, presumably.
						currencyName = candidate.trim();
					}
				}
			}
		}
		if ( currencyName == null ){
			// attempt reverse parse:
			String[] splits = formatted.split(" ");
			boolean lastDot = false;
			int index = -1;
			for( String part : splits){
				part = part.trim();
				for ( int i=0; i<part.length(); i++){
					char x = part.charAt(i);
					if (x =='.' || x==',') lastDot= true;
					else if (x=='0' || x=='1' || x=='2' || x=='3' || x=='4' || x=='5' || x=='6' || x=='7' || x=='8' || x=='9' ){
						if (lastDot) index = i-1;
						else index = i;
						currencyName = part.substring(0, index).trim();
						if ( currencyName == "" ) return null;
						else return currencyName;
					} else{
						lastDot = false;
					}
				}
			}
		}
		return currencyName;
	}
	
	/**
	 * Add, also as lower-case, upper-case.
	 * If default currency is not set it will be set, if valid.
	 * @param currency
	 */
	public void addAcceptedCurrency(String currency){
		if (currency == null ) return;
		currency = currency.trim();
		if (!currency.equals("")){
			// not sure about these:
			this.acceptedCurrencies.add(currency);
			// TODO: add as aliases ! (MAPPING)
			this.acceptedCurrencies.add(currency.toLowerCase());
			this.acceptedCurrencies.add(currency.toUpperCase());
			if ( this.defaultCurrency == null){
				this.defaultCurrency = currency;
			}
		}
	}
	
	/**
	 * Set the default currency and add it to accepted currencies (see addAcceptedCurrency).
	 * @param currency
	 */
	public void setDefaultCurrency(String currency){
		if ( currency == null ) return;
		currency = currency.trim();
		if (!currency.equals("")){
			this.defaultCurrency = currency;
			this.addAcceptedCurrency(currency);
		}
	}
	
	/**
	 * Convenience method to set accepted currencies and default currency.
	 * If you want to have the first be the default currency, have it be singular (like 1) rather.
	 * Do not use fractions (other than 0) and do not use values of 0.
	 * @param formats
	 * @return
	 */
	public boolean setCurrenciesFromFormats(String[] formats){
		boolean set = false;
		for ( String formatted : formats){
			String name = currencyNameFromFormat(formatted);
			// TODO: change to mapping !
			this.addAcceptedCurrency(name); // can handle null.
			if (name != null) set = true;
		}
		return set;
	}

	/**
	 * Convenience method to set some standard aliases (from Admittance configuration) for acceptedCurrencies.
	 */
	public void addCurrencyAliases(){
		Set<String> reduced = new HashSet<String>();
		for (String c : this.acceptedCurrencies){
			reduced.add(c.toLowerCase());
		}
		Map<String,String[]> map = EconomyMixin.getCurrencyAliasMap(reduced);
		for ( String key : map.keySet()){
			for ( String value : map.get(key)){
				this.addAcceptedCurrency(value);
			}
		}
	}
	
	/**
	 * Default behavior is to accept all if none are set.
	 * @param currency
	 * @return
	 */
	public boolean isAcceptedCurrency(String currency) {
		if ( this.acceptedCurrencies.isEmpty()) return true;
		return this.acceptedCurrencies.contains(currency.toLowerCase());
	}

	@Override
	public String getDefaultCurrency() {
		return this.defaultCurrency;
	}
	
	
	/**
	 * Convenience implementation checking stuff and messaging, then delegate to doTransfer.
	 * This is called by admittance.
	 */
	public boolean transfer(Player sender, String receiverName, double amount,
			String currency){
		if (currency == null){
			currency = this.getDefaultCurrency();
		} else if ( !this.isAcceptedCurrency(currency) ) {
			sendErrorMessage(sender, "The currency is not accepted.");
			return false;
		}
		String senderName = sender.getName();
		if (!this.hasAccount(senderName, currency)){
			sendErrorMessage(sender, "You do not have an account ("+currency+").");
			return false; // fails !
		}
		if ( amount < 0 ){
			sendErrorMessage(sender, "You can not send negative amounts of a currency.");
			return false;
		}
		if (!this.hasEnough(senderName, amount, currency)){
			sendErrorMessage(sender, "Your balance is insufficient for this transaction.");
			return false;
		}
		if (!this.hasAccount(receiverName, currency)){
			sendErrorMessage(sender, "The receiver does not have an account ("+currency+").");
			return false;
		}
		// do transfer money
		return this.doTransfer(sender, receiverName, amount, currency);
	}
	
	/**
	 * Implementation of money transfer using add and subtract.
	 * Admittance will send a general success/failure message to the sender, so that need not be done here.
	 * @param sender
	 * @param receiverName
	 * @param amount
	 * @param currency
	 * @return
	 */
	public boolean doTransfer(Player sender, String receiverName, double amount, String currency){
		String senderName = sender.getName();
		if (!this.subtract(senderName, amount, currency)) {
			sendErrorMessage(sender, "Failed to take the amount from your account.");
			return false;
		}
		if (!this.add(receiverName,  amount, currency)){
			sendErrorMessage(sender, "Failed to send the amount.");
			if (this.add(senderName,  amount, currency)){
				// refunded
			} else{
				sendErrorMessage(sender, "Failed to refund, contact an admin.");
				Bukkit.getServer().getLogger().severe("admittance - withdrew "+amount+" "+currency+" from the player "+senderName+" without being able to refund!");
				// problem
			}
			return false;
		}
		if ( this.messageReceiver){
			// check if receiver is online
			Player receiver = Bukkit.getServer().getPlayerExact(receiverName);
			if ( receiver != null ){
				sendSuccessMessage(receiver, "You have received "+amount+" units of the currency "+currency+" from the player "+senderName+".");
			}
		}
		return true;
	}
	
	/**
	 * Convenience.
	 * @param player
	 * @param msg
	 */
	public static void sendErrorMessage(Player player, String msg){
		MixinUtil.sendErrorMessage(player, msg);
	}
	
	/**
	 * Convenience.
	 * @param player
	 * @param message
	 */
	public static void sendSuccessMessage(Player player, String message ){
		MixinUtil.sendSuccessMessage(player, message);
	}
	
	@Override
	public boolean hasEnough(String playerName, double amount, String currency){
		if ( !isAcceptedCurrency(currency)) return false;
		if ( !hasAccount(playerName, currency)) return false;
		double balance = getBalance(playerName, currency);
		if ( balance <= 0.0 ) return false;
		if ( balance < amount ) return false;
		return true;
	}
	
	/*
	 * IMPLEMENT THE FOLLOWING: 
	 * ----------------------------------------
	 */
	
	
	/**
	 * Alternatively: override hasEnough, if only using admittance.
	 * If other plugins use admittance interfaces this should be implemented non-empty.
	 * @param playerName
	 * @param currency
	 * @return
	 */
	public abstract double getBalance(String playerName, String currency);
	
	@Override
	public abstract boolean hasAccount(String playerName, String currency);
	@Override
	public abstract String getImplementationName();
	@Override
	public abstract String getImplementationVersion();
	
	/**
	 * For simple economy plugins, used by transfer.
	 * Override doTransfer if not using this.
	 * @param playerName
	 * @param amount
	 * @param currency
	 * @return
	 */
	public boolean add(String playerName, double amount, String currency){
		return false;
	}
	
	/**
	 * For simple economy plugins, used by transfer.
	 * Override doTransfer if not using this.
	 * @param playerName
	 * @param amount
	 * @param currency
	 * @return
	 */
	public  boolean subtract(String playerName, double amount, String currency){
		return false;
	}
}
