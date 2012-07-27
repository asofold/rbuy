package me.asofold.bpl.rbuy.mixin.economy.impl;

import me.asofold.bpl.rbuy.mixin.economy.MixinEconomyInterface;

import org.bukkit.entity.Player;


/**
 * TODO: use Register to hook into economies. 
 * @author mc_dev
 *
 */
public class RegisterEconomy implements MixinEconomyInterface {

	@Override
	public String getImplementationName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getImplementationVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasEnough(String playerName, double amount, String currency) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasAccount(String playerName, String currency) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean transfer(Player sender, String receiverName, double amount,
			String currency) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAcceptedCurrency(String currency) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getDefaultCurrency() {
		// TODO Auto-generated method stub
		return null;
	}

}
