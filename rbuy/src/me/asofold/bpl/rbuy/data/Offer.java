package me.asofold.bpl.rbuy.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.asofold.bpl.rbuy.settings.compatlayer.CompatConfig;

public class Offer {
	/**
	 * A bid by a player / whatever.
	 * @author mc_dev
	 *
	 */
	public static final class Bid {
		public final String name;
		public double amount;
		public Bid(String name, double amount) {
			this.name = name;
			this.amount = amount;
		}
	}
	
	private static final List<Bid> emptyBids = Collections.unmodifiableList(new LinkedList<Bid>());
	
	/**
	 * Name of the player who gets the money.
	 */
	public String benefits = null;
	public String regionName = null;
	public String worldName = null;
	public double amount = 0;
	public String currency = null;
	
	// NEW (auction)
	public boolean isAuction = false;
	public long  timeEndAuction = 0; // TODO: use date !?
	/** Active bids, highest is the last one (!). */
	public List<Bid> bids = null;
	
	public boolean fromConfig(CompatConfig config, String prefix){
		regionName = config.getString(prefix+"regionName", null);
		if ( regionName == null ) return false;
		worldName = config.getString(prefix+"worldName", null);
		if ( worldName == null) return false;
		benefits = config.getString(prefix+"benefits", null);
		currency = config.getString(prefix+"currency", null);
		amount = config.getDouble(prefix+"amount", -1.0);
		if ( amount < 0) return false;
		isAuction = config.getBoolean(prefix + "isAuction", false);
		if (isAuction) {
			timeEndAuction = config.getLong(prefix + "timeAuctionEnd", 0L);
			List<String> keys = config.getStringKeys(prefix +  "bids");
			if (bids != null && !bids.isEmpty()) {
				this.bids = new ArrayList<Bid>(keys.size());
				for (String key : keys) {
					String name = config.getString(prefix + "bids." + key + ".name", null);
					if (name != null) {
						Double bidAmount = config.getDouble(prefix + "bids." + key + ".amount", null);
						if (bidAmount != null) {
							this.bids.add(new Bid(name, bidAmount));
						}
					}
				}
				if (this.bids.isEmpty()) {
					this.bids = null;
				}
			}
		}
		return true;
	}
	
	public void toConfig( CompatConfig config, String prefix){
		if (regionName == null) return; // set nothing.
		config.setProperty(prefix+"regionName",	 regionName);
		if ( worldName == null) return;
		config.setProperty(prefix+"worldName", worldName);
		if ( benefits != null){
			config.setProperty(prefix+"benefits", benefits);
		}
		if ( currency != null){
			config.setProperty(prefix+"currency", currency);
		}
		config.setProperty(prefix+"amount", amount);
		if (isAuction) {
			config.set(prefix + "isAuction", true);
			config.set(prefix + "timeAuctionEnd", true);
			if (bids != null) {
				for (int i = 0; i < bids.size(); i++) {
					Bid bid = bids.get(i);
					config.set(prefix + "bids.b" + i + ".name", bid.name);
					config.set(prefix + "bids.b" + i + ".amount", bid.amount);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param name
	 * @param amount
	 * @param maxNumBids
	 * @return null if invalid, otherwise a Collection<Bid> of removed bids.
	 */
	public Collection<Bid> addBid(String name, double amount, int maxNumBids) {
		if (amount < this.amount) {
			return null;
		}
		if (bids == null) {
			bids = new ArrayList<Bid>(Math.min(3, maxNumBids));
			bids.add(new Bid(name, amount));
			this.amount = amount;
			return emptyBids;
		} else {
			Bid oldBid = null;
			for (int i = 0; i < this.bids.size(); i++) {
				Bid bid = bids.get(i);
				if (bid.name.equalsIgnoreCase(name)) {
					oldBid = bid;
					break;
				}
			}
			if (oldBid != null) {
				// Update.
				oldBid.amount = amount;
				if (bids.get(bids.size() -1) != oldBid) {
					bids.remove(oldBid);
					bids.add(oldBid);
				}
			} else {
				bids.add(new Bid(name, amount));
			}
		}
		return checkBids(maxNumBids);
	}
	
	/**
	 * Returns a collection always.
	 * @param maxNumBids
	 * @return
	 */
	public Collection<Bid> checkBids(int maxNumBids) {
		if (bids == null || bids.size() <= maxNumBids) {
			return emptyBids;
		}
		final Collection<Bid> removed = new ArrayList<Bid>(bids.size() - maxNumBids);
		// Check max bids in any case (!).
		while (bids.size() > maxNumBids) {
			// Remove the lowest one.
			removed.add(bids.remove(0));
		}
		return removed;
	}
	
	/**
	 * 
	 * @param name
	 * @return If previously contained.
	 */
	public Bid withdrawBid(String name) {
		Bid oldBid = null;
		for (int i = 0; i < this.bids.size(); i++) {
			Bid bid = bids.get(i);
			if (bid.name.equalsIgnoreCase(name)) {
				oldBid = bid;
				break;
			}
		}
		if (oldBid != null) {
			bids.remove(oldBid);
			if (bids.isEmpty()) {
				bids = null;
			}
			return oldBid;
		} else {
			return null;
		}
	}
	
	public Bid getBid(String name) {
		for (int i = 0; i < this.bids.size(); i++) {
			Bid bid = bids.get(i);
			if (bid.name.equalsIgnoreCase(name)) {
				return bid;
			}
		}
		return null;
	}
}
