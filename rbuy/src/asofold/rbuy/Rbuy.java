package asofold.rbuy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
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
public class Rbuy extends JavaPlugin{
	
	// Classes: 
	
	/**
	 * Delegates commands.
	 * @author mc_dev
	 *
	 */
	class RbuyCommand implements CommandExecutor{
		Rbuy plugin;
		public RbuyCommand(Rbuy plugin){
			this.plugin = plugin;
		}
		@Override
		public boolean onCommand(CommandSender sender, Command command,
				String label, String[] args) {
			return this.plugin.processCommand(sender, command, label, args);
		}
		
	}
	
	/**
	 * Represents an offer.
	 * @author mc_dev
	 *
	 */
	class Offer{
		/**
		 * Name of the player who gets the money.
		 */
		String benefits = null;
		String regionName = null;
		String worldName = null;
		double amount = 0;
		String currency = null;
		
		public boolean fromConfig(Configuration config, String prefix){
			regionName = config.getString(prefix+"regionName", null);
			if ( regionName == null ) return false;
			benefits = config.getString(prefix+"benefits", null);
			worldName = config.getString(prefix+"worldName", null);
			currency = config.getString(prefix+"currency", null);
			amount = config.getDouble(prefix+"amount", -1);
			if ( amount < 0) return false;
			return true;
		}
		
		public void toConfig( Configuration config, String prefix){
			if (regionName == null) return; // set nothing.
			config.setProperty(prefix+"regionName",	 regionName);
			if ( benefits != null){
				config.setProperty(prefix+"benefits", benefits);
			}
			config.getString(prefix+"regionName");
			if ( worldName != null){
				config.setProperty(prefix+"worldName", worldName);
			}
			if ( currency != null){
				config.setProperty(prefix+"currency", currency);
			}
			config.setProperty(prefix+"amount", amount);
			
		}
	}
	
	class Transaction{
		String regionName = null;
		String buyerName = null;
		String sellerName = null;
		double amount = 0;
		String currency = null;
		long timestamp = 0;
		
		long area = 0;
		public boolean fromConfig(Configuration config, String prefix){
			buyerName = config.getString(prefix+"buyerName", null);
			sellerName = config.getString(prefix+"sellerName", null);
			regionName = config.getString(prefix+"regionName", null);
			amount = config.getDouble(prefix+"amount", 0);
			currency = config.getString(prefix+"currency", null);
			timestamp = getLong(config, prefix+"timestamp", 0L);
			// TODO: maybe this will change some time:
			if ( (buyerName == null) ) return false;
			return true;
		}
		
		public void toConfig( Configuration config, String prefix){
			if ( buyerName != null){
				config.setProperty(prefix+"buyerName", buyerName);
			}
			if ( sellerName != null){
				config.setProperty(prefix+"sellerName", sellerName);
			}
			if ( regionName != null){
				config.setProperty(prefix+"regionName", regionName);
			}
			if ( currency != null){
				config.setProperty(prefix+"currency", currency);
			}
			config.setProperty(prefix+"amount", amount);
			config.setProperty(prefix+"timestamp", timestamp);
		}
	}
	
	/**
	 * Info about player, basically for limiting the buying and selling.
	 * @author mc_dev
	 *
	 */
	class PlayerInfo{
		String playerName = null;
		Collection<Transaction> transactions = new LinkedList<Transaction>();
		Collection<Offer> offers = new LinkedList<Offer>();
		public boolean isEmpty() {
			return (transactions.isEmpty()&&offers.isEmpty());
		}
	}

	
	
	
	// Configuration content: 
	Configuration currentConfig = null;
	
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
	int defaultTimeCountArea = 10;
	int timeCountArea = defaultTimeCountArea;
	
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
	
	/**
	 * Strict boundary
	 */
	int defaultMaxMapHeight = 127;
	int maxMapHeight = defaultMaxMapHeight;
	
	/**
	 * Non-op users can use the rbuy command (overrides permissions).
	 */
	boolean defaultUsersBuy = true;
	boolean usersBuy = defaultUsersBuy;
	
	/**
	 * Non-op users can use the rsell command (overrides permissions).
	 */
	boolean defaultUsersSell = true;
	boolean usersSell = defaultUsersSell;
	
	/**
	 * Permissions to ignore: allow by default.
	 */
	String[] defaultIgnorePermissions = new String[]{
		"rbuy.buy", "rbuy.sell", "rbuy.info", "rbuy.list",
		"rbuy.show-own"
	};
	Set<String> ignorePermissions = new HashSet<String>();
	
	// Others:
	
	String name = "Rbuy";
	String version = "?";
	
	String[] cmds = new String[]{
			"rbuy", "rsell", "rlist", "rinfo", "rhelp", "rreload", "renable", "rdisable"
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
	 * Offers by region name.
	 */
	Map<String, Offer> offers = new HashMap<String, Offer>();
	
	
	/**
	 * Past transactions.
	 */
	List<Transaction> transactions = new LinkedList<Transaction>();
	
	/**
	 * Player infos.
	 */
	Map<String, PlayerInfo> infos = new HashMap<String, PlayerInfo>();
	
	

	@Override
	public void onDisable() {
		this.active = false;
		this.saveData();
		System.out.println(this.getPluginDescr()+" is disabled.");
	}
	
	

	public String getPluginDescr(){
		return this.name +"("+this.version+")";
	}
	
	/**
	 * Constructor, in case someone is looking for it.
	 */
	public Rbuy(){
		super();
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdf = getDescription();
		this.version = pdf.getVersion();
		this.name = pdf.getName();
		
//		try{
			this.reloadConfig();
//		} catch ( Throwable t ){
//			t.printStackTrace();
//			getServer().getLogger().severe("rbuy - Failed to load configuration (set to inactive).");
//			this.setActive(false);
//		}
//		try{
			this.loadData();
//		} catch ( Throwable t ){
//			t.printStackTrace();
//			getServer().getLogger().severe("rbuy - Failed to load data (set to inactive).");
//			this.setActive(false);
//		}
		
		CommandExecutor exe = new RbuyCommand(this);
		for (String n : this.cmds ){
			PluginCommand cmd = this.getCommand(n);
			cmd.setExecutor(exe);
		}
		
		// TODO: RuntimeConfig (data)
		
		System.out.println(this.getPluginDescr()+" is enabled (active="+this.active+").");
	}
	
	/**
	 * Enable/disable during runtime (TODO)
	 * @param b
	 */
	public void setActive(boolean b) {
		this.active = b;
		// TODO: save config ?
	}



	public Configuration getCurrentConfig(){
		if ( this.currentConfig == null){
			File file = new File(this.getDataFolder(), "rbuy.yml");
			if ( file.exists() ){
				Configuration config = new Configuration(file);
				config.load();
				this.currentConfig = config;
			} else{
				this.setDefaultConfig();
			}
			this.applyConfig();
		}
		return this.currentConfig;
	}
	
	public void reloadConfig(){
		this.currentConfig = null;
		this.getCurrentConfig();
		this.applyConfig();
	}

	void applyConfig() {
		// apply this.currentConfig
		Configuration config = this.currentConfig;
		this.active = config.getBoolean("active", defaultActive);
		this.usersBuy = config.getBoolean("users-buy", defaultUsersBuy);
		this.usersSell = config.getBoolean("users-sell", defaultUsersSell);
		this.showOwn = config.getBoolean("show-own", defaultShowOwn);
		this.showAll = config.getBoolean("show-all", defaultShowAll);
		this.minMapHeight = config.getInt("min-map-height", defaultMinMapHeight);
		this.maxMapHeight = config.getInt("max-map-height", defaultMaxMapHeight);
		this.maxBuy = config.getInt("max-buy",  defaultMaxBuy);
		this.timeCountBuy= config.getInt("time-count-buy", defaultTimeCountBuy);
		this.maxArea = getLong(config, "max-area", defaultMaxArea);
		this.timeCountArea = config.getInt("time-count-area", defaultTimeCountArea);
		this.maxOffers = config.getInt("max-offers", defaultMaxOffers);
		this.timeForgetTransaction = config.getInt("time-forget-transaction", defaultTimeForgetTransaction);
		this.distanceBuy = config.getInt("distance-buy", defaultDistanceBuy);
		this.distanceSell = config.getInt("distance-sell", defaultDistanceSell);
		this.infoRadius = config.getInt("info-radius", defaultInfoRadius);
		this.ignorePermissions.clear();
		this.ignorePermissions.addAll(config.getStringList("ignore-permissions", new LinkedList<String>()));
	}

	private void setDefaultConfig() {
		// create and safe default configuration.
		File file = new File(this.getDataFolder(), "rbuy.yml");
		Configuration config = new Configuration(file);
		config.setProperty("active", defaultActive);
		config.setProperty("users-buy", defaultDistanceBuy);
		config.setProperty("users-sell", defaultDistanceSell);
		config.setProperty("show-own", defaultShowOwn);
		config.setProperty("show-all", defaultShowAll);
		config.setProperty("min-map-height", defaultMinMapHeight);
		config.setProperty("max-map-height", defaultMaxMapHeight);
		config.setProperty("max-buy", defaultMaxBuy);
		config.setProperty("time-count-buy", defaultTimeCountBuy);
		config.setProperty("max-area", defaultMaxArea);
		config.setProperty("time-count-area", defaultTimeCountArea);
		config.setProperty("max-offers", defaultMaxOffers);
		config.setProperty("time-forget-transaction", defaultTimeForgetTransaction);
		config.setProperty("distance-buy", defaultDistanceBuy);
		config.setProperty("distance-sell", defaultDistanceSell);
		config.setProperty("info-radius", defaultInfoRadius);
		LinkedList<String> ign = new LinkedList<String>();
		for ( String p : this.defaultIgnorePermissions){
			ign.add(p);
		}
		config.setProperty("ignore-permissions", ign);
		
		if ( !config.save()){
			getServer().getLogger().severe("Rbuy - failed to save default configuration.");
		}
		this.currentConfig = config;
	}
	
	/**
	 * Get a long value from a Configuration instance.
	 * @param config
	 * @param key
	 * @param preset
	 * @return
	 */
	public Long getLong(Configuration config, String key, Long preset ){
		String candidate = config.getString(key, null);
		if ( candidate == null) return preset;
		if ( !(candidate instanceof String) ) candidate = candidate.toString();
		try{
			return Long.parseLong(candidate);
		} catch (NumberFormatException e){
			return preset;
		}
	}
	
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
	 * Load all data into memory: offers, past transactions.
	 */
	void loadData(){
		this.offers.clear();
		this.transactions.clear();
		this.infos.clear();
		this.changed = false;
		File file = new File( getDataFolder(), "runtime.yml");
		if ( !file.exists() ) return;
		Configuration config = new Configuration( file);
		config.load();
		long tsLoad = System.currentTimeMillis();
		// construct data from config.
		
		// transactions:
		String prefix = "transactions";
		List<String> keys = config.getKeys(prefix);
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
		keys = config.getKeys(prefix);
		if (keys != null ){
			for ( String key : keys){
				Offer offer = new Offer();
				if ( offer.fromConfig(config, prefix+"."+key+".")){
					PlayerInfo info = getPlayerInfo(offer.benefits); // TODO: check for null
					info.offers.add(offer);
					// TODO: check if already in ?
					offers.put(offer.regionName.toLowerCase(), offer);
				} else {
					getServer().getLogger().warning("rbuy - Could not load offer: "+key);
				}
			}
		}
		System.out.println("rbuy - load data: "+offers.size() +" offers, "+transactions.size()+" transactions.");
	}
	
	/**
	 * Save data from memory to file.
	 */
	void saveData(){
		if ( !active ) return; // policy - prevent
		File file = new File( getDataFolder(), "runtime.yml");
		Configuration config = new Configuration( file);
		
		// TODO: add entries from internal data
		int i = 0;
		for ( Offer offer : offers.values()){
			i++;
			String prefix = "offers.n"+i+".";
			offer.toConfig(config, prefix);
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
	
	/**
	 * Process a chat command.
	 * @param sender
	 * @param command
	 * @param label
	 * @param args
	 * @return
	 */
	public boolean processCommand(CommandSender sender, Command command,
			String label, String[] args) {
		int length = args.length;
		
		if ( !active ) {
			if (!label.equalsIgnoreCase("renable") && !label.equalsIgnoreCase("rreload")){
				sender.sendMessage("rbuy - All functionality is disabled temporarily.");
				return false;
			}
		}
		
		if ( label.equalsIgnoreCase("rhelp") || ( (label.equalsIgnoreCase("rbuy")) && (args.length>=1) && (args[0].equalsIgnoreCase("help")) ) ){
			sender.sendMessage("rbuy - No help available, use /rbuy for a short comand listing.");
			return true;
		} else if ((length==0)&&(label.equalsIgnoreCase("rbuy"))){
			sender.sendMessage("rbuy - /rbuy <region> | /rsell <region> <price> [<currency>] ; /rsell <region> ; /rsell | /rlist ; /rlist <player> | /rinfo <region> | /rreload | /renable ; /rdisable | /rhelp");
			return true;
		}
		
		if ( label.equalsIgnoreCase("rsell")){
			// mind replacement strategy (ops may)
			if ( !(sender instanceof Player) ){
				// TODO: could be sold by console ...
				sender.sendMessage("rbuy - Expect a player for using rsell.");
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
					sender.sendMessage("rbuy - You don't have permission.");
					return false;
				}
				boolean res = processOffer(player, args);
				if ( this.changed ) this.saveData();
				return res;
			} else if (length == 0){
				if ( sender instanceof Player){ // cloning ?
					this.sendOwnOffers((Player) sender);
					return true;
				} else {
					sender.sendMessage("rbuy - Currently only available for players: /rsell");
					return false;
				}
				
			} else{
				player.sendMessage("rbuy - Wrong number of arguments for rsell, expect: /rsell <region> <amount> [<currency>] | /rsell <region>");
				return false;
			}
		} else if ( label.equalsIgnoreCase("rbuy")){
			if ( !hasPermission(sender, "rbuy.buy")){
				sender.sendMessage("rbuy - You don't have permission.");
				return false;
			}
			if ( !(sender instanceof Player) ){
				sender.sendMessage("rbuy - Expect a player for using rbuy.");
				return false;
			}
			Player player = (Player) sender;
			if (length == 1){
				boolean res = this.processBuy( player, args[0]); // could use the result...
				if ( this.changed ) this.saveData();
				return res; 
			} else{
				sender.sendMessage("rbuy - Wrong number of arguments for rbuy, expect /rbuy <region>.");
				return false;
			}
		} else if (label.equalsIgnoreCase("rinfo")){
			if ( (length ==1)  ){
				// most detailed info
				showInfo(sender, args[0]);
			} else if ((sender instanceof Player)){
				// medium/less detailed info
				showNearbyInfo((Player) sender);
			} else{
				// most detailed info
				showAllInfos(sender);
			}
			return true;
		} else if (label.equalsIgnoreCase("rlist")){
			// just show names
			showAllOffers(sender);
			return true;
		} else if (label.equalsIgnoreCase("rreload")){
			if ( !hasPermission(sender, "rbuy.reload")){
				sender.sendMessage("rbuy - You don't have permission.");
				return false;
			}
			this.saveData();
			this.reloadConfig();
			this.loadData();
			sender.sendMessage("rbuy - Reloaded configuration.");
			return true;		
		}  else if ( label.equalsIgnoreCase("renable")){
			if ( !hasPermission(sender, "rbuy.enable")){
				sender.sendMessage("rbuy - You don't have permission.");
				return false;
			}
			setActive(true);
			sender.sendMessage("rbuy - Active (possibly till next reload, only).");
			return true;
		} else if ( label.equalsIgnoreCase("rdisable")){
			if ( !hasPermission(sender, "rbuy.disable")){
				sender.sendMessage("rbuy - You don't have permission.");
				return false;
			}
			setActive(false);
			sender.sendMessage("rbuy - Active (possibly till next reload, only).");
			return true;
		}
		
		sender.sendMessage("rbuy - unknown options/command: "+label);
		return false;
	}
	
	/**
	 * Show region offers that are nearby the players current location.
	 * @param player
	 */
	private void showNearbyInfo(Player player) {
		World world = player.getWorld();
		String body = "";
		
		// TODO: WHY CLASSNOTFOUND ?
		// minimal version: Show those the player is standing on.
		// TODO: check 8 further away points
//		Location loc = player.getLocation().clone();
//		int x = loc.getBlockX();
//		int y = loc.getBlockY(); // TODO: what exactly ?
//		int z = loc.getBlockZ();
//		ProtectedCuboidRegion bound = new ProtectedCuboidRegion("__rbuy_temp__", new BlockVector(x-infoRadius,Math.max(y-infoRadius,minMapHeight),z-infoRadius),
//				new BlockVector(x+infoRadius,Math.min(y+infoRadius,maxMapHeight),z+infoRadius));
//		ApplicableRegionSet set = getWorldGuard().getRegionManager(world).getApplicableRegions(bound);
//		if ( set != null){
//			for (ProtectedRegion reg : set){
//				String rgn = reg.getId();
//				String rn = rgn.toLowerCase();
//				Offer offer = offers.get(rn);
//				if ( offer != null ){
//					body+=" "+rgn+"["+offer.amount+" "+offer.currency+"]";
//				}
//			}
//		}
		String playerName = player.getName();
		RegionManager manager = getWorldGuard().getRegionManager(world);
		if ( manager != null){
			for ( Offer offer:offers.values()){ // HILARIOUS
				if (!showOwn && (playerName.equalsIgnoreCase(offer.benefits)) ){
					
				} else{
					ProtectedRegion region = manager.getRegion(offer.regionName);
					if ( region != null){
						if ( checkDistance(player, region, infoRadius)){
							body+=" "+ChatColor.YELLOW+offer.regionName+ChatColor.WHITE+"["+offer.amount+" "+offer.currency+"]";
						}
					}
				}
			}
		}

		if ( body.length() == 0){
			send( player, "rbuy - No nearby offers found.");
		} else{
			send( player, "rbuy - Nearby offers:"+body);
		}
	}

	public void showAllInfos(CommandSender sender) {
		String own = "";
		boolean restrict = !showOwn &&(sender instanceof Player);
		if (restrict) own = sender.getName();
		sender.sendMessage("rbuy - all infos:");
		for (String  rn : sortedStrings(offers.keySet())){
			Offer offer = offers.get(rn);
			if ( !restrict || !own.equalsIgnoreCase(rn)){
				showInfo(sender, offer.regionName);
			}
		}
	}

	/**
	 * Show detailed info about a specific region-offer.
	 * @param sender
	 * @param rgn
	 */
	public  void showInfo(CommandSender sender, String rgn) {
		String rn = rgn.trim().toLowerCase();
		if (this.offers.containsKey(rn)){
			sender.sendMessage("rbuy - info: "+getSingleInfo(rn));
		}else{
			sender.sendMessage("rbuy - No offer for: "+rgn);
		}
	}

	public String getSingleInfo(String rgn) {
		String rn = rgn.trim().toLowerCase();
		Offer offer = offers.get(rn);
		if ( offer == null) return "";
		return offer.regionName+" ["+offer.worldName+" | "+offer.amount+" "+offer.currency+" from "+offer.benefits+"]";
	}

	/**
	 * 
	 * @param sender
	 */
	public void showAllOffers(CommandSender sender) {
		if ( !showAll && (sender instanceof Player) ){
			sender.sendMessage("rbuy - Showing all entries is disabled.");
		}
		StringBuilder builder = new StringBuilder ();
		for (String  rn : sortedStrings(offers.keySet())){
			builder.append(" "+offers.get(rn).regionName);
		}
		sender.sendMessage("rbuy - Offers: "+builder.toString());
	}

	/**
	 * Display own offers.
	 * @param player
	 */
	public void sendOwnOffers(Player player) {
		PlayerInfo info = this.infos.get(player.getName());
		if ( (info == null) || (info.offers.size() == 0)){
			player.sendMessage("rbuy - You are not offering any regions for sale.");
		} else{
			String msg = "rbuy - Your offers: ";
			// TODO: sorted version !
			for ( Offer offer: info.offers ){
				msg += offer.regionName+" ";
			}
			player.sendMessage(msg);
		}
	}

	/**
	 * Call with two or three args (!).
	 * @param player
	 * @param args
	 * @return
	 */
	public boolean processOffer(Player player, String[] args) {
		asofold.admittance.interfaces.EconomyInterface eco = getAdmittanceEconomyInterface();
		String currency = eco.getDefaultCurrency();
		if ( args.length == 3){
			currency = args[2].trim().toLowerCase();
			if (!eco.isAcceptedCurrency(currency)){
				player.sendMessage("The currency is not accepted: "+currency);
				return false;
			}
		} 
		String rgn = args[0].trim();
		double amount = -1;
		try{
			amount = Double.parseDouble(args[1]);
		} catch (NumberFormatException e){
			
		}
		if ( amount < 0 ){
			player.sendMessage("rbuy - invalid amount: "+args[1]);
			return false;
		}
		
		if ( !canSellRegion(player, rgn)){
			player.sendMessage("rbuy - You do not have the exclusive ownership or rights to sell the region: "+rgn);
			return false;
		}
		String playerName = player.getName();
		PlayerInfo info = getPlayerInfo(playerName);
		if ( (this.maxOffers>0) && (info.offers.size()>=this.maxOffers) && !hasPermission(player, "rbuy.max-offers")){
			player.sendMessage("rbuy - You can only place "+maxOffers+" offers at a time.");
			return false;
		}
		World world = player.getWorld();
		ProtectedRegion region = getRegion(world, rgn);
		
		if ( region == null ){
			player.sendMessage("rbuy - The region "+rgn+" does not exist for the world: "+world.getName());
			return false;
		}
		
		if ( distanceSell > 0 ){
			if ( !checkDistance(player, region, distanceSell) && !hasPermission(player, "rbuy.distance-sell") ){
				player.sendMessage("rbuy - You need to be no farther than "+distanceSell+" blocks away from the region you want to offer.");
				return false;
			}
		}
		
		if ( !region.hasMembersOrOwners())setExclusiveOwner(playerName, region); // TODO: subject to policy 
		
		String rn = rgn.toLowerCase();
		Offer offer = this.offers.get(rn);
		if ( offer != null ) removeOffer(offer);
		offer = new Offer();
		offer.benefits = playerName;
		offer.regionName = rgn;
		offer.amount = amount;
		offer.worldName = world.getName();
		offer.currency = currency;
		info.offers.add(offer);
		offers.put(offer.regionName.toLowerCase(), offer);
		player.sendMessage("rbuy - Placed offer for "+amount+" "+currency+" for: "+rgn);
		this.changed = true;
		return true;
	}



	public boolean processCancelOffer(Player player, String rgn) {
		String rn = rgn.trim().toLowerCase();
		Offer offer = offers.get(rn);
		if ( offer == null ){
			player.sendMessage("rbuy - No offer for region: "+rn);
			return true;
		}
		
		if ( !player.getName().equalsIgnoreCase(offer.benefits)){
			World world = player.getWorld();
			ProtectedRegion region = getRegion(world, rn);
			if ( region == null){
				player.sendMessage("rbuy - the region does not exist: "+rgn);
				return false;
			}
			if (!region.isOwner(getWorldGuard().wrapPlayer(player))){
				player.sendMessage("rbuy - You are not owner of the region, neither is this your offer: "+rgn);
				return false;
			}
		}
		// owner of region or offer can remove it
		removeOffer(offer);
		player.sendMessage("rbuy - offer removed: "+rgn);
		changed = true;
		return true;
	}



	/**
	 * Remove an offer from the data.
	 * @param offer
	 */
	public void removeOffer(Offer offer){
		if ( offer == null){
			return ;
		}
		if (this.offers.remove(offer.regionName.toLowerCase())!=null){
			this.changed = true;
		}
		if ( offer.benefits!=null){
			String lc = offer.benefits.toLowerCase();
			PlayerInfo info = this.infos.get(lc);
			if ( info != null){
				info.offers.remove(offer);
				if ( info.isEmpty() ) infos.remove(lc);
				this.changed = true;
			}
		}
		
	}

	/**
	 * Getting the economy-interface.
	 * @return
	 */
	public asofold.admittance.interfaces.EconomyInterface getAdmittanceEconomyInterface(){
		Plugin plugin = getServer().getPluginManager().getPlugin("admittance");
		return ((asofold.admittance.Admittance) plugin).getEconomyInterface();
	}
	
	/**
	 * Check if the player is allowed to sell the region,
	 * i.e. is the only owner, with no members. ALso successful if the region no owners or  members.
	 * This is for checking when buying (seller is potentially offline).
	 * @param playername
	 * @param regionName
	 * @return
	 */
	public boolean canSellRegion(String playername, ProtectedRegion region){
		if ( !region.hasMembersOrOwners() ){
			// policy, keep it in mind (!).
			return true;
		}
		return isExclusiveOwner(playername , region);
	}
	
	/**
	 * Exclusive owner, not member, no others.
	 * @param playername
	 * @param region
	 * @return
	 */
	public boolean isExclusiveOwner(String playername, ProtectedRegion region) {
		DefaultDomain dom = region.getMembers();
		if ( dom.size() != 0) return false;
		dom = region.getOwners();
		if (dom.size() != 1) return false;
		if (playername.equalsIgnoreCase(dom.toPlayersString())) return true;
		return false;
	}



	public boolean canSellRegion( Player player, String rgn){
		World world = player.getWorld();
		ProtectedRegion region = getRegion(world, rgn);
		if ( region == null ) return false;
		if ( !region.hasMembersOrOwners() ){
			if (!player.isOp()){
				return false;
			} else{
				return hasPermission(player, "rbuy.sell-unowned");
			}
		}
		return isExclusiveOwner(player.getName(), region);
	}
	
	/**
	 * Get the WorldGuardPlugin (no checks!).
	 * @return
	 */
	public WorldGuardPlugin getWorldGuard(){
		Plugin plg = getServer().getPluginManager().getPlugin("WorldGuard");
		return (WorldGuardPlugin) plg;
	}
	
	public ProtectedRegion getRegion(World world, String regionName){
		return getWorldGuard().getRegionManager(world).getRegion(regionName);
	}
	
	int getNbuy( PlayerInfo info, long ts){
		LinkedList<Transaction> rem = new LinkedList<Transaction>();
		int n = 0;
		long tsExpireTa = ts - timeForgetTransaction*msDay;
		long tsExpireBuy = ts - timeCountBuy*msDay;
		for ( Transaction ta: info.transactions){
			if ( ta.timestamp < tsExpireTa ) rem.add(ta);
			else if ( ta.timestamp >= tsExpireBuy) n++;
		}
		if ( !rem.isEmpty()){
			info.transactions.removeAll(rem);
			changed = true;
		}
		return n;
	}
	
	long getAreaBuy(PlayerInfo info, long ts){
		LinkedList<Transaction> rem = new LinkedList<Transaction>();
		long a = 0;
		long tsExpireTa = ts - timeForgetTransaction*msDay;
		long tsExpireBuy = ts - timeCountArea*msDay;
		for ( Transaction ta: info.transactions){
			if ( ta.timestamp < tsExpireTa ) rem.add(ta);
			else if ( ta.timestamp >= tsExpireBuy) a += ta.area;
		}
		if ( !rem.isEmpty()){
			info.transactions.removeAll(rem);
			changed = true;
		}
		return a;
	}

	public boolean processBuy(Player player, String regionName) {
		long ts = System.currentTimeMillis();
		regionName = regionName.trim();
		World world = player.getWorld();
		
		
		Offer offer = offers.get(regionName.toLowerCase());
		if ( offer == null ){
			player.sendMessage("rbuy - There is no offer for the region: "+regionName);
			return false;
		}
		String playerName = player.getName();
		if (playerName.equalsIgnoreCase(offer.benefits)){
			player.sendMessage("rbuy - Your offer: "+regionName);
			return false;
		}
		
		PlayerInfo info = getPlayerInfo(playerName);
		if ( this.maxBuy > 0){
			if ( (getNbuy(info, ts) >= this.maxBuy) && !hasPermission(player, "rbuy.max-buy")){
				String msg = "rbuy - You can only buy "+this.maxBuy+" regions";
				if ( this.maxBuy>0 ) msg += " within "+this.timeCountBuy+" days.";
				else msg += ".";
				player.sendMessage(msg);
				return false;
			}
		}
		
		String benefits = offer.benefits;
		if ( benefits == null ){
			player.sendMessage("rbuy - Expect the name of the seller to be set, to benefit from the transaction.");
			// TODO: policy / defaultName, anonymous transaction.
			removeOffer(offer);
			changed = true;
			return false;
		}
		ProtectedRegion region = getRegion(world, regionName);
		if ( region == null){
			player.sendMessage("rbuy - The region seems not to exist in your world: "+regionName);
			// keep, in case it is diferent managers for different worlds.
			return false;
		}
		if ( !this.canSellRegion(benefits, region)){
			player.sendMessage("rbuy - The player "+benefits+" is not authorized to sell the region: "+regionName);
			removeOffer(offer);
			changed = true;
			return false;
		}
		
		
		long area =  getArea(region);
		// check versus boundaries of info:
		if (this.maxArea > 0){
			if ( (getAreaBuy(info, ts)+area>this.maxArea) && !hasPermission(player, "rbuy.max-area") ){
				String msg = "rbuy - The area is too big in total to be bought by you";
				if ( this.timeCountArea == 0 ) msg += ".";
				else msg += " within "+ this.timeCountArea+" days.";
				player.sendMessage(msg);
				return false;
			}
		}
		
		if (!this.checkDistance(player, region, distanceBuy) && !hasPermission(player, "rbuy.distance-buy")){
			player.sendMessage("rbuy - You are too far from the region to buy it, you must be within "+this.distanceBuy+" blocks of it.");
			return false;
		}
		
		
		if (this.getAdmittanceEconomyInterface().transfer(player, benefits, offer.amount, offer.currency)){
			setExclusiveOwner(playerName, region);
			removeOffer(offer);
			Transaction ta = new Transaction();
			ta.timestamp = ts;
			ta.regionName = regionName;
			ta.sellerName = benefits;
			ta.buyerName = playerName;
			ta.amount = offer.amount;
			ta.currency = offer.currency;
			ta.area = area;
			this.transactions.add(ta);
			info.transactions.add(ta);
			// TODO: policy for the seller ?
			// getPlayerInfo(benefits).transactions.add(ta);
			changed = true;
			// TODO: simple message like "(region ... bought.)" in case the economy plugin messages the sender as well.
			player.sendMessage("rbuy - You bought the region "+regionName+" from "+benefits+" for "+offer.amount+" "+offer.currency);
		} else{
			player.sendMessage("rbuy - the transaction failed.");
			if ( info.isEmpty() ){
				this.infos.remove(playerName.toLowerCase());
				changed = true;
			}
		}
		// transaction
		
		return true;
	}



	public void setExclusiveOwner(String playerName, ProtectedRegion region) {
		region.setMembers(new DefaultDomain());
		DefaultDomain dom = new DefaultDomain();
		dom.addPlayer(playerName);
		region.setOwners(dom);
	}


	/**
	 * Check if player is within distance of region (cuboid!)
	 * @param player
	 * @param region
	 * @param distance
	 * @return
	 */
	public boolean checkDistance(Player player, ProtectedRegion region, int distance) {
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



	public long getArea(ProtectedRegion region) {
		BlockVector min = region.getMinimumPoint();
		BlockVector max = region.getMaximumPoint();
		return Math.abs(max.getBlockX()-min.getBlockX())*Math.abs(max.getBlockZ()-min.getBlockZ());
	}
	
	public static ArrayList<String> sortedStrings(Collection<String> ref){
		ArrayList<String> out = new ArrayList<String>(ref.size());
		out.addAll(ref);
		Collections.sort(out);
		return out;
	}
	
	public static String removeChatColors(String msg){
		for ( ChatColor cc : ChatColor.values()){
			String k = ""+cc;
			if (msg.indexOf(k)!=-1){
				msg = msg.replaceAll(k, "");
			}
		}
		return msg;
	}
	
	public static void send(CommandSender sender, String message){
		if ( sender instanceof Player) sender.sendMessage(message);
		else sender.sendMessage(removeChatColors(message));
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
		Player player = (Player) sender;
		
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
	
		return false;
	}

}
