package me.asofold.bpl.rbuy.data;

import java.util.Collection;
import java.util.LinkedList;


public class PlayerInfo {
	public String playerName = null;
	public Collection<Transaction> transactions = new LinkedList<Transaction>();
	public Collection<Offer> offers = new LinkedList<Offer>();
	/** Offers the player has a bid running on. */
	public Collection<Offer> bids = new LinkedList<Offer>();

	public boolean isEmpty() {
		return (transactions.isEmpty() && offers.isEmpty());
	}
}
