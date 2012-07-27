/**
 * Package for copy and paste for simply hooking into economies.
 */
package me.asofold.bpl.rbuy.mixin.economy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.asofold.bpl.rbuy.compatlayer.CompatConfig;
import me.asofold.bpl.rbuy.mixin.MixinPublicInterface;
import me.asofold.bpl.rbuy.mixin.economy.impl.BOSEConomy;
import me.asofold.bpl.rbuy.mixin.economy.impl.Essentials257;
import me.asofold.bpl.rbuy.mixin.economy.impl.IConomy5;
import me.asofold.bpl.rbuy.mixin.economy.impl.IConomy6;
import me.asofold.bpl.rbuy.mixin.economy.impl.MultiCurrency;
import me.asofold.bpl.rbuy.mixin.economy.impl.PluginLibEconomy;
import me.asofold.bpl.rbuy.mixin.economy.impl.SimpleEconomy;
import me.asofold.bpl.rbuy.mixin.economy.impl.VaultEconomy;
import me.asofold.bpl.rbuy.mixin.util.StringPropertyFilter;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;


/**
 * "Mixin" class to hook into economies in a simple way.
 * @author mc_dev
 *
 */
public class EconomyMixin implements Listener {
	public static final String mixinVersion = "1.0.0";
	public static final String mixinName = "EconomyMixin";
	
	/**
	 * Set this to "[YourPlugin]" to ensure log messages carry that too. 
	 */
	public String tag = "";
	/**
	 * Some preset currency aliases.
	 */
	static Map<String, String[] > currencyAliasMap = null;
	
	SimpleEconomy simpleEco = null;
	MixinEconomyInterface eco = null;
	boolean mightHaveVault = false;
	
	/**
	 * For filtering out some plugins
	 */
	StringPropertyFilter pluginFilter = new StringPropertyFilter(null, null);
	
	/**
	 * Currently only in use with one-currency-interfaces (!).
	 */
	public static String[][] presetCurrencyAliases = new String[][]{
		{"dollar", "dollars", "$"},
		{"coin", "coins"},
		{"euro", "euros", "€"},
		{"diamond", "diamonds"},
		{"gold_ingot", "gold_ingots"},
		{"iron_ingot", "iron_ingots"}
	};
	
	private static void initCurrencyAliasMap() {
		Map<String, String[]> map = new HashMap<String, String[]>();
		
		for (String[] aliases : presetCurrencyAliases){
			for ( String alias : aliases){
				map.put( alias.toLowerCase(), aliases);
			}
		}
		currencyAliasMap = map;
	}
	
	/**
	 * Return map mapping lower-case/trimmed name from given set to array with lower-case names.
	 * @param reduced
	 * @return
	 */
	public static Map<String, String[]> getCurrencyAliasMap(Set<String> set) {
		if (currencyAliasMap == null){
			initCurrencyAliasMap();
		}
		
		Map<String,String[]> out = new HashMap<String, String[]>();
		
		for ( String c : set){
			String key = c.toLowerCase().trim();
			String[] aliases = currencyAliasMap.get(key);
			if (aliases != null){
				out.put(key, aliases );
			}
		}
		return out;
	}
	
	/**
	 * Get all aliases.
	 * @see getCurrencyAliasMap(Set<String> set) 
	 * @param set
	 * @return
	 */
	public static Map<String, String[]> getCurrencyAliasMap() {
		if (currencyAliasMap == null){
			initCurrencyAliasMap();
		}
		return currencyAliasMap;
	}
	
	public EconomyMixin(){
		
	}
	
	/**
	 * Register events.
	 * @param plugin
	 */
	public void registerEvents(Plugin plugin){
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	
	
	public void applySettings(CompatConfig cfg, String path){
		pluginFilter = new StringPropertyFilter(cfg.getStringList(path+".include-plugins", null), cfg.getStringList(path+".exclude-plugins", null));
		List<String> load = cfg.getStringList(path+".load-plugins", null);
		if ( load != null){
			PluginManager pm = Bukkit.getPluginManager();
			for (String n : load){
				Plugin plugin = pm.getPlugin(n);
				if (plugin == null) continue;
				if (pm.isPluginEnabled(plugin)) continue;
				try{
					pm.enablePlugin(plugin);
				} catch(Throwable t){
					Bukkit.getLogger().severe("[EconomyMixin]"+tag+" Failed to load plugin '"+n+"': "+t.getMessage());
					t.printStackTrace();
				}
			}
		}
		// TODO: also read aliases !
		initCompatibleEconomyInterface();
	}
	
	public void addDefaultSettings(CompatConfig cfg, String path){
		List<String> exc = new LinkedList<String>();
		exc.add("PluginLibSharedLibrary");
		cfg.setProperty(path+".exclude-plugins", exc);
		cfg.setProperty(path+".include-plugins", new LinkedList<String>());
		cfg.setProperty(path+".load-plugins", new LinkedList<String>());
		// TODO: currency aliases !
	}
	
	/**
	 * Get the implementation currently set.
	 * @return
	 */
	public MixinEconomyInterface getEconomyInterface(){
		if (eco==null) return simpleEco;
		else return eco;
	}
	
	/**
	 * This should work for most economy interface implementations.
	 * @return 
	 */
	public SimpleEconomy getSimpleEconomy(){
		return simpleEco;
	}
	
	/**
	 * Central access method for setting the interface. This will also write a log message to the console.
	 * @param eco
	 */
	public void setEconomyInterface(MixinEconomyInterface eco){
		this.eco = eco;
		if ( eco instanceof SimpleEconomy) this.simpleEco = (SimpleEconomy) eco;
		else this.simpleEco = null;
		System.out.println("[EconomyMixin]"+tag+" Set economy interface: "+interfaceImplementationInfo(eco));
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
		checkDisabledPlugin(event);
	}

	@EventHandler(priority=EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
    	checkEnabledPlugin(event.getPlugin());
   }
	
	private void checkDisabledPlugin(PluginDisableEvent event) {
		// TODO: Check if a full re-check is necessary ...
	}

	private boolean checkEnabledPlugin(Plugin plugin) {
		String pluginName = plugin.getName();
		if (!isPluginAllowed(pluginName)) return false;
		if (pluginName.equals("iConomy")) return initIConomy();
		else if (pluginName.equals("BOSEconomy")) return initBOSEconomy();
    	else if (pluginName.equals("PluginLib") || pluginName.equals("PluginLibSharedLibrary")) return initPluginLibEconomy();
    	else if (pluginName.equals("Essentials")) return initEssentialsEconomy();
		else if (pluginName.equals("MultiCurrency")) return initMultiCurrency();
		else if (pluginName.equals("Vault")) return initVaultEconomy();
		return false;
	}
	
	public boolean isPluginAllowed(String pluginName) {
		return pluginFilter.passesFilter(pluginName);
	}

	public boolean initCompatibleEconomyInterface(){
		eco = null;
		simpleEco = null;
		mightHaveVault = false;
		if (initVaultEconomy()) return true;
		if (initBOSEconomy()) return true;
		if (initIConomy()) return true;
		if (initEssentialsEconomy()) return true;
		if (initMultiCurrency()) return true;
        if (initPluginLibEconomy()) return true;
        if ( eco == null) setEconomyInterface(null);
        return false;
	}
	
	public boolean initVaultEconomy() {
		if ( !this.isPluginAllowed("Vault")) return false;
		Plugin plugin = this.getEnabledPlugin("Vault");
		if ( plugin != null){
			mightHaveVault = true;
			try{
				RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		        if (economyProvider != null) {
		        	net.milkbowl.vault.economy.Economy eco = economyProvider.getProvider();
		        	if ( eco == null ) return false;
		            this.setEconomyInterface (new VaultEconomy(eco));
					return true;
		        }
			} catch (Throwable t){
				return false;
			}
		}
		return false;
	}

	public  boolean initMultiCurrency() {
		if ( !this.isPluginAllowed("MultiCurrency")) return false;
		Plugin plugin = this.getEnabledPlugin("MultiCurrency");
		if ( plugin != null){
			try{
				this.setEconomyInterface (new MultiCurrency());
				return true;
			} catch (Throwable t){
				return false;
			}
		}
		return false;
	}

	public boolean initEssentialsEconomy() {
		if ( !this.isPluginAllowed("Essentials")) return false;
		Plugin plugin = this.getEnabledPlugin("Essentials");
		if (plugin != null){
			try{
				this.setEconomyInterface (new Essentials257());
				return true;
			} catch (Throwable t){
				return false;
			}
		}
		return false;
	}

	
	
	public String interfaceImplementationInfo(MixinPublicInterface in){
		if (in==null) return "<none>";
		return in.getImplementationName()+ " , Version: "+in.getImplementationVersion();
	}
	
	/**
	 * Get enabled plugin or null.
	 * @param name
	 * @return
	 */
	public Plugin getEnabledPlugin(String name){
		Plugin temp = Bukkit.getServer().getPluginManager().getPlugin(name);
		if (temp != null){
			if (!temp.isEnabled()) temp = null;
		}
		return temp;
	}
	
	public boolean initBOSEconomy() {
		if ( !this.isPluginAllowed("BOSEconomy")) return false;
		Plugin temp = this.getEnabledPlugin("BOSEconomy");
		if(temp != null){
			try{
				if ( temp instanceof cosine.boseconomy.BOSEconomy){
					cosine.boseconomy.BOSEconomy economy = (cosine.boseconomy.BOSEconomy) temp;
					this.setEconomyInterface(new BOSEConomy(economy));
					//System.out.println("admittance - EconomyInterface: "+this.interfaceImplementationInfo(this.economyInterface));
					return true;
				} else{
					System.out.println("admittance - set up with BOSEconomy failed (wrong instance).");
				}
			} catch (Throwable t){
				org.bukkit.Bukkit.getServer().getLogger().severe("admittance - setting up BOSEconomy  failed.");
				if (!temp.isEnabled()){
					System.out.println("admittance - BOSEconomy is not yet enabled, it might be found when it is enabled.");
				} else{
					t.printStackTrace();
				}
			}
		}
		
		
		return false;  
	}

	public boolean initPluginLibEconomy() {
		if ( !this.isPluginAllowed("PluginLib")) return false;
		if ( !this.isPluginAllowed("PluginLibSharedLibrary")) return false;
		if ( !this.isPluginAllowed("plshared")) return false;
		 // find pluginlib/shared for economy, if not set:
		Plugin plugin = this.getEnabledPlugin("PluginLibSharedLibrary");
		if (plugin == null ) plugin = this.getEnabledPlugin("PluginLib");
    	if (plugin == null ) {
    		try {
    			String dummy = me.asofold.bpl.plshared.Shared.getName() + "("+me.asofold.bpl.plshared.Shared.getVersion()+")";
    			System.out.println("admittance - found "+dummy+" as non-plugin library.");
    		} catch(Throwable t){
    			return false;
    		}
    	}
    	
		try{
			this.setEconomyInterface (new PluginLibEconomy());
			//System.out.println("admittance - EconomyInterface: "+this.interfaceImplementationInfo(this.economyInterface));
			return true;
		} catch (Throwable t){
			org.bukkit.Bukkit.getServer().getLogger().severe("admittance - setting up EconomyInterfacewith pluginlib failed.");
			t.printStackTrace();
		}
    
        
        return false;
	}


	public boolean initIConomy() {
		if ( !this.isPluginAllowed("iConomy")) return false;
		// try to use iConomy
       
    	Plugin iconPlugin = this.getEnabledPlugin("iConomy");
    	if (iconPlugin != null){
    		if (this.tryIConomy5(iconPlugin)) return true;
    		if (this.tryIConomy6(iconPlugin)) return true;
    		
    	} 
    	return false;
	}
	
	boolean tryIConomy5(Plugin iconPlugin){
		if ( !this.isPluginAllowed("iConomy")) return false;
		try{
    		if (iconPlugin instanceof com.iConomy.iConomy){
    			this.setEconomyInterface(new IConomy5((com.iConomy.iConomy) iconPlugin ));
    			// this.usesIConomy = true;
    			// System.out.println("admittance - set up EconomyInterface implementation for iConomy (before version 6).");
    			return true;
    		} else{
    			System.out.println("admittance - failed to set up with iConomy (before version 6).");
    		}
		} catch (NoClassDefFoundError e){
			// ignore
		} catch (Throwable t){
			System.out.println("admittance - failed to set up with iConomy (before version 6), might retry once iConomy is loaded.");
			t.printStackTrace();
		}
		return false;
	}
	
	boolean tryIConomy6(Plugin iconPlugin){
		if ( !this.isPluginAllowed("iConomy")) return false;
		try{
    		if (iconPlugin instanceof com.iCo6.iConomy){
    			this.setEconomyInterface(new IConomy6((com.iCo6.iConomy) iconPlugin ));
    			// this.usesIConomy = true;
    			// System.out.println("admittance - set up EconomyInterface implementation for iConomy6.");
    			return true;
    		} else{
    			System.out.println("admittance - failed to set up with iConomy6.");
    		}
		} catch (NoClassDefFoundError e){
			// ignore
		} catch (Throwable t){
			System.out.println("admittance - failed to set up with iConomy6, might retry once iConomy is loaded.");
			t.printStackTrace();
		}
		return false;
	}

}
