package me.asofold.bpl.rbuy;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.asofold.bpl.plshared.Utils;
import me.asofold.bpl.rbuy.command.RbuyTabExecutor;
import me.asofold.bpl.rbuy.command.auction.AuctionCommand;
import me.asofold.bpl.rbuy.data.Offer;
import me.asofold.bpl.rbuy.data.PlayerInfo;
import me.asofold.bpl.rbuy.data.Transaction;
import me.asofold.bpl.rbuy.mixin.economy.EconomyMixin;
import me.asofold.bpl.rbuy.mixin.economy.MixinEconomyInterface;
import me.asofold.bpl.rbuy.settings.RegionFilterSettings;
import me.asofold.bpl.rbuy.settings.compatlayer.CompatConfig;
import me.asofold.bpl.rbuy.settings.compatlayer.CompatConfigFactory;
import me.asofold.bpl.rbuy.settings.compatlayer.ConfigUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;


/**
 * Command based plugin.
 * Buy/sell WorldGuard regions.
 * Interfacing to economy: via admittance (later: Register or plshared).
 * Internally lowercase keys are used, though names are stored case-sensitive.
 * ("Fast" dev.)
 * @author mc_dev
 *
 */
public class Rbuy extends JavaPlugin implements Listener{
	
	/**
	 * Check if r1 is fully inside in r2 (cuboid bounds).
	 * @param r1
	 * @param r2
	 * @return
	 */
	public final static boolean isFullyInside(ProtectedRegion r1, ProtectedRegion r2) {
		// if ( !(r1 instanceof ProtectedCuboidRegion) || !(r2 instanceof ProtectedCuboidRegion) ) return false;
		BlockVector r1Min = r1.getMinimumPoint();
		BlockVector r1Max= r1.getMaximumPoint();
		BlockVector r2Min = r2.getMinimumPoint();
		BlockVector r2Max= r2.getMaximumPoint();
		return (r1Min.getBlockX()>=r2Min.getBlockX()) && (r1Max.getBlockX()<=r2Max.getBlockX()) && (r1Min.getBlockY()>=r2Min.getBlockY()) && (r1Max.getBlockY()<=r2Max.getBlockY()) && (r1Min.getBlockZ()>=r2Min.getBlockZ()) && (r1Max.getBlockZ()<=r2Max.getBlockZ());
	}
	
	public static final class EdgeData {
		public Map<String, Integer> nRestriction = null;
		public int nTotal = 0;
		public int nBuy = 0;
		public long areaBuy = 0L;
		public long volumeBuy = 0L;
	}
	
	// Configuration content: 
	CompatConfig currentConfig = null;
	
	/**
	 * Enable/disable functionality flag.
	 * Everything except for reload, enable, disable can be disabled.
	 */
	boolean defaultActive = true;
	boolean active= false;
	
	/**
	 * Allow ops everything.
	 */
	boolean defaultOpPermissions = true;
	boolean opPermissions = defaultOpPermissions;
	
	/**
	 * Number of regions one can buy during interval.
	 */
	int defaultMaxBuy = 5;
	int maxBuy = defaultMaxBuy;
	
	/**
	 * Days that buying transactions count for the limit.
	 */
	int defaultTimeCountBuy = 10;
	int timeCountBuy = defaultTimeCountBuy;
	
	/**
	 * Maximum area to buy within timeCountArea days.
	 */
	long defaultMaxArea = 0;
	long maxArea = defaultMaxArea;
	
	/**
	 * Valid maxArea interval.
	 */
	int defaultTimeCountArea = -1; // same as timeCountBuy
	int timeCountArea = defaultTimeCountArea;
	
	/**
	 * Maximum area to buy within timeCountArea days.
	 */
	long defaultMaxVolume= 0;
	long maxVolume = defaultMaxArea;
	
	/**
	 * Valid maxArea interval.
	 */
	int defaultTimeCountVolume = -1; // same as timeCountArea
	int timeCountVolume = defaultTimeCountArea;
	
	/**
	 * Maximum number of offers a player can have at a time.
	 */
	int defaultMaxOffers = 3;
	int maxOffers = defaultMaxOffers;
	
	/**
	 * Forget transactions older than this (days).
	 */
	int defaultTimeForgetTransaction = 30;
	int timeForgetTransaction = defaultTimeForgetTransaction;
	
	/**
	 * Distance to region at which players can buy it.
	 */
	int defaultDistanceBuy = 30;
	int distanceBuy = defaultDistanceBuy;
	
	/**
	 * Distance to region at which players can sell it.
	 */
	int defaultDistanceSell = 60;	
	int distanceSell = defaultDistanceSell;	
	
	/**
	 * Distance to region for which is shown when using the info command without arguments.
	 */
	int defaultInfoRadius = 20;
	int infoRadius = defaultInfoRadius;
	
	/**
	 * Show all entries for list.
	 */
	boolean defaultShowAll = true;
	boolean showAll = defaultShowAll;
	
	/**
	 * Show own entries on info / list (?).
	 */
	boolean defaultShowOwn = false;
	boolean showOwn = defaultShowOwn;
	
	/**
	 * Strict boundary.
	 */
	int defaultMinMapHeight = 0;
	int minMapHeight = defaultMinMapHeight;
	
	/*
	 * Only list offers from the same world on /rlist.
	 */
	boolean defaultListWorldSpecific = true;
	boolean listWorldSpecific = defaultListWorldSpecific;
	
	// TODO: config
	boolean defaultShowWorldName = true;
	boolean showWorldName = defaultShowWorldName;
	
	// TODO:  config
	boolean defaultUseWgPerms = true;
	boolean useWgPerms = defaultUseWgPerms;
	
	// TODO:config
	boolean defaultUseBukkitPerms = true;
	boolean useBukkitPerms = defaultUseBukkitPerms;
	
	// TODO: config 
	String defaultMagicWordSell = "/rsell";
	LinkedHashSet<String> magicWordsSell = new LinkedHashSet<String>();
	// TODO: config 
	String defaultMagicWordBuy = "/rbuy";
	LinkedHashSet<String> magicWordsBuy = new LinkedHashSet<String>();
	
	// TODO: config
	boolean defaultUseSigns = true;
	boolean useSigns = defaultUseSigns;
	
	
	// TODO: config
	boolean defaultSignsOffground = true;
	boolean signsOffground = true;
	
	// TODO: cofnig
	int defaultCommandsPerSecond = 50;
	int commandsPerSecond = defaultCommandsPerSecond;
	
	
	boolean defaultAllowMemberSell = false; // for compatibility
	boolean allowMemberSell = defaultAllowMemberSell;
	
	int defaultMaxRegions = 0;
	int maxRegions = defaultMaxRegions;
	
	/**
	 * Permissions to ignore: allow by default.
	 */
	String[] defaultIgnorePermissions = new String[]{
		"rbuy.buy", "rbuy.sell", "rbuy.info", "rbuy.list",
		"rbuy.show-all"
	};
	Set<String> ignorePermissions = new HashSet<String>();
	
	// Others:
	
	String name = "Rbuy";
	String version = "?";
	
	/**
	 * Commands to be registered.
	 */
	String[] cmds = new String[]{
			"rbuy", "rsell", "rlist", "rinfo", "rhelp", 
			"rreload", "renable", "rdisable", "rremove",
			"runsellable", "rcheckoffers", "rtransfer"
		};
	
	/**
	 * Milliseconds per day.
	 */
	long msDay = 1000*60*60*24;
	
	/**
	 * Indicates whether saving is appropriate.
	 */
	boolean changed = false;
	
	/**
	 * Offers: world->region name->Offer
	 */
	Map<String, Map<String, Offer>> offers = new HashMap<String, Map<String, Offer>>();
	
	/**
	 * Past transactions.
	 */
	List<Transaction> transactions = new LinkedList<Transaction>();
	
	/**
	 * Player infos.
	 */
	Map<String, PlayerInfo> infos = new HashMap<String, PlayerInfo>();
	
	long tsCommand = 0;
	int nCommands = 0;
	
	/**
	 * Unsellable regions.
	 */
	Map<String, Set<String>> unsellable = new HashMap<String, Set<String>>();
	
	/**
	 * Economy support!
	 */
	EconomyMixin ecoMixin = new EconomyMixin();
	
	final Map<String, Map<String, RegionFilterSettings>> filterSettings = new HashMap<String, Map<String,RegionFilterSettings>>();
	
	@Override
	public void onDisable() {
		this.saveData();
		this.active = false;
		System.out.println(this.getPluginDescr()+" is disabled.");
	}

	public String getPluginDescr(){
		return this.name +"("+this.version+")";
	}
	
	/**
	 * Constructor, in case someone is looking for it.
	 */
	public Rbuy(){
		magicWordsBuy.clear();
		magicWordsBuy.add(defaultMagicWordBuy);
		magicWordsSell.clear();
		magicWordsSell.add(defaultMagicWordSell);
		ecoMixin.tag = "[rbuy]";
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdf = getDescription();
		this.version = pdf.getVersion();
		this.name = pdf.getName();

		// Errors in configuration should lead to deactivation of the plugin (intended).
		this.reloadSettings();
		this.loadData();

		TabExecutor tabExe = new RbuyTabExecutor(this);
		for (String n : this.cmds ){
			PluginCommand cmd = this.getCommand(n);
			cmd.setExecutor(tabExe);
		}
		getCommand("rauction").setExecutor(new AuctionCommand(this));
		
		// TODO: RuntimeConfig (data)
		
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		ecoMixin.registerEvents(this);
		
		System.out.println(this.getPluginDescr()+" is enabled (active="+this.active+").");
	}
	
	@EventHandler(priority=EventPriority.LOW)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if ( event.isCancelled() ) return;
		if ( !useSigns) return;
		Action action = event.getAction();
		if ( action == Action.RIGHT_CLICK_BLOCK){
			Block block = event.getClickedBlock();
			Material mat = block.getType();
			if ( (mat == Material.SIGN) || (mat == Material.SIGN_POST) || (mat == Material.WALL_SIGN)){
				BlockState state = block.getState();
				if ( state instanceof Sign){
					if (processSignInteract(event.getPlayer(), (Sign) state)){
						event.setCancelled(true);
						event.setUseInteractedBlock(Result.DENY);
						event.setUseItemInHand(Result.DENY);
					}
					
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.LOW)
	public void onSignChange( SignChangeEvent event) {
		if ( event.isCancelled()) return;
		if ( !useSigns) return;
		if (processSignChange(event)){
			event.setCancelled(true);
			removeSign(event.getBlock());
		}
	}
	
	/**
	 * Enable/disable during runtime (TODO)
	 * @param b
	 */
	public void setActive(boolean b) {
		this.active = b;
		// TODO: save config ?
	}

	public CompatConfig getCurrentSettings(){
		if ( this.currentConfig == null){
			File file = new File(this.getDataFolder(), "rbuy.yml");
			CompatConfig config = CompatConfigFactory.getConfig(file);
			if ( file.exists() ){
				config.load();
			}
			if (ConfigUtil.forceDefaults(getDefaultSettings(), config)) config.save();
			this.currentConfig = config;
			this.applySettings();
		}
		return this.currentConfig;
	}
	
	public void reloadSettings(){
		this.currentConfig = null;
		this.getCurrentSettings();
		this.applySettings();
	}
	
	public static List<String> stringList(String[] preset){
		List<String> out = new LinkedList<String>();
		for ( String s : preset){
			out.add(s);
		}
			return out;	
	}
	public static List<String> stringList( String preset){
		return stringList( new String[]{preset});
	}

	public void applySettings() {
		// apply this.currentConfig to internals
		CompatConfig config = this.currentConfig;
		// Read filter settings.
		filterSettings.clear();
		for (String worldKey : config.getStringKeys("filter")) {
			for (String resKey : config.getStringKeys("filter." + worldKey)) {
				RegionFilterSettings rfs = RegionFilterSettings.fromConfig(config, "filter." + worldKey + "." + resKey + ".");
				if (rfs != null) {
					Map<String, RegionFilterSettings> worldMap = filterSettings.get(worldKey.toLowerCase());
					if (worldMap == null) {
						worldMap = new HashMap<String, RegionFilterSettings>();
						filterSettings.put(worldKey.toLowerCase(), worldMap);
					}
					worldMap.put(resKey.toLowerCase(), rfs);
				}
			}
		}
		// ...
		if (config.getString("users-buy", null) != null ) {
			getServer().getLogger().warning("rbuy - The configuration entry 'users-buy' is deprecated and has no function. Instead add the entry 'rbuy.buy' to  'ignore-permissions' or set it as a permission for everyone (Further check: rbuy.info, rbuy.list, rbuy.show-all).");
		}
		if (config.getString("users-sell", null) != null){
			getServer().getLogger().warning("rbuy - The configuration entry 'users-sell' is deprecated and has no function. Instead add the entry 'rbuy.sell' to  'ignore-permissions' or set it as a permission for everyone (Further check: rbuy.info, rbuy.list, rbuy.show-all).");
		}
		this.active = config.getBoolean("active", defaultActive);
		this.commandsPerSecond = config.getInt("commands-per-second", defaultCommandsPerSecond);
		this.distanceBuy = config.getInt("distance-buy", defaultDistanceBuy);
		this.distanceSell = config.getInt("distance-sell", defaultDistanceSell);
		this.ignorePermissions.clear(); // TODO: maybe check and warn if wrong perms ?
		this.ignorePermissions.addAll(config.getStringList("ignore-permissions", new LinkedList<String>()));
		this.infoRadius = config.getInt("info-radius", defaultInfoRadius);
		this.listWorldSpecific = !config.getBoolean("list-all-worlds", false);
		this.magicWordsBuy.clear();
		this.magicWordsBuy.addAll(config.getStringList("magicwords-buy", stringList(defaultMagicWordBuy)));
		this.magicWordsSell.clear();
		this.magicWordsSell.addAll(config.getStringList("magicwords-sell", stringList(defaultMagicWordSell)));
		this.maxArea = config.getLong("max-area", defaultMaxArea);
		this.maxBuy = config.getInt("max-buy",  defaultMaxBuy);
		this.maxOffers = config.getInt("max-offers", defaultMaxOffers);
		this.maxVolume = config.getLong("max-volume", defaultMaxVolume);
		this.minMapHeight = config.getInt("min-map-height", defaultMinMapHeight);
		this.opPermissions = config.getBoolean("op-permissions", defaultOpPermissions);
		this.showAll = config.getBoolean("show-all", defaultShowAll);
		this.showOwn = config.getBoolean("show-own", defaultShowOwn);
		this.showWorldName = config.getBoolean("show-worldname", defaultShowWorldName);
		this.signsOffground = config.getBoolean("signs-offground", defaultSignsOffground);
		this.timeCountArea = config.getInt("time-count-area", defaultTimeCountArea);
		this.timeCountBuy = config.getInt("time-count-buy", defaultTimeCountBuy);
		this.timeCountVolume = config.getInt("time-count-volume", defaultTimeCountVolume);
		this.timeForgetTransaction = config.getInt("time-forget-transaction", defaultTimeForgetTransaction);
		this.useBukkitPerms = config.getBoolean("use-bukkit-perms", defaultUseBukkitPerms);
		this.useSigns = config.getBoolean("use-signs", defaultUseSigns);
		this.useWgPerms = config.getBoolean("use-worldguard-perms", defaultUseWgPerms);
		this.allowMemberSell = config.getBoolean("allow-member-sell", defaultAllowMemberSell);
		maxRegions = config.getInt("max-regions", defaultMaxRegions);
		// Some special defaults / checks:
		if ( this.timeForgetTransaction<0 ) this.timeForgetTransaction = 0;
		if ( this.timeCountBuy<0 ) this.timeCountBuy = this.timeForgetTransaction;
		if ( this.timeCountArea<0 ) this.timeCountArea = this.timeCountBuy;
		if ( this.timeCountVolume<0) this.timeCountVolume = this.timeCountArea;
		if (this.magicWordsBuy.isEmpty()) this.magicWordsBuy.add(this.defaultMagicWordBuy);
		if (this.magicWordsSell.isEmpty()) this.magicWordsSell.add(this.defaultMagicWordSell);
		ecoMixin.applySettings(config, "economy");
	}
	
	/**
	 * Set the defualt configuration and save it.
	 * applyConfig has to be called (!) for the changes to take effect.
	 */
	public CompatConfig getDefaultSettings() {
		// create and safe default configuration.
//		File file = new File(this.getDataFolder(), "rbuy.yml");
		CompatConfig config = CompatConfigFactory.getConfig(null);
		config.setProperty("active", defaultActive);
		config.setProperty("commands-per-second", defaultCommandsPerSecond);
		config.setProperty("distance-buy", defaultDistanceBuy);
		config.setProperty("distance-sell", defaultDistanceSell);
		config.setProperty("ignore-permissions", stringList(this.defaultIgnorePermissions));
		config.setProperty("info-radius", defaultInfoRadius);
		config.setProperty("list-all-worlds", !defaultListWorldSpecific);
		config.setProperty("magicwords-buy", stringList(defaultMagicWordBuy));
		config.setProperty("magicwords-sell", stringList(defaultMagicWordSell));
		config.setProperty("max-area", defaultMaxArea);
		config.setProperty("max-buy", defaultMaxBuy);
		config.setProperty("max-offers", defaultMaxOffers);
		config.setProperty("min-map-height", defaultMinMapHeight);
		config.setProperty("max-volume", defaultMaxVolume);
		config.setProperty("op-permissions", defaultOpPermissions);
		config.setProperty("show-own", defaultShowOwn);
		config.setProperty("show-all", defaultShowAll);
		config.setProperty("show-worldname", defaultShowWorldName);
		config.setProperty("signs-offground", defaultSignsOffground);
		config.setProperty("time-count-area", defaultTimeCountArea);
		config.setProperty("time-count-buy", defaultTimeCountBuy);
		config.setProperty("time-count-volume", defaultTimeCountVolume);
		config.setProperty("time-forget-transaction", defaultTimeForgetTransaction);
		config.setProperty("use-bukkit-perms", defaultUseBukkitPerms);
		config.setProperty("use-signs", defaultUseSigns);
		config.setProperty("use-worldguard-perms", defaultUseWgPerms);
		config.set("allow-member-sell", defaultAllowMemberSell);
		config.set("max-regions", 0);
		ecoMixin.addDefaultSettings(config, "economy");
		return config;
	}
	
//	/**
//	 * Get a long value from a Configuration instance.
//	 * @param config
//	 * @param key
//	 * @param preset
//	 * @return
//	 */
//	public static Long getLong(CompatConfig config, String key, Long preset ){
//		String candidate = config.getString(key, null);
//		if ( candidate == null) return preset;
//		if ( !(candidate instanceof String) ) candidate = candidate.toString();
//		try{
//			return Long.parseLong(candidate);
//		} catch (NumberFormatException e){
//			return preset;
//		}
//	}
	
	/**
	 * Load all data into memory: offers, past transactions.
	 */
	public void loadData(){
		this.offers.clear();
		this.transactions.clear();
		this.infos.clear();
		this.changed = false;
		loadUnsellable();
		File file = new File( getDataFolder(), "runtime.yml");
		if ( !file.exists() ) return;
		CompatConfig config = CompatConfigFactory.getConfig( file);
		config.load();
		long tsLoad = System.currentTimeMillis();
		// construct data from config.
		int nOffers = 0;
		// transactions:
		String prefix = "transactions";
		List<String> keys = config.getStringKeys(prefix);
		if (keys != null ){
			for ( String key : keys){
				Transaction ta = new Transaction();
				if ( ta.fromConfig(config, prefix+"."+key+".")){
					// check if expired:
					if ( ta.timestamp < tsLoad - timeForgetTransaction*msDay){
						// ignore this one.
					} else{
						this.transactions.add(ta);
						// TODO: test for null ?
						PlayerInfo info = getPlayerInfo(ta.buyerName);
						info.transactions.add(ta); // twice for quicker access.
					}
				} else {
					getServer().getLogger().warning("rbuy - Could not load transaction: "+key);
				}
			}
		}
		// offers:
		prefix = "offers";
		keys = config.getStringKeys(prefix);
		if (keys != null ){
			for ( String key : keys){
				Offer offer = new Offer();
				if ( offer.fromConfig(config, prefix+"."+key+".")){
					// TODO: check if already in ?
					putOffer(offer);
					nOffers++;
				} else {
					getServer().getLogger().warning("rbuy - Could not load offer: "+key);
				}
			}
		}
		System.out.println("rbuy - load data: "+nOffers +" offers, "+transactions.size()+" transactions.");
	}

	/**
	 * Save data from memory to file.
	 */
	public void saveData(){
		if ( !active ) return; // policy - prevent
		File file = new File( getDataFolder(), "runtime.yml");
		CompatConfig config = CompatConfigFactory.getConfig( file);
		
		// TODO: add entries from internal data
		int i = 0;
		for ( String wn : this.offers.keySet()){
			Map<String, Offer> rMap = this.offers.get(wn);
			if ( rMap == null) { // IMPOSSIBLE :)
				getServer().getLogger().severe("rbuy - Miraculous modification of offers while saving.");
				continue;
			}
			for ( Offer offer : rMap.values()){
				i++;
				String prefix = "offers.n"+i+".";
				offer.toConfig(config, prefix);
			}
		}
		i = 0;
		for ( Transaction ta : this.transactions){
			i++;
			String prefix = "transactions.n"+i+".";
			ta.toConfig(config, prefix);
		}
		if ( !config.save() ){
			getServer().getLogger().severe("rbuy - Failed to save data.");
		}
		this.changed = false;
	}
	
	// NO CONFIG BELOW HERE ----------------------------------------------------------------
	
	/**
	 * Get a playerInfo for the given player name, either from infos or a new one (then put to infos).
	 * @param playerName
	 * @return
	 */
	public PlayerInfo getPlayerInfo(String playerName){
		String key = playerName.toLowerCase();
		PlayerInfo info = this.infos.get(key);
		if ( info != null){
			return info;
		} else{
			info = new PlayerInfo();
			info.playerName = playerName;
			this.infos.put(key, info);
			return info;
		}
	}
	
	/**
	 * Process a chat command.
	 * @param sender
	 * @param command (unused)
	 * @param label
	 * @param args
	 * @return
	 */
	public boolean processCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if (command != null) label = command.getLabel();
		label = label.toLowerCase();
		if ( commandsPerSecond > 0 ){
			long tsCommand = System.currentTimeMillis();
			if (tsCommand-this.tsCommand <=1000 ){
				if ( nCommands >= commandsPerSecond ){
					if ( (sender instanceof Player) || !sender.isOp() ){
						send(sender, "rbuy - Rbuy is under heavy use, please wait a second and retry...");
						// TODO: log this somehow ?
						return true;
					}
				} else{
					nCommands++;
				}
			} else{
				this.nCommands = 1;
				this.tsCommand = tsCommand;
			}
		}
		int length = args.length;
		if ( !active ) {
			if (!label.equalsIgnoreCase("renable") && !label.equalsIgnoreCase("rreload")){
				send(sender, "rbuy - All functionality is disabled temporarily.");
				return false;
			}
		}
		
		if ( label.equalsIgnoreCase("rhelp") || ( (label.equalsIgnoreCase("rbuy")) && (args.length>=1) && (args[0].equalsIgnoreCase("help")) ) ){
			send(sender, "rbuy - No help available, use /rbuy for a short comand listing.");
			return true;
		} else if ((length==0)&&(label.equalsIgnoreCase("rbuy"))){
			send(sender, "rbuy - /rbuy <region> | /rsell <region> <price> [<currency>] ; /rsell <region> ; /rsell | /rlist | /rinfo <region> | /rremove ... | /rreload | /renable ; /rdisable | /rhelp");
			return true;
		}
		
		if ( label.equalsIgnoreCase("rsell")){
			// mind replacement strategy (ops may)
			if ( !(sender instanceof Player) ){
				// TODO: could be sold by console ...
				send(sender, "rbuy - Expect a player for using rsell.");
				return false;
			}
			
			Player player = (Player) sender;
			if ( length == 1){
				// remove region from  offers:
				boolean res = processCancelOffer(player, args[0]);
				if ( this.changed ) this.saveData();
				return res;
			} else if ( (length == 3) || (length == 2)  ){
				if ( !hasPermission(sender, "rbuy.sell")){
					send(sender, "rbuy - You don't have permission.");
					return false;
				}
				boolean res = processOffer(player, args);
				if ( this.changed ) this.saveData();
				return res;
			} else if (length == 0){
				if ( sender instanceof Player){ // cloning ?
					this.sendOwnOffers((Player) sender, null);
					return true;
				} else {
					send(sender, "rbuy - Currently only available for players: /rsell");
					return false;
				}
				
			} else{
				send(player, "rbuy - Wrong number of arguments for rsell, expect: /rsell <region> <amount> [<currency>] | /rsell <region>");
				return false;
			}
		} else if ( label.equalsIgnoreCase("rbuy")){
			if ( !hasPermission(sender, "rbuy.buy")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			if ( !(sender instanceof Player) ){
				send(sender, "rbuy - Expect a player for using rbuy.");
				return false;
			}
			Player player = (Player) sender;
			if (length == 1){
				boolean res = this.processBuy( player, args[0]); // could use the result...
				if ( this.changed ) this.saveData();
				return res; 
			} else{
				send(sender,"rbuy - Wrong number of arguments for rbuy, expect /rbuy <region>.");
				return false;
			}
		} else if (label.equalsIgnoreCase("rinfo")){
			if ( !hasPermission(sender, "rbuy.info")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			if ( (length ==1)  ){
				// most detailed info
				if ( sender instanceof Player){
					showInfo(sender, args[0], ((Player) sender).getWorld().getName());
				} else{
					showInfo(sender, args[0]);
				}
			} else if ((sender instanceof Player)){
				// medium/less detailed info
				showNearbyInfo((Player) sender);
			} else{
				// most detailed info
				showAllInfos(sender);
			}
			return true;
		} else if (label.equalsIgnoreCase("rlist")){
			if ( !hasPermission(sender, "rbuy.list")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			// just show names
			if ( length == 0) showAllOffers(sender, null);
			else if ( length == 1 ) showAllOffers(sender, args[0]);
			return true;
		} else if (label.equalsIgnoreCase("rreload")){
			if ( !hasPermission(sender, "rbuy.reload")){
				send(sender,"rbuy - You don't have permission.");
				return false;
			}
			this.saveData(); // TODO: rethink this - maybe if (this.changed) ...
			this.reloadSettings();
			this.loadData();
			send( sender, "rbuy - Reloaded configuration.");
			return true;		
		}  else if ( label.equalsIgnoreCase("renable")){
			if ( !hasPermission(sender, "rbuy.enable")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			setActive(true);
			send(sender, "rbuy - Active (possibly till next reload, only).");
			return true;
		} else if ( label.equalsIgnoreCase("rdisable")){
			if ( !hasPermission(sender, "rbuy.disable")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			setActive(false);
			send(sender, "rbuy - Active (possibly till next reload, only).");
			return true;
		} else if (label.equalsIgnoreCase("rremove")){
			if ( !hasPermission(sender, "rbuy.remove")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			// rremove p:<playername> w:[<worldname>] r:<region> 
			boolean res = processRemove(sender, args);
			if ( this.changed ) this.saveData(); 
			return res;
		} else if (label.equalsIgnoreCase("runsellable")){
			String world;
			String rid;
			if (args.length == 1){
				if (!(sender instanceof Player)) return false;
				world = ((Player) sender).getWorld().getName();
				rid = args[0];
			}
			else if (args.length == 2){
				world = args[0];
				rid = args[1];
			}
			else return false;
			// permission checks are done inside of the method:
			processUnsellable(sender, world, rid);
			return true;
		} else if (label.equalsIgnoreCase("rcheckoffers") && args.length > 0){
			if ( !hasPermission(sender, "rbuy.checkoffers")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			boolean all = false;
			Set<String> worlds = new HashSet<String>();
			for (int i = 0; i < args.length; i ++){
				String w = args[i].trim().toLowerCase();
				if (w.equals("*")) all = true;
				else worlds.add(w);
			}
			int removed = processCheckOffers(worlds, all);
			sender.sendMessage("[rbuy] Removed " + removed + " offers.");
			if (changed) saveData();
			return true;
		}
		else if (label.equalsIgnoreCase("rtransfer")){
			if (!hasPermission(sender, "rbuy.transfer")){
				send(sender, "rbuy - You don't have permission.");
				return false;
			}
			if (!(sender instanceof Player) && args.length < 4 ){
				// TODO: could be sold by console ...
				send(sender, "rbuy - Expect a player for using rtransfer with incomplete arguments.");
				return false;
			}
			if (args.length != 2 && args.length != 3 && args.length != 4) return false;
			processTransfer(args.length == 4 ? args[3] : ((Player) sender).getWorld().getName(), args[0], args[1], args.length == 2 ? sender.getName() : args[2], sender);
			return true;
		}
		
		send(sender, "rbuy - unknown options/command: "+label);
		return false;
	}
	
	/**
	 * 
	 * @param world
	 * @param regionName May end on '*' to indicate it being used as a prefix.
	 * @param newOwnerName 
	 * @param oldOwnerName
	 */
	public void processTransfer(final String worldName, String regionName, final String newOwnerName, final String oldOwnerName, final CommandSender notify){
		final boolean isPrefix;
		if (regionName.endsWith("*")){
			isPrefix = true;
			regionName = regionName.substring(0, regionName.length() - 1).toLowerCase();
		}
		else{
			isPrefix = false;
			regionName = regionName.toLowerCase();
		}
		final boolean allWorlds = worldName.equals("*");
		final Server server = getServer();
		final WorldGuardPlugin wg = Utils.getWorldGuard();
		// TODO: Consider removing invalid offers here. 
		int totalDone = 0;
		for (final Entry<String, Map<String, Offer>> wEntry : offers.entrySet()){
			final String w = wEntry.getKey();
			if (!allWorlds && !w.equalsIgnoreCase(worldName)){
				continue;
			}
			final World world = server.getWorld(w);
			if (w == null){
				notify.sendMessage("rtransfer: Skip world " + w + ", due to not being loaded.");
			}
			final RegionManager man = wg.getRegionManager(world);
			int transfered = 0;
			for (final Entry<String, Offer> rEntry : wEntry.getValue().entrySet()){
				final String rid = rEntry.getKey();
				if (!(isPrefix && rid.startsWith(regionName)) && !rid.equals(regionName)){
					continue;
				}
				final Offer offer = rEntry.getValue();
				if (!oldOwnerName.equalsIgnoreCase(offer.benefits)){
					continue;
				}
				final ProtectedRegion region = man.getRegion(rid);
				if (region == null){
					continue;
				}
				if (!region.isOwner(oldOwnerName)){
					continue;
				}
				// Transfer ownership and offer:
				final DefaultDomain owners = region.getOwners();
				owners.removePlayer(oldOwnerName);
				owners.addPlayer(newOwnerName);
				offer.benefits = newOwnerName;
				transfered ++;
			}
			if (transfered > 0){
				// transfered
				notify.sendMessage("rtransfer: Transfered " + transfered + " regions for world " + world.getName() + ".");
				try {
					// TODO: Inefficient.
					man.save();
				} catch (ProtectionDatabaseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				totalDone += transfered;
			}
		}
		if (totalDone > 0){
			saveData();
		}
		notify.sendMessage("rtransfer: done.");
	}

	/**
	 * 
	 * @param worlds Lower case world names.
	 * @param all If to check all worlds.
	 * @return
	 */
	private int processCheckOffers(Set<String> worlds, boolean all) {
		List<Offer> rem = new LinkedList<Offer>();
		WorldGuardPlugin wg = getWorldGuard();
		for (String w : offers.keySet()){
			if (!all && !worlds.contains(w)) continue;
			Map<String, Offer> map = offers.get(w);
			World world = getServer().getWorld(w);
			if (world == null){
				rem.addAll(map.values());
				continue;
			}
			RegionManager man = wg.getRegionManager(world);
			for (Offer offer : map.values()){
				ProtectedRegion region = man.getRegion(offer.regionName);
				if (region == null){
					rem.add(offer);
					continue;
				}
				if (!isExclusiveOwner(offer.benefits, region)){
					rem.add(offer);
					continue;
				}
			}
		}
		if (rem.isEmpty()) return 0;
		for (Offer offer : rem){
			removeOffer(offer);
		}
		saveData();
		return rem.size();
	}

	public void processUnsellable(CommandSender sender, String worldName,
			String rid) {
		worldName = worldName.trim().toLowerCase();
		World world = getServer().getWorld(worldName);
		if (world == null){
			sender.sendMessage(ChatColor.DARK_RED+"[rbuy] World does not exist: "+worldName);
			return;
		}
		rid = rid.trim().toLowerCase();
		ProtectedRegion region = getRegion(world, rid);
		if (region == null){
			sender.sendMessage(ChatColor.DARK_RED+"[rbuy] Region does not exist (world: "+worldName+"): "+rid);
			return;
		}
		if (!hasPermission(sender, "rbuy.unsellable.set")){
			boolean hasPerm = false;
			if (sender instanceof Player){
				if (region.isOwner(((Player) sender).getName())){
					if (hasPermission(sender, "rbuy.unsellable.set.region.own")) hasPerm = true;
				}
			}
			if (!hasPerm){
				sender.sendMessage(ChatColor.DARK_RED+"[rbuy] You don't have permission.");
				return;
			}
		}
		// (permission given)
		sender.sendMessage("The region "+region.getId()+" ("+world.getName()+") is now: "+((toggleUnsellable(worldName, rid))?(ChatColor.RED+"unsellable"):(ChatColor.YELLOW+"sellable")));
	}

	/**
	 * Return if now unsellable.
	 * @param worldName
	 * @param rid
	 * @return
	 */
	public boolean toggleUnsellable(String worldName, String rid) {
		worldName = worldName.trim().toLowerCase();
		rid = rid.trim().toLowerCase();
		Set<String> rids = unsellable.get(worldName);
		final boolean res;
		if (rids == null){
			rids = new HashSet<String>();
			rids.add(rid);
			unsellable.put(worldName, rids);
			res = true;
		}
		else{
			res = !rids.remove(rid);
			if (res) rids.add(rid); // to be added.
		}
		saveUnsellable();
		return res;
	}
	
	public File getUnsellableFile(){
		return new File(getDataFolder(), "unsellable.yml");
	}
	
	private void saveUnsellable() {
		CompatConfig cfg = CompatConfigFactory.getConfig(getUnsellableFile());
		for (String w : unsellable.keySet() ){
			Set<String> rids = unsellable.get(w);
			if (rids == null || rids.isEmpty()) continue;
			List<String> all = new LinkedList<String>(rids);
			cfg.set(w, all);
		}
		cfg.save();
	}

	private void loadUnsellable() {
		unsellable.clear();
		CompatConfig cfg = CompatConfigFactory.getConfig(getUnsellableFile());
		cfg.load();
		for (String w : cfg.getStringKeys()){
			List<String> rids = cfg.getStringList(w, null);
			if (rids == null || rids.isEmpty()) continue;
			Set<String> uns = new HashSet<String>();
			for (String rid : rids){
				uns.add(rid.trim().toLowerCase());
			}
			unsellable.put(w.trim().toLowerCase(), uns);
		}
	}

	/**
	 * Remove offers for players, worlds, regions.
	 * Args will have prefixes like 'w:', 'p:' or 'r:',
	 * then r/w allow for a '*' entry as well as prefix* entries,
	 * while p only allows for full player names or '*' for all.
	 * Given player names will lead to only removing regions for the given players.
	 * Worlds will include all given worlds.
	 * Regions will include all given regions.
	 * If a player calls this and does not specify a world, the world the player is in is used, other CommandSenders must specify a world.
	 * @param sender
	 * @param args
	 * @return
	 */
	public boolean processRemove(CommandSender sender, String[] args) {
		if ( args.length == 0 ){
			send(sender, "rbuy - Expect a specification what to remove (Arguments can be: p:<player> or p:* for players, r:<region> or r:* or r:<prefix>* for regions, w:<world> or w:* or w:<prefix>* for worlds).");
			return false;
		}
		Set<String> worlds = new HashSet<String>();
		Set<String> players = new HashSet<String>();
		Set<String> regions = new HashSet<String>();
		Set<String> newOwners = new HashSet<String>();
		List<String> worldPrefixes = new LinkedList<String>();
		List<String> regionPrefixes = new LinkedList<String>();
		boolean allWorlds = false;
		boolean allPlayers = false;
		boolean allRegions = false;
		// process args:
		for (String arg : args){
			arg = arg.trim();
			if ( (arg.length()>2) && (arg.charAt(1)==':')){
				char tp = arg.charAt(0); // type 
				arg = arg.substring(2).toLowerCase();
				if (tp=='p'){
					if (arg.equalsIgnoreCase("*")) allPlayers = true;
					else players.add(arg);
				} else if (tp=='r'){
					if (arg.equalsIgnoreCase("*")) allRegions = true;
					else if (arg.endsWith("*")) regionPrefixes.add(arg.substring(0, arg.length()-1));
					else regions.add(arg);
				} else if (tp=='w'){
					if (arg.equalsIgnoreCase("*")) allWorlds = true;
					else if (arg.endsWith("*")) worldPrefixes.add(arg.substring(0, arg.length()-1));
					else worlds.add(arg);
				} else if (tp=='x') {
					// special commands
					String lcArg = arg.toLowerCase();
					if (lcArg.startsWith("own:")){
						if ( arg.length()>4){
							newOwners.add(arg.substring(4, arg.length()));
						}
					} else{
						send( sender, "rbuy - Bad specification of sub command for rremove: "+tp+":"+arg);
						return false;
					}
				}else{
					send( sender, "rbuy - Bad specification for rremove: "+tp+":"+arg);
					return false;
				}
			} else {
				send(sender, "rbuy: Bad specification for rremove: "+arg);
				return false;
			}
		}
		if (!allWorlds){
			if ( worlds.isEmpty() && worldPrefixes.isEmpty() ){
				if ( sender instanceof Player){
					worlds.add(((Player)sender).getWorld().getName().trim().toLowerCase());
				} else{
					send(sender, "rbuy - Non players must specify a world from which to remove offers.");
					return false;
				}
			}
		}
		if (!allPlayers && players.isEmpty()){
			send(sender, "rbuy - Must specify player(s) for rremove.");
			return false;
		} 
		if (!allRegions && regions.isEmpty() && regionPrefixes.isEmpty()){
			send(sender, "rbuy - Must specify region(s) for rremove.");
			return false;
		}
		// One could optimize this (if !allPlayers -> go through player infos instead), but this is not called very often, presumably.
		List<Offer> removeThese = new LinkedList<Offer>();
		for ( String wn : this.offers.keySet()){
			if ( allWorlds || matches(wn, worlds, worldPrefixes)){
				Map<String, Offer> offers =this.offers.get(wn);
				if ( offers == null) continue; // throw new IllegalStateException
				for ( String rn : offers.keySet() ){
					if ( allRegions || matches(rn, regions,regionPrefixes)){
						Offer offer = offers.get(rn);
						if (offer == null) continue; // throw new ...
						if (allPlayers || players.contains(offer.benefits.toLowerCase())) removeThese.add(offer);
					}
				}
			}
		}
		if ( !removeThese.isEmpty()){
			if ( !newOwners.isEmpty()){
				for ( Offer offer : removeThese){
					setOwners(offer.worldName, offer.regionName, newOwners);
				}
				if ( allWorlds) saveRegions(null, sender);
				else saveRegions(worlds, sender);
			}
			for ( Offer offer : removeThese){
				this.removeOffer(offer);
			}
			this.changed = true;
		}
		send(sender, "rbuy - Removed "+removeThese.size()+" offers.");
		return true;
	}
	
	/**
	 * NOTE: DOES NOT SAVE REGIONS, call saveRegions
	 * @param worldName
	 * @param regionName
	 * @param newOwners
	 */
	public static void setOwners(String worldName, String regionName,
			Set<String> newOwners) {
		World world = Bukkit.getServer().getWorld(worldName);
		if ( world == null) return;
		RegionManager man = getWorldGuard().getRegionManager(world);
		ProtectedRegion region = man.getRegion(regionName);
		if (region == null) return;
		region.setMembers(new DefaultDomain());
		DefaultDomain dom = new DefaultDomain();
		for (String pn : newOwners){
			dom.addPlayer(pn);
		}
		region.setOwners(dom);
	}
	
	/**
	 * If null given: sdave all worlds.
	 * 
	 * @param worldNames
	 * @param notify 
	 * @return Number of successful saves (includes non existing worlds!).
	 */
	public static int saveRegions( Set<String> worldNames, CommandSender notify ){
		List<World> worlds = new LinkedList<World>();
		int success = 0;
		if ( worldNames== null){
			worlds.addAll(Bukkit.getServer().getWorlds());
		} else{
			for ( String name : worldNames ){
				World world = Bukkit.getServer().getWorld(name);
				if ( world != null) worlds.add(world);
			}
		}
		for (World world : worlds){
			if (saveRegions(world, notify, null)) success++;
		}
		return success;
	}

	/**
	 * Check if a given String matches either directly or one of the prefixes - case sensitive (!).
	 * Allows null on each argument.
	 * @param item
	 * @param fullRef
	 * @param prefixRef
	 * @return
	 */
	public static boolean matches(String item, Set<String> fullRef, List<String> prefixRef){
		if ( item==null ) return false;
		if ( (fullRef != null) && fullRef.contains(item)) return true;
		if ( prefixRef != null ){
			for ( String ref : prefixRef){
				if ( item.startsWith(ref)) return true;
			}
		}
		return false;
	}

	/**
	 * Show region offers that are nearby the players current location.
	 * @param player
	 */
	public void showNearbyInfo(Player player) {
		World world = player.getWorld();
		String body = "";
		LinkedList<String> rNames = new LinkedList<String>();
		// TODO: Use a WorldEdit selection or a WorldGuard region or some Tree-structure for finding nearby regions.
		String playerName = player.getName();
		RegionManager manager = getWorldGuard().getRegionManager(world);
		if ( manager != null){
			Map<String, Offer> offers = this.offers.get(world.getName().toLowerCase());
			if ( offers != null){
				for ( Offer offer:offers.values()){ // HILARIOUS
					if (!showOwn && (playerName.equalsIgnoreCase(offer.benefits)) ){
						
					} else{
						rNames.add(offer.regionName);
					}
				}
				for ( String rn : sortedStrings(rNames)){
					ProtectedRegion region = manager.getRegion(rn);
					Offer offer = offers.get(rn.toLowerCase());
					if ( region != null){
						if ( checkDistance(player, region, infoRadius)){
							body+=" "+ChatColor.YELLOW+offer.regionName+ChatColor.WHITE+"["+offer.amount+" "+offer.currency+"]";
						}
					}
				}
			}
		}
	
		if ( body.length() == 0){
			send( player, "rbuy - No nearby offers found ("+world.getName()+").");
		} else{
			send( player, "rbuy - Nearby offers ("+world.getName()+"):"+body);
		}
	}

	/**
	 * Show all detaild infos, for console only.
	 * @param sender
	 */
	public void showAllInfos(CommandSender sender) {
		String own = "";
		boolean restrict = !showOwn && (sender instanceof Player);
		if (restrict) own = sender.getName();
		send(sender, "rbuy - all infos:");
		for (String wn : this.offers.keySet()){
			Map<String, Offer> offers = this.offers.get(wn);
			if ( offers != null){
				send(sender, "rbuy - Offers for world: "+wn);
				for (String rn : sortedStrings(offers.keySet())){
					Offer offer = offers.get(rn);
					if ( !restrict || !own.equalsIgnoreCase(offer.benefits)){
						showInfo(sender, offer.regionName);
					}
				}
			}
			
		}
	}

	/**
	 * Show detailed info about a specific region-offer.
	 * @param sender
	 * @param rgn
	 */
	public void showInfo(CommandSender sender, String rgn, String worldName) {
		String rn = rgn.trim().toLowerCase();
		Offer offer = getOffer(rn, worldName);
		if (offer != null){
			send(sender ,"rbuy - info: "+getSingleInfo(offer, showWorldName));
		}else{
			send(sender,"rbuy - No offer for: "+rgn);
		}
	}
	
	public void showInfo(CommandSender sender, String rgn){
		boolean found = false;
		for ( String wn : this.offers.keySet()){
			Offer offer = this.getOffer(rgn,  wn);
			if ( offer != null ){
				found = true;
				send(sender ,"rbuy - info: "+getSingleInfo(offer, showWorldName));
			}
		}
		if ( !found) send(sender, "rbuy - No offers found for: "+rgn);
	}

	public String getSingleInfo(String rgn, String worldName, boolean showWorldName) {
		String rn = rgn.trim().toLowerCase();
		Offer offer = getOffer(rn, worldName);
		return getSingleInfo(offer, showWorldName);
	}
	
	public String getSingleInfo( Offer offer, boolean showWorldName){
		if ( offer == null) return "";
		String wn = "";
		if (showWorldName ) wn = offer.worldName+" | ";
		String dim;
		ProtectedRegion reg = getRegion(offer);
		if (reg == null){
			dim = "?x?x? for ";
		}
		else{
			int[] sz = getDimensions(reg);
			dim = sz[0] + "x" + sz[1] + "x" + sz[2] + " for ";
		}
		return offer.regionName + " [" + wn + dim + offer.amount + " " + offer.currency + " from " + offer.benefits + "]";
	}

	public int[] getDimensions(ProtectedRegion reg) {
		BlockVector min = reg.getMinimumPoint();
		BlockVector max = reg.getMaximumPoint();
		return new int[]{max.getBlockX() - min.getBlockX() + 1, max.getBlockY() - min.getBlockY() + 1, max.getBlockZ() - min.getBlockZ() + 1};
	}

	/**
	 * 
	 * @param sender
	 */
	public void showAllOffers(CommandSender sender, String prefix) {
		if ( !showAll && (sender instanceof Player) && !hasPermission((Player)sender, "rbuy.show-all") ){
			send(sender, "rbuy - Showing all entries is disabled.");
			return;
		}
		if ( prefix != null) prefix = prefix.toLowerCase();
		String wn = "";
		boolean worldSpecific = this.listWorldSpecific;
		if ( sender instanceof Player ){
			wn = ((Player)sender).getWorld().getName();
		} else worldSpecific = false;
		boolean found = false;
		for ( String worldName : this.offers.keySet()){
			Map<String, Offer> offers = this.offers.get(worldName);
			if ( offers != null){
				if ( worldSpecific &&  !wn.equalsIgnoreCase(worldName)) continue;
				StringBuilder builder = new StringBuilder ();
				for (String rn : sortedStrings(offers.keySet())){
					if ((prefix!=null) && !rn.startsWith(prefix) ) continue;
					Offer offer = offers.get(rn);
					builder.append(" "+offer.regionName);
					// maybe check for consistency (worldName) later on.
				}
				String msg = builder.toString();
				if (  msg.length()>0){
					send(sender, "rbuy - Offers ("+wn+"): "+msg);
					found = true;
				}
			}
		}
		if (!found) send(sender, "rbuy - No offers present.");
	}

	/**
	 * Display own offers.
	 * @param player
	 */
	public void sendOwnOffers(Player player, String prefix ) {
		PlayerInfo info = this.infos.get(player.getName());
		if ( (info == null) || (info.offers.size() == 0)){
			send(player, "rbuy - You are not offering any regions for sale.");
		} else{
			String msg = "rbuy - Your offers: ";
			// TODO: list worldspecific, list by prefix
			ArrayList<String> rns = new ArrayList<String>(info.offers.size());
			if ( prefix == null){
				for ( Offer offer: info.offers ){
					rns.add(offer.regionName);
				}
			} else{
				prefix = prefix.trim().toLowerCase();
				for ( Offer offer: info.offers ){
					if ( offer.regionName.toLowerCase().startsWith(prefix)) rns.add(offer.regionName);
				}
			}
			for (String rn : sortedStrings(rns)){
				msg += rn+" ";
			}
			send(player, msg);
		}
	}

	/**
	 * Call with two or three args (!).<br>
	 * rbuy.sell is not checked in this method.
	 * @param player
	 * @param args
	 * @return
	 */
	public boolean processOffer(Player player, String[] args) {
		MixinEconomyInterface eco = ecoMixin.getEconomyInterface();
		String currency = eco.getDefaultCurrency();
		if ( args.length == 3){
			currency = args[2].trim().toLowerCase();
			if (!eco.isAcceptedCurrency(currency)){
				send(player, "The currency is not accepted: "+currency);
				return false;
			}
		} 
		String rgn = args[0].trim();
		if ( rgn.equalsIgnoreCase("__global__")){
			send(player, "rbuy - The region '__global__' can not be sold.");
			return false;
		}
		if (isUnsellable(player.getWorld().getName(), rgn)){
			send(player, ChatColor.DARK_RED+"[rbuy] This region is unsellable.");
			return false;
		}
		double amount = -1;
		try{
			amount = Double.parseDouble(args[1]);
		} catch (NumberFormatException e){
			
		}
		if ( amount < 0 ){
			send(player, "rbuy - invalid amount: "+args[1]);
			return false;
		}
		
		if ( !canSellRegion(player, rgn)){
			send(player, "rbuy - You do not have the exclusive ownership or rights to sell the region: "+rgn);
			return false;
		}
		String playerName = player.getName();
		PlayerInfo info = getPlayerInfo(playerName);
		if ( (this.maxOffers>0) && (info.offers.size()>=this.maxOffers) && !hasPermission(player, "rbuy.max-offers")){
			send(player, "rbuy - You can only place "+maxOffers+" offers at a time.");
			return false;
		}
		World world = player.getWorld();
		ProtectedRegion region = getRegion(world, rgn);
		
		if ( region == null ){
			send(player, "rbuy - The region "+rgn+" does not exist for the world: "+world.getName());
			return false;
		}
		if ( distanceSell > 0 ){
			if ( !checkDistance(player, region, distanceSell) && !hasPermission(player, "rbuy.distance-sell") ){
				send(player, "rbuy - You need to be no farther than "+distanceSell+" blocks away from the region you want to offer.");
				return false;
			}
		}
		if ( !region.hasMembersOrOwners()){
			// (has been checked above already: permission "rbuy.sell-unowned")
			setExclusiveOwner(playerName, world, region); // TODO: subject to policy 
		}
		Offer offer = new Offer();
		offer.benefits = playerName;
		offer.regionName = rgn;
		offer.amount = amount;
		offer.worldName = world.getName();
		offer.currency = getCurrency(currency);
		putOffer(offer); // removes old offer if present.
		send(player, "rbuy - Placed offer for "+amount+" "+currency+" for: "+rgn);
		this.changed = true;
		return true;
	}

	public boolean processCancelOffer(Player player, String rgn) {
		String wn = player.getWorld().getName();
		String playerName = player.getName();
		if ( rgn.equalsIgnoreCase("*")){
			PlayerInfo info = getPlayerInfo(playerName);
			List<Offer> offers = new LinkedList<Offer>();
			offers.addAll(info.offers);
			for ( Offer offer: offers){
				removeOffer(offer);
			}
			send(player, "rbuy - Removed all offers ("+offers.size()+").");
			return true;
		}
		
		Offer offer = getOffer(rgn,wn);
		if ( offer == null ){
			send(player, "rbuy - No offer in world '"+wn+"' for region: "+rgn);
			return true;
		}
		if ( !playerName.equalsIgnoreCase(offer.benefits)){
			World world = player.getWorld();
			ProtectedRegion region = getRegion(world, rgn);
			if ( region == null){
				send(player, "rbuy - the region does not exist: "+rgn);
				return false;
			}
			if (!region.isOwner(getWorldGuard().wrapPlayer(player))){
				send(player, "rbuy - You are not owner of the region, neither is this your offer: "+rgn);
				return false;
			}
		}
		// owner of region or offer can remove it
		removeOffer(offer);
		send(player, "rbuy - offer removed: "+rgn);
		changed = true;
		return true;
	}

	/**
	 * Getting the economy-interface.
	 * @return 
	 * @return
	 */
	public EconomyMixin getEconomyMixin(){
		return ecoMixin;
	}
	
	/**
	 * Check if the player is allowed to sell the region,
	 * i.e. is the only owner, with no members. ALso successful if the region no owners or  members.
	 * This is for checking when buying (seller is potentially offline).
	 * @param playername
	 * @param regionName
	 * @return
	 */
	boolean canSellRegion(String playername, ProtectedRegion region){
		if ( !region.hasMembersOrOwners() ){
			// policy, keep it in mind (!).
			return false;
		}
		return isExclusiveOwner(playername , region);
	}
	
	/**
	 * Exclusive owner, not member, no others.
	 * if allowMemberSell is set members may be set.
	 * @param playername
	 * @param region
	 * @return
	 */
	public boolean isExclusiveOwner(String playername, ProtectedRegion region) {
		DefaultDomain dom;
		if ( !allowMemberSell){
			dom = region.getMembers();
			if ( dom.size() != 0) return false;
		}
		dom = region.getOwners();
		if (dom.size() != 1) return false;
		if (playername.equalsIgnoreCase(dom.toPlayersString())) return true;
		return false;
	}

	/**
	 * Check for ownership issues. 
	 * Currently only exclusive ownership allows for //
	 *  if allowMemberSell is set members may be set.
	 * @param player
	 * @param rgn
	 * @return
	 */
	public boolean canSellRegion( Player player, String rgn){
		World world = player.getWorld();
		if (isUnsellable(world.getName(), rgn)) return false;
		ProtectedRegion region = getRegion(world, rgn);
		if ( region == null ) return false;
		if ( !region.hasMembersOrOwners() ){
			return hasPermission(player, "rbuy.sell-unowned");
		}
		return isExclusiveOwner(player.getName(), region);
	}
	
	/**
	 * Get the WorldGuardPlugin (no checks!).
	 * @return
	 */
	public static WorldGuardPlugin getWorldGuard(){
		Plugin plg = Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
		return (WorldGuardPlugin) plg;
	}
	
	public static ProtectedRegion getRegion(World world, String regionName){
		return getWorldGuard().getRegionManager(world).getRegion(regionName);
	}
	
	/**
	 * Get a new instance.
	 * @return
	 */
	public RegionFilterSettings getDefaultRegionFilterSettings() {
		RegionFilterSettings rfs = new RegionFilterSettings();
		rfs.maxBuy = maxBuy;
		rfs.timeCountBuy = timeCountBuy;
		return rfs;
	}
	
	/**
	 * Test if a transaction is to be counted in.
	 * @param ta
	 * @return true if expired
	 */
	public boolean isBuyExpired(final Transaction ta, final long ts) {
		int daysExpireBuy = timeCountBuy;
		if (ta.restrictions != null && ta.worldName != null) {
			// Regions for which filter settings might exist.
			final Map<String, RegionFilterSettings> worldMap = filterSettings.get(ta.worldName.toLowerCase());
			if (worldMap != null) {
				for (final String regionName : ta.restrictions) {
					final RegionFilterSettings rfs = worldMap.get(regionName.toLowerCase());
					if (rfs != null && rfs.timeCountBuy != null) {
						daysExpireBuy = Math.max(daysExpireBuy, rfs.timeCountBuy);
					}
				}
			}
		}
		return ta.timestamp < ts - msDay * (long) daysExpireBuy;
	}
	
	/**
	 * Get data such as number of bought regions, area, volume, sold regions.
	 * @param info
	 * @param ts
	 * @return
	 */
	public EdgeData getEdgeData(final PlayerInfo info, final long ts) {
		// re model: check global and per restriction.
		// TODO: set world name on tas
		final EdgeData ed = new EdgeData();
		final LinkedList<Transaction> rem = new LinkedList<Transaction>();
		// global timestamps
		final long tsExpireTa = ts - timeForgetTransaction * msDay;
		final long tsExpireBuy = ts - timeCountBuy * msDay;
		final long tsExpireArea = ts - timeCountArea * msDay;
		final long tsExpireVolume = ts - timeCountVolume * msDay;
		for (Transaction ta: info.transactions){
			if (ta.timestamp < tsExpireTa) {
				// Removal of transaction.
				rem.add(ta);
			}
			else {
				// Global statistics.
				if (ta.timestamp >= tsExpireBuy) ed.nBuy++;
				if (ta.timestamp >= tsExpireArea) ed.areaBuy += ta.area;
				if (ta.timestamp >= tsExpireVolume) ed.volumeBuy += ta.volume;
				// "Lightly" check restriction (no re-checking regions).
				if (ta.restrictions != null && ta.worldName != null) {
					// Regions for which filter settings might exist.
					final Map<String, RegionFilterSettings> worldMap = filterSettings.get(ta.worldName.toLowerCase());
					if (worldMap != null) {
						int presetTimeCountBuy = timeCountBuy;
						final RegionFilterSettings rfsGlobal = worldMap.get("__global__");
						if (rfsGlobal != null && rfsGlobal.timeCountBuy != null) {
							presetTimeCountBuy = rfsGlobal.timeCountBuy;
						}
						for (final String regionName : ta.restrictions) {
							final String lcRid = regionName.toLowerCase();
							final RegionFilterSettings rfs = worldMap.get(lcRid);
							if (rfs != null) {
								if (ta.timestamp >= ts - msDay * (rfs.timeCountBuy == null ? presetTimeCountBuy : rfs.timeCountBuy)) {
									if (ed.nRestriction == null) {
										ed.nRestriction = new HashMap<String, Integer>();
										ed.nRestriction.put(lcRid, 1);
									} else {
										final Integer n = ed.nRestriction.get(lcRid);
										if (n == null) {
											ed.nRestriction.put(lcRid, 1);
										} else {
											ed.nRestriction.put(lcRid, n + 1);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		if ( !rem.isEmpty()){
			info.transactions.removeAll(rem);
			this.transactions.removeAll(rem);
			changed = true;
		}
		ed.nTotal = info.transactions.size();
		return ed;
	}
	
	/**
	 * Get n offer for the given region name and world name.
	 * @param regionName
	 * @param worldName
	 * @return
	 */
	public Offer getOffer(String regionName, String worldName){
		Map<String, Offer> rMap  = this.offers.get(worldName.toLowerCase());
		if (rMap == null) return null;
		Offer offer = rMap.get(regionName.toLowerCase());
		if ( offer == null ) return null;
		// direct return should be possible.
		if ( offer.worldName.equalsIgnoreCase(worldName)){
			// success
			return offer; 
		}
		// safety (remove lateron)
		getServer().getLogger().warning("rbuy - The region '"+offer.regionName+"' from '"+offer.worldName+"' is sorted into world: "+worldName);
		return null;
	}
	
	/**
	 * Put offer to internals, remove old one from internals if existent.
	 * This does NOT save offers, but sets the changed flag.
	 * (This adds the offer to a PlayerInfo.)
	 * @param offer
	 * @return
	 */
	public boolean putOffer(Offer offer){
		PlayerInfo info = getPlayerInfo(offer.benefits); // TODO: check for null
		info.offers.add(offer);
		String worldName = offer.worldName.toLowerCase();
		String regionName = offer.regionName.toLowerCase();
		Map<String, Offer> rMap  = this.offers.get(worldName);
		this.changed = true;
		if ( rMap == null){
			rMap = new HashMap<String, Offer>();
			this.offers.put(worldName, rMap);
		}
		boolean out = rMap.containsKey(regionName);
		if (out) removeOffer(rMap.get(regionName));
		rMap.put(regionName, offer);
		return out;
	}
	
	/**
	 * Remove an offer from the data.
	 * @param offer
	 * @return if offer was found somewhere (offers/infos)
	 */
	public boolean removeOffer(Offer offer){
		if ( offer == null){
			return false;
		}
		String worldName = offer.worldName.toLowerCase();
		String regionName = offer.regionName.toLowerCase();
		Map<String, Offer> rMap  = this.offers.get(worldName);
		boolean out = false;
		if ( rMap != null ){
			if (rMap.remove(regionName) != null){
				out = true;
				this.changed = true;
			}
			if (rMap.isEmpty()) this.offers.remove(worldName);
		}
		if ( offer.benefits!=null){
			String lc = offer.benefits.toLowerCase();
			PlayerInfo info = this.infos.get(lc);
			if ( info != null){
				if ( info.offers.remove(offer)){
					out = true;
					this.changed = true;
				}
				if ( info.isEmpty() ){
					infos.remove(lc);
				}
			}
		}
		return out;
	}
	
	public boolean isUnsellable(String worldName, String rid){
		Set<String>  rids = unsellable.get(worldName.trim().toLowerCase());
		if (rids == null) return false;
		return rids.contains(rid.trim().toLowerCase());
	}

	/**
	 * rbuy.buy permission is not checked in here !
	 * @param player
	 * @param regionName
	 * @return success of buying
	 */
	public boolean processBuy(Player player, String regionName) {
		final long ts = System.currentTimeMillis();
		regionName = regionName.trim();
		final World world = player.getWorld();
		final Offer offer = getOffer(regionName, world.getName());
		if (offer == null ){
			send(player, "rbuy - In this world there is no offer for: "+regionName);
			return false;
		}
		if (isUnsellable(offer.worldName, offer.regionName)){
			send(player, ChatColor.DARK_RED+"[rbuy] This region is unsellable.");
			removeOffer(offer);
			return false;
		}
		final String playerName = player.getName();
		if (playerName.equalsIgnoreCase(offer.benefits)){
			send(player, "rbuy - Your offer: "+offer.regionName);
			return false;
		}
		
		final String benefits = offer.benefits;
		if (benefits == null ){
			send(player, "rbuy - Expect the name of the seller to be set, to benefit from the transaction.");
			// TODO: policy / defaultName, anonymous transaction.
			removeOffer(offer);
			changed = true;
			return false;
		}
		final RegionManager man = Utils.getWorldGuard().getRegionManager(world);
		final ProtectedRegion region = man.getRegion(regionName);
		if (region == null){
			send(player, "rbuy - The region seems not to exist in this world: "+regionName);
			// keep, in case it is diferent managers for different worlds.
			return false;
		}
		
		final PlayerInfo info = getPlayerInfo(playerName);
		final EdgeData edgeData = getEdgeData(info, ts);
		if (!this.canSellRegion(benefits, region)){
			send(player, "rbuy - The player "+benefits+" is not authorized to sell the region: "+regionName);
			removeOffer(offer);
			changed = true;
			return false;
		}
		if (allowMemberSell){
			DefaultDomain members = region.getMembers();
			if ( members.size()>0){
				if (!members.contains(getWorldGuard().wrapPlayer(player))){
					send(player, "rbuy - The region can only be bought by members.");
					return false;
				}
			}
		}
		long area =  getArea(region);
		// check versus boundaries of info:
		if (this.maxArea > 0){
			if ((edgeData.areaBuy + area > this.maxArea) && !hasPermission(player, "rbuy.max-area") ){
				String msg = "rbuy - The area of the regions bought by you is too big in total";
				if ( this.timeCountArea == 0 ) msg += "."; // TODO: timeCountBuy/timeCountTransaction
				else msg += " (counting the last "+ this.timeCountArea+" days).";
				send(player, msg);
				return false;
			}
		}
		long volume = region.volume();
		if (this.maxVolume > 0 ){
			if ((edgeData.volumeBuy + volume > this.maxVolume) && !hasPermission(player, "rbuy.max-volume") ){
				String msg = "rbuy - The volume of the regions bought by you is too big in total";
				if ( this.timeCountVolume == 0 ) msg += "."; // TODO: timeCountBuy/timeCountTransaction
				else msg += " (counting the the last "+ this.timeCountVolume+" days).";
				send(player, msg);
				return false;
			}
		}
		if (this.maxBuy > 0){
			if ((edgeData.nBuy >= this.maxBuy) && !hasPermission(player, "rbuy.max-buy")){
				String msg = "rbuy - You can only buy "+this.maxBuy+" regions";
				if ( this.maxBuy>0 ) msg += " within "+this.timeCountBuy+" days.";
				else msg += ".";
				send(player, msg);
				return false;
			}
		}
		if (!checkDistance(player, region, distanceBuy) && !hasPermission(player, "rbuy.distance-buy")){
			send(player, "rbuy - You are too far from the region to buy it, you must be within "+this.distanceBuy+" blocks of it.");
			return false;
		}
		if (maxRegions > 0){
			if (getNumberOfRegions(player) >= maxRegions && !hasPermission(player, "rbuy.max-regions")){
				send(player, "rbuy - You can not buy more regions (max. "+ maxRegions +").");
				return false;
			}
		}
		// Further check world and container-region restrictions.
		final Map<String, RegionFilterSettings> worldMap = filterSettings.get(world.getName().toLowerCase());
		List<String> restrictions = null;
		if (worldMap != null) {
			//  Check the global region first (whole world).
			int presetMaxBuy = maxBuy;
			if (worldMap.containsKey("__global__")) {
				restrictions = new LinkedList<String>();
				restrictions.add("__global__");
				RegionFilterSettings rfs = worldMap.get("__global__");
				if (rfs.maxBuy != null) {
					presetMaxBuy = rfs.maxBuy;
				}
			}
			if (worldMap.size() > 1 || restrictions == null) {
				// Safety check to spare getting all regions for world-only cases.
				for (final ProtectedRegion otherRegion : man.getRegions().values()){
					if (region != otherRegion && isFullyInside(region, otherRegion)) {
						// Candidate for restriction settings.
						final String otherLcRid = otherRegion.getId().toLowerCase();
						if (worldMap.containsKey(otherLcRid)) {
							if (restrictions == null) {
								restrictions = new LinkedList<String>();
							}
							restrictions.add(otherLcRid);
						}
					}
				}
			}
			if (restrictions != null) {
				// Check restrictions.
				for (final String resKey : restrictions) {
					Integer nPresent = edgeData.nRestriction != null ? edgeData.nRestriction.get(resKey) : null;
					if (nPresent == null) {
						nPresent = 0;
					}
					final RegionFilterSettings rfs = worldMap.get(resKey);
					if ((rfs.maxBuy == null ? presetMaxBuy : rfs.maxBuy) <= nPresent) {
						// Failed.
						send(player, "rbuy - You can not buy more regions within " + (resKey.equals("__global__") ? "this world" : "'" + resKey + "'")+ " (max. "+ rfs.maxBuy +").");
						return false;
					}
				}
			}
		}
		
		if (ecoMixin.getEconomyInterface().transfer(player, benefits, offer.amount, offer.currency)){
			setExclusiveOwner(playerName, world,  region);
			removeOffer(offer);
			Transaction ta = new Transaction();
			ta.timestamp = ts;
			ta.worldName = world.getName();
			ta.regionName = regionName;
			ta.sellerName = benefits;
			ta.buyerName = playerName;
			ta.amount = offer.amount;
			ta.currency = getCurrency(offer.currency);
			ta.area = area;
			ta.volume = volume;
			ta.restrictions = restrictions;
			this.transactions.add(ta);
			info.transactions.add(ta);
			// TODO: policy for the seller ?
			changed = true;
			// TODO: simple message like "(region ... bought.)" in case the economy plugin messages the sender as well.
			send(player, "rbuy - You bought the region "+regionName+" from "+benefits+" for "+offer.amount+" "+offer.currency);
		} else{
			send(player, "rbuy - the transaction failed.");
			if ( info.isEmpty() ){
				this.infos.remove(playerName.toLowerCase());
				changed = true;
			}
			return false;
		}
		return true;
	}

	public int getNumberOfRegions(Player player) {
		int n = 0;
		WorldGuardPlugin wg = getWorldGuard();
		LocalPlayer lp = wg.wrapPlayer(player); 
		for (World world : getServer().getWorlds()){
			RegionManager man = wg.getRegionManager(world);
			n += man.getRegionCountOfPlayer(lp);
		}
		return n;
	}

	/**
	 * Saves regions for the world.
	 * @param playerName
	 * @param world
	 * @param region
	 */
	public static void setExclusiveOwner(String playerName, World world, ProtectedRegion region) {
		region.setMembers(new DefaultDomain());
		DefaultDomain dom = new DefaultDomain();
		dom.addPlayer(playerName);
		region.setOwners(dom);
		saveRegions(world, Bukkit.getServer().getPlayerExact(playerName), "set excl. owner for: "+region.getId());
	}
	
	public static boolean saveRegions(World world, CommandSender notify, String desc){
		try {
			getWorldGuard().getRegionManager(world).save();
			return true;
		} catch (Throwable t) {
			String extra;
			if ( desc != null) extra = " ("+desc+")";
			else extra = "";
			String msg = "[rbuy] WorldGuard failed to save regions"+extra+", changes might get lost for world: "+world.getName();
			Bukkit.getServer().getLogger().severe(msg);
			t.printStackTrace();
			if ( notify != null){
				if (notify instanceof Player) notify.sendMessage(ChatColor.RED+"[rbuy] Failed to save changes, please contact an administrator !"); 
				else notify.sendMessage(msg);
			}
			return false;
		}
	}

	/**
	 * Check if player is within distance of region (cuboid!)
	 * This does NOT check if the worlds match.
	 * @param player
	 * @param region
	 * @param distance
	 * @return
	 */
	public static  boolean checkDistance(Player player, ProtectedRegion region, int distance) {
		// TODO: cleanup (WorldGuard does the min/max thing right, no?)!
		Location loc = player.getLocation();
		int x = loc.getBlockX();
		int y = loc.getBlockY();
		int z = loc.getBlockZ();
		BlockVector min = region.getMinimumPoint();
		BlockVector max = region.getMaximumPoint();
		int minX = min.getBlockX();
		int maxX = max.getBlockX();
		if ( maxX<minX ){
			int s = minX;
			minX = maxX;
			maxX = s;
		}
		int minY = min.getBlockY();
		int maxY = max.getBlockY();
		if ( maxY<minY ){
			int s = minY;
			minY = maxY;
			maxY = s;
		}
		int minZ = min.getBlockZ();
		int maxZ = max.getBlockZ();
		if ( maxZ<minZ ){
			int s = minZ;
			minZ = maxZ;
			maxZ = s;
		}
		if ( x < minX - distance) return false;
		if ( x > maxX + distance) return false;
		if ( y < minY - distance) return false;
		if ( y > maxY + distance) return false;
		if ( z < minZ - distance) return false;
		if ( z > maxZ + distance) return false;
		return true;
	}

	public static long getArea(ProtectedRegion region) {
		BlockVector min = region.getMinimumPoint();
		BlockVector max = region.getMaximumPoint();
		return Math.abs(max.getBlockX()-min.getBlockX()+1)*Math.abs(max.getBlockZ()-min.getBlockZ()+1);
	}
	
	public static ArrayList<String> sortedStrings(Collection<String> ref){
		ArrayList<String> out = new ArrayList<String>(ref.size());
		out.addAll(ref);
		Collections.sort(out);
		return out;
	}
	
	public static void send(CommandSender sender, String message){
		if ( sender instanceof Player) sender.sendMessage(message);
		else sender.sendMessage(ChatColor.stripColor(message));
	}
	
	public boolean hasPermission( CommandSender sender , String perm){
		if (this.ignorePermissions.contains(perm)) return true;
		if ( !(sender instanceof Player)){
			// TODO: document this (!)
			if ( sender.isOp()) return true;
			else return false;
		} else if (opPermissions){
			if (sender.isOp()) return true;
		}
		//Player player = (Player) sender;
		
		if (this.useWgPerms){
			try{
				// Check permission by WorldGuard.
				// NOTE: WorldGuard might have set oppermissions to true in its configuration,
				//       so that ops have any permission, which could override other settings for rbuy.
				// TODO: Hook into WEPIF, since WorldGuard/WorldEdit have it on anyway.
				WorldGuardPlugin wg = getWorldGuard();
				if ( wg != null){
					if ( wg.hasPermission(sender, perm)) return true;
					if ( wg.hasPermission(sender, "rbuy.all")) return true;

				} // else: PROBLEM.
			} catch (Throwable t){
				// TODO: maybe log.
			}
		}
		if ( this.useBukkitPerms){
			if (sender.hasPermission(perm)) return true;
			if ( sender.hasPermission("rbuy.all")) return true;
		}

		return false;
	}

	/**
	 * Player right clicked on a sign, potentially buying a region.
	 * If the sign does not specify a price or the price is wrong, it will warn and replace the price with the regions price.
	 * @param player
	 * @param lines
	 * @return If the sign contains /rbuy syntax (even if wrong).
	 */
	public boolean processSignInteract(Player player, Sign sign) {
		// TODO: change signature
		String[] lines = sign.getLines();
		if ( lines == null ) return false;
		String firstLine = lines[0].trim().toLowerCase();
		if (!magicWordsBuy.contains(firstLine)) return false;
		if ( !active){
			send(player, "rbuy - All functionality is disabled temporarily.");
			return true;
		}
		String secondLine = lines[1].trim().toLowerCase();
		Offer offer = getOffer(secondLine, player.getWorld().getName());
		ProtectedRegion region = getRegion(offer);
		if (region == null || (offer != null && !isExclusiveOwner(offer.benefits, region))){
			sign.setLine(0, ChatColor.YELLOW+"INVALID!");
			sign.update();
			send(player, "rbuy - The offer is invalid.");
			if (offer != null) removeOffer(offer);
			return true;
		}
		else if ( offer == null ){
			sign.setLine(0, ChatColor.YELLOW+"INVALID!");
			sign.setLine(2, ChatColor.GRAY+"(probably sold)");
			sign.setLine(3, "");
			sign.update();
			send(player, "rbuy - The region '"+lines[1]+"' is not for sale in this world.");
			return true; 
		} else if (!player.getWorld().getName().equalsIgnoreCase(offer.worldName)){
			send(player, "rbuy - Buying regions cross-world is not supported (anymore/yet).");
			return true;
		}
		boolean isOwner = player.getName().equalsIgnoreCase(offer.benefits);
		boolean setFromOffer = false;
		String thirdLine = lines[2].trim();
		String fourthLine = lines[3].trim();
		try{
			double amount = Double.parseDouble(thirdLine);
			if (Math.abs(amount - offer.amount) > 0.001) {
				setFromOffer = true;
			}
		} catch (NumberFormatException e){
			setFromOffer = true;
		}
		if ( !fourthLine.equals(offer.currency)){
			// maybe too rigid, but more safe.
			setFromOffer = true;
		}
		if (setFromOffer){
			String amount = getSmallestString(offer.amount);
			if ( amount.length() > 15 ){
				send(player, "rbuy - The price does not fit on the sign, type for buying:");
				send(player, ChatColor.GREEN+"      /rbuy "+offer.regionName);
				sign.setLine(2, "?");
				sign.update();
				return true;
			}
			sign.setLine(2, amount);
			String currency = getCurrency(offer.currency);
			if ( currency.length() >15 ){
				send(player, "rbuy - The currency does not fit on the sign, type for buying:");
				send(player, ChatColor.GREEN+"      /rbuy "+offer.regionName);
				sign.setLine(3, "?");
				sign.update();
				return true;
			}
			sign.setLine(3, currency);
			send(player, "rbuy - Updated the price, right-click to buy or type '/rbuy " + region.getId() + "'.");
			sign.update();
			return true;
		} else if (isOwner){
			send(player, "rbuy - The offer is valid.");
		} else{
			// process buy: delegates to the command-processing method.
			if (!hasPermission(player, "rbuy.buy")){
				player.sendMessage(ChatColor.DARK_RED + "You don't have permission to buy.");
				return true;
			}
			if (processBuy(player, secondLine)){
				sign.setLine(0, ChatColor.YELLOW+"SOLD!");
				sign.setLine(2, "to");
				sign.setLine(3, (player.getName().length()>15)?(player.getName().substring(0,15)):player.getName());
				sign.update();
				if (changed) saveData();
			}
			// TODO: pop/change sign ?
		}
		return true;
	}
	
	/**
	 * Convenience method.
	 * @param offer
	 * @return
	 */
	public static ProtectedRegion getRegion(Offer offer) {
		if (offer == null) return null;
		World world =Bukkit.getWorld(offer.worldName);
		if (world == null) return null;
		return getWorldGuard().getRegionManager(world).getRegion(offer.regionName);
	}

	public static String getSmallestString(double amount){
		String out = new DecimalFormat("#.###").format(amount);
		if (out.indexOf('.') != -1) {
			while (out.endsWith("0")){ // ...
				out = out.substring(0, out.length() - 1);
			}
			if (out.endsWith(".")) {
				out = out.substring(0, out.length() - 1);
			}
		}
		return out;
	}
	
	public String getFirstMagicWordBuy(){
		for (String mw : magicWordsBuy){
			return mw; // !
		}
		return null;
	}

	/**
	 * Check a changed sign for syntax and return standard lines for selling a region.
	 * @param player
	 * @param lines
	 * @return If event should be cancelled and sign popped out of the ground.
	 */
	boolean processSignChange( SignChangeEvent event){
		String[] lines = event.getLines();
		if ( lines == null ) return false;
		String firstLine = lines[0].trim().toLowerCase();
		boolean setLines = false;
		if ( magicWordsSell.contains(firstLine)){
			lines[0] = getFirstMagicWordBuy();
			setLines = true; // at least set first line to '/rbuy'
		} else if (magicWordsBuy.contains(firstLine)){
			/// ok
		} else if (firstLine.equals("/rinfo")) {
			// TODO: Magic words list.
			return processInfoSign(event, lines);
		} else {
			return false;
		}
		Player player = event.getPlayer();
		if ( !active){
			send(player, "rbuy - All functionality is disabled temporarily.");
			return true;
		}
		if ( !hasPermission(player, "rbuy.sell") ){
			send(player, "rbuy - You don't have permission to sell regions.");
			return true;
		}
		String secondLine = lines[1].trim().toLowerCase();
		Location loc = event.getBlock().getLocation();
		if (secondLine.equals("") || secondLine.endsWith("*")){
			// Attempt to find the region associated with it.
			List<ProtectedRegion> regions = getSellableRegions(player, loc);
			if (secondLine.endsWith("*")){
				secondLine = secondLine.substring(0, secondLine.length()-1);
				List<ProtectedRegion> rem = new LinkedList<ProtectedRegion>();
				for (ProtectedRegion r : regions){
					if (!r.getId().toLowerCase().startsWith(secondLine)) rem.add(r);
				}
				regions.removeAll(rem);
			}
			if ( regions.size() == 0 ){
				send(player, "rbuy - There is no sellable region at the given position.");
				return true;
			} 
			else if ( regions.size() == 1 ){
				secondLine = regions.get(0).getId();
				if ( secondLine.length()>15){
					send(player, "rbuy - The region name is too long to fit on a sign: "+secondLine);
					return true;
				}
				setLines = true;
				lines[1] = secondLine;
				secondLine = secondLine.toLowerCase(); // hmm
			} 
			else{
				String names = "";
				// TODO: (maybe) feature: disregard regions that are already for sale ?
				for ( ProtectedRegion r : regions){
					names += " "+r.getId();
				}
				send(player, "rbuy - There is more than one sellable region at the given position:"+names);
				return true;
			}
		} else if (!signsOffground){
			boolean match = false;
			for ( ProtectedRegion region : getSellableRegions(player, loc)){
				if ( region.getId().equalsIgnoreCase(secondLine)){
					match = true;
					break;
				}
			}
			if ( !match ){
				if ( !hasPermission(player, "rbuy.offground-signs")){
					send(player, "rbuy - Placing signs off region is disabled.");
					return true;
				}
			}
		}
		String thirdLine = lines[2].trim();
		String fourthLine = lines[3].trim();
		Double amount = null; // Must be specified if no offer exists.
		String currency = null; // Must match existing offer, if no price is specified. (?)
		try{
			amount = Double.parseDouble(thirdLine);
		} catch (NumberFormatException e){
			
		}
		if ( !fourthLine.equals("")) currency = fourthLine;
		if ((amount == null) && (currency!=null)){
			send(player, "rbuy - Leave currency blank or specify a price if you want to display an existing offers price.");
			return true;
		}
		String wn = player.getWorld().getName();
		Offer offer = getOffer(secondLine, wn);
		if ( (offer != null) && (!player.getName().equalsIgnoreCase(offer.benefits)) ){
			removeOffer(offer); // legitimate, for the player can sell the region.
			offer = null;
		}
		if ( amount != null ){
			// Create new offer ot override existing offer.
			String[] args;
			if ( currency == null) args = new String[]{secondLine, thirdLine};
			else args = new String[]{secondLine, thirdLine, fourthLine};
			removeOffer(offer);
			offer = null;
			processCommand(player, null, "rsell", args);
			offer = getOffer(secondLine, wn);
			if ( offer == null ) return true;
			setLines = true;
			lines[2] = getSmallestString(offer.amount);
			lines[3] = getCurrency(offer.currency);
		} else{
			// (amount == null , currency == null)
			// Attempt to fill in from offer.
			if ( offer == null ){
				send(player, "rbuy - Specify a price, there is no offer for: "+lines[1].trim());
				return true;
			}
			lines[2] = getSmallestString(offer.amount);
			lines[3] = getCurrency(offer.currency);
			setLines = true;
		}
		if ( setLines){
			boolean tooLong = false;
			for ( int i=0; i<4; i++){
				if ( lines[i].length()>15){
					lines[i] = "?";
					tooLong = true;
				}
				event.setLine(i, lines[i]);
			}
			if ( tooLong ){
				send(player , "rbuy - The buying information does not fit on the sign, the buyer has to use the command '/rbuy "+offer.regionName+"'.");
				return false;
			}
		}
		return false;
	}
	
	private boolean processInfoSign(SignChangeEvent event, String[] lines) {
		final Block block = event.getBlock();
		final Player player = event.getPlayer();
		final Collection<Offer> offers = getOffers(block);
		String secondLine = lines[1] == null ? "" : lines[1].trim().toLowerCase();
		boolean prefix = secondLine.endsWith("*") || secondLine.isEmpty();
		final Iterator<Offer> it = offers.iterator();
		final String worldName = block.getWorld().getName();
		Offer lastMatch = null;
		while (it.hasNext()) {
			final Offer offer = it.next();
			if (!worldName.equalsIgnoreCase(offer.worldName)) {
				it.remove();
				continue;
			}
			if (prefix && offer.regionName.toLowerCase().startsWith(secondLine)) {
				// Ok.
			} else if (secondLine.equalsIgnoreCase(offer.regionName)) {
				// ok.
			} else {
				it.remove();
				continue;
			}
			// Set.
			lastMatch = offer;
		}
		if (offers.isEmpty()) {
			player.sendMessage(ChatColor.RED + "No offers at this position.");
			return true;
		} else if (offers.size() > 1)  {
			String of = "";
			for (Offer offer : offers) {
				of += " " + offer.regionName;
			}
			player.sendMessage(ChatColor.RED + "Multiple offers: " + of);
			return true;
		} else {
			// Fill in sign.
			lines[0] = "/rbuy";
			secondLine = lastMatch.regionName;
			if (secondLine.length() > 15) {
				secondLine = secondLine.substring(0, 14) + "*";
			}
			lines[1] = secondLine;
			lines[2] = getSmallestString(lastMatch.amount);
			if (lines[2].length() > 15) {
				player.sendMessage(ChatColor.RED + "Amount does not fit on the sign: " + lines[2]);
				return true;
			}
			lines[3] = getCurrency(lastMatch.currency);
			if (lines[3].length() > 15) {
				lines[3] = lines[3].substring(0, 15);
			}
			for (int i = 0; i < 4 ; i++) {
				event.setLine(i, lines[i]);
			}
			return false;
		}
	}
	
	public Collection<Offer> getOffers(Block block) {
		// Could sue a stored Location instance.
		return getOffers(block.getLocation());
	}

	public Collection<Offer> getOffers(final Location loc) {
		final List<Offer> offers = new LinkedList<Offer>();
		final World world = loc.getWorld();
		final String worldName = world.getName();
		for (final ProtectedRegion region : Utils.getWorldGuard().getRegionManager(world).getApplicableRegions(loc)) {
			final Offer offer = getOffer(region.getId(), worldName);
			if (offer != null) {
				offers.add(offer);
			}
		}
		return offers;
	}

	/**
	 * Ensure currency is set: return currency (no change) or default currency if currency is null.
	 * @param currency
	 * @return
	 */
	public String getCurrency(String currency){
		if ( currency == null ) {
			currency = ecoMixin.getEconomyInterface().getDefaultCurrency();
			if ( currency == null ) currency = "";
		}
		return currency;
	}
	
	/**
	 * Get all regions that contain the location and the player may sell.
	 * @param player
	 * @param loc
	 * @return
	 */
	public List<ProtectedRegion> getSellableRegions( Player player, Location loc){
		boolean sellUnowned = hasPermission(player, "rbuy.sell-unowned");
		World world = loc.getWorld();
		Set<String> rids = unsellable.get(world.getName().toLowerCase());
		ApplicableRegionSet set = getWorldGuard().getRegionManager(world).getApplicableRegions(loc);
		List<ProtectedRegion> candidates = new LinkedList<ProtectedRegion>();
		String playerName = player.getName();
		for (ProtectedRegion region : set){
			if (rids != null && rids.contains(region.getId().toLowerCase())) continue;
			if ( sellUnowned && !region.hasMembersOrOwners()){
				candidates.add(region);
			} else if (isExclusiveOwner(playerName, region)){
				candidates.add(region);
			}
		}
		return candidates;
	}
	
	/**
	 * Just remove the sign and drop it "naturally".
	 * @param block
	 */
	public static void removeSign(Block block) {
		Material mat = block.getType();
		if ( (mat==Material.SIGN) || (mat==Material.SIGN_POST)){
			block.setType(Material.AIR);
			block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(323,1));
			block.getState().update();
		}
	}
}
