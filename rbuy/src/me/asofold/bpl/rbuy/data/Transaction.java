package me.asofold.bpl.rbuy.data;

import java.util.ArrayList;
import java.util.List;

import me.asofold.bpl.rbuy.settings.compatlayer.CompatConfig;

public class Transaction {
	public String worldName = null;
	public String regionName = null;
	public String buyerName = null;
	public String sellerName = null;
	public double amount = 0;
	public String currency = null;
	public long timestamp = 0;
	public long area = 0;
	public long volume = 0;
	
	public List<String> restrictions = null;
	
	public boolean fromConfig(CompatConfig config, String prefix){
		worldName = config.getString(prefix + "worldName", null);
		regionName = config.getString(prefix+"regionName", null);
		
		buyerName = config.getString(prefix+"buyerName", null);
		sellerName = config.getString(prefix+"sellerName", null);
		amount = config.getDouble(prefix+"amount", 0.0);
		currency = config.getString(prefix+"currency", null);
		timestamp = config.getLong(prefix+"timestamp", 0L);
		area = config.getLong(prefix+"area", 0L);
		volume = config.getLong( prefix+"volume", area); // preset is area.
		restrictions = config.getStringList(prefix + "restrictions", null);
		if (restrictions != null) {
			restrictions = new ArrayList<String>(restrictions);
		}
		// TODO: maybe this will change some time:
		if ( (buyerName == null) ) return false;
		return true;
	}
	
	public void toConfig( CompatConfig config, String prefix){
		if ( buyerName != null){
			config.setProperty(prefix+"buyerName", buyerName);
		}
		if ( sellerName != null){
			config.setProperty(prefix+"sellerName", sellerName);
		}
		if (worldName != null) {
			config.setProperty(prefix + "worldName", worldName);
		}
		if ( regionName != null){
			config.setProperty(prefix+"regionName", regionName);
		}
		if ( currency != null){
			config.setProperty(prefix+"currency", currency);
		}
		config.setProperty(prefix+"amount", amount);
		config.setProperty(prefix+"timestamp", timestamp);
		config.setProperty(prefix+"area", area);
		config.setProperty(prefix+"volume", volume);
		if (restrictions != null && !restrictions.isEmpty()) {
			config.setProperty(prefix + "restrictions", new ArrayList<String> (restrictions));
		}
	}
}
