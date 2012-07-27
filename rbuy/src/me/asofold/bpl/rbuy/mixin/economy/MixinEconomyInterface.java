package me.asofold.bpl.rbuy.mixin.economy;

import me.asofold.bpl.rbuy.mixin.MixinPublicInterface;

import org.bukkit.entity.Player;


/**
 * Intended for "consumer perspective" money transfer for one online player and one potentially offline player.
 * Very simplish.
 * @author dummy
 *
 */
public interface MixinEconomyInterface extends MixinPublicInterface{
	
	/**
	 * 
	 * @param player
	 * @param amount
	 * @param currency
	 * @return has or has not enough
	 */
	public boolean hasEnough(String playerName, double amount, String currency);
	
	/**
	 * Check if the player has an account for this currency.
	 * @param playerName
	 * @param currency
	 * @return shas or has not account
	 */
	public boolean hasAccount(String playerName, String currency);
	
	/**
	 * This assumes that the account is created, if players exist.
	 * @param sender
	 * @param receiverName
	 * @param amount
	 * @param currency
	 * @return success
	 */
	public boolean transfer(Player sender, String receiverName, double amount, String currency);
	
	/**
	 * Check if the currency is valid.
	 * @param currency
	 * @return if accepted
	 */
	public boolean isAcceptedCurrency(String currency);
	
	/**
	 * Default currency will be used, if no currency is specified for a transaction.
	 * @return name of the default currency
	 */
	public String getDefaultCurrency();
	

}
