package asofold.rbuy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
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
	 * Checking for sign interaction.
	 * @author mc_dev
	 *
	 */
	class RbuyPlayerListener extends PlayerListener{
		Rbuy plugin;
		public RbuyPlayerListener(Rbuy plugin){
			this.plugin = plugin;
		}
		@Override
		public void onPlayerInteract(PlayerInteractEvent event) {
			if ( event.isCancelled() ) return;
			if ( !useSigns) return;
			Action action = event.getAction();
			if ( action == Action.RIGHT_CLICK_BLOCK){
				Block block = event.getClickedBlock();
				Material mat = block.getType();
				if ( (mat == Material.SIGN) || (mat == Material.SIGN_POST) ){
					BlockState state = block.getState();
					if ( state instanceof Sign){
						if (plugin.processSignInteract(event.getPlayer(), (Sign) state)){
							event.setCancelled(true);
							event.setUseInteractedBlock(Result.DENY);
							event.setUseItemInHand(Result.DENY);
						}
						
					}
				}
			}
		}
	}
	
	/**
	 * Checking for placing/changing signs.
	 * @author mc_dev
	 *
	 */
	class RbuyBlockListener extends BlockListener{
		Rbuy plugin;
		public RbuyBlockListener(Rbuy plugin){
			this.plugin = plugin;
		}
		@Override
		public void onSignChange( SignChangeEvent event) {
			if ( event.isCancelled()) return;
			if ( !useSigns) return;
			if (processSignChange(event)){
				event.setCancelled(true);
				// maybe this should be done in a delayed task:
				popSign(event.getBlock());
			}
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
			worldName = config.getString(prefix+"worldName", null);
			if ( worldName == null) return false;
			benefits = config.getString(prefix+"benefits", null);
			currency = config.getString(prefix+"currency", null);
			amount = config.getDouble(prefix+"amount", -1);
			if ( amount < 0) return false;
			return true;
		}
		
		public void toConfig( Configuration config, String prefix){
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
			"rbuy", "rsell", "rlist", "rinfo", "rhelp", "rreload", "renable", "rdisable",
			"rremove"
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
	
	final RbuyPlayerListener playerListener = new RbuyPlayerListener(this);
	final RbuyBlockListener blockListener = new RbuyBlockListener(this);
	

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
		magicWordsBuy.clear();
		magicWordsBuy.add(defaultMagicWordBuy);
		magicWordsSell.clear();
		magicWordsSell.add(defaultMagicWordSell);
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdf = getDescription();
		this.version = pdf.getVersion();
		this.name = pdf.getName();

		// Errors in configuration should lead to deactivation of the plugin (intended).
		this.reloadConfig();
		this.loadData();

		CommandExecutor exe = new RbuyCommand(this);
		for (String n : this.cmds ){
			PluginCommand cmd = this.getCommand(n);
			cmd.setExecutor(exe);
		}
		
		// TODO: RuntimeConfig (data)
		
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Type.PLAYER_INTERACT, playerListener	, Priority.Low, this);
		pm.registerEvent(Type.SIGN_CHANGE, blockListener	, Priority.Low, this);
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

	void applyConfig() {
		// apply this.currentConfig to internals
		Configuration config = this.currentConfig;
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
		this.maxArea = getLong(config, "max-area", defaultMaxArea);
		this.maxBuy = config.getInt("max-buy",  defaultMaxBuy);
		this.maxMapHeight = config.getInt("max-map-height", defaultMaxMapHeight);
		this.maxOffers = config.getInt("max-offers", defaultMaxOffers);
		this.minMapHeight = config.getInt("min-map-height", defaultMinMapHeight);
		this.opPermissions = config.getBoolean("op-permissions", defaultOpPermissions);
		this.showAll = config.getBoolean("show-all", defaultShowAll);
		this.showOwn = config.getBoolean("show-own", defaultShowOwn);
		this.showWorldName = config.getBoolean("show-worldname", defaultShowWorldName);
		this.signsOffground = config.getBoolean("signs-offground", defaultSignsOffground);
		this.timeCountArea = config.getInt("time-count-area", defaultTimeCountArea);
		this.timeCountBuy = config.getInt("time-count-buy", defaultTimeCountBuy);
		this.timeForgetTransaction = config.getInt("time-forget-transaction", defaultTimeForgetTransaction);
		this.useBukkitPerms = config.getBoolean("use-bukkit-perms", defaultUseBukkitPerms);
		this.useSigns = config.getBoolean("use-signs", defaultUseSigns);
		this.useWgPerms = config.getBoolean("use-worldguard-perms", defaultUseWgPerms);
	}

	private void setDefaultConfig() {
		// create and safe default configuration.
		File file = new File(this.getDataFolder(), "rbuy.yml");
		Configuration config = new Configuration(file);
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
		config.setProperty("max-map-height", defaultMaxMapHeight);
		config.setProperty("max-offers", defaultMaxOffers);
		config.setProperty("min-map-height", defaultMinMapHeight);
		config.setProperty("op-permissions", defaultOpPermissions);
		config.setProperty("show-own", defaultShowOwn);
		config.setProperty("show-all", defaultShowAll);
		config.setProperty("show-worldname", defaultShowWorldName);
		config.setProperty("signs-offground", defaultSignsOffground);
		config.setProperty("time-count-area", defaultTimeCountArea);
		config.setProperty("time-count-buy", defaultTimeCountBuy);
		config.setProperty("time-forget-transaction", defaultTimeForgetTransaction);
		config.setProperty("use-bukkit-perms", defaultUseBukkitPerms);
		config.setProperty("use-signs", defaultUseSigns);
		config.setProperty("use-worldguard-perms", defaultUseWgPerms);
		
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
					putOffer(offer);
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
		for ( String wn : this.offers.keySet()){
			Map<String, Offer> rMap = this.offers.get(wn);
			if ( rMap == null) { // IMPOSSIBLE :)
				getServer().getLogger().severe("rbuy - Concurrent modification of offers while saving.");
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
			send(sender, "rbuy - /rbuy <region> | /rsell <region> <price> [<currency>] ; /rsell <region> ; /rsell | /rlist ; /rlist <player> | /rinfo <region> | /rreload | /renable ; /rdisable | /rhelp");
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
				player.sendMessage("rbuy - Wrong number of arguments for rsell, expect: /rsell <region> <amount> [<currency>] | /rsell <region>");
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
			// just show names
			showAllOffers(sender);
			return true;
		} else if (label.equalsIgnoreCase("rreload")){
			if ( !hasPermission(sender, "rbuy.reload")){
				send(sender,"rbuy - You don't have permission.");
				return false;
			}
			this.saveData();
			this.reloadConfig();
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
		}
		
		send(sender, "rbuy - unknown options/command: "+label);
		return false;
	}
	
	/**
	 * Show region offers that are nearby the players current location.
	 * @param player
	 */
	private void showNearbyInfo(Player player) {
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
	public  void showInfo(CommandSender sender, String rgn, String worldName) {
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
		if ( showWorldName ) wn = offer.worldName+" | ";
		return offer.regionName+" ["+wn+offer.amount+" "+offer.currency+" from "+offer.benefits+"]";
	}

	/**
	 * 
	 * @param sender
	 */
	public void showAllOffers(CommandSender sender) {
		if ( !showAll && (sender instanceof Player) && !hasPermission((Player)sender, "rbuy.show-all") ){
			send(sender, "rbuy - Showing all entries is disabled.");
			return;
		}
		String wn = "";
		boolean worldSpecific = this.listWorldSpecific;
		if ( sender instanceof Player ){
			wn = ((Player)sender).getWorld().getName();
		} else worldSpecific = false;
		boolean found = false;
		for ( String worldName : this.offers.keySet()){
			Map<String, Offer> offers = this.offers.get(wn);
			if ( offers != null){
				if ( worldSpecific &&  !wn.equalsIgnoreCase(worldName)) continue;
				StringBuilder builder = new StringBuilder ();
				for (String rn : sortedStrings(offers.keySet())){
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
			player.sendMessage("rbuy - You are not offering any regions for sale.");
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
//		Offer offer = this.offers.get(rn);
//		if ( offer != null ) removeOffer(offer);
		Offer offer = new Offer();
		offer.benefits = playerName;
		offer.regionName = rgn;
		offer.amount = amount;
		offer.worldName = world.getName();
		offer.currency = getCurrency(currency);
		info.offers.add(offer);
		putOffer(offer);
		player.sendMessage("rbuy - Placed offer for "+amount+" "+currency+" for: "+rgn);
		this.changed = true;
		return true;
	}



	public boolean processCancelOffer(Player player, String rgn) {
		String rn = rgn.trim().toLowerCase();
		String wn = player.getWorld().getName();
		Offer offer = getOffer(rgn,wn);
		if ( offer == null ){
			player.sendMessage("rbuy - No offer in world '"+wn+"' for region: "+rn);
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


	/**
	 * Check for ownership issues. 
	 * Currently only exclusive ownership allows for 
	 * @param player
	 * @param rgn
	 * @return
	 */
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
	
	/**
	 * Number of bought regions within time-count buy, till now.
	 * @param info
	 * @param ts time: now.
	 * @return
	 */
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
	
	/**
	 * Get the area of all bought regions within forget-time.
	 * @param info
	 * @param ts time: now.
	 * @return area
	 */
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
	 * This does NOT add the offer to a PlayerInfo.
	 * @param offer
	 * @return
	 */
	public boolean putOffer(Offer offer){
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

	public boolean processBuy(Player player, String regionName) {
		long ts = System.currentTimeMillis();
		regionName = regionName.trim();
		World world = player.getWorld();
		
		
		Offer offer = getOffer(regionName, world.getName());
		if ( offer == null ){
			player.sendMessage("rbuy - In this world there is no offer for: "+regionName);
			return false;
		}
		String playerName = player.getName();
		if (playerName.equalsIgnoreCase(offer.benefits)){
			player.sendMessage("rbuy - Your offer: "+offer.regionName);
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
			player.sendMessage("rbuy - The region seems not to exist in this world: "+regionName);
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
			ta.currency = getCurrency(offer.currency);
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
	 * This does NOT check if the worlds match.
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
		String secondLine = lines[1].trim().toLowerCase();
		Offer offer = getOffer(secondLine, player.getWorld().getName());
		if ( offer == null ){
			player.sendMessage("rbuy - There is no region '"+lines[1]+"' for sale in this world.");
			return true; 
		} else if (!player.getWorld().getName().equalsIgnoreCase(offer.worldName)){
			player.sendMessage("rbuy - Buying regions cross-world is not supported (anymore/yet).");
			return true;
		}
		boolean isOwner = player.getName().equalsIgnoreCase(offer.benefits);
		boolean setFromOffer = false;
		String thirdLine = lines[2].trim();
		String fourthLine = lines[3].trim();
		try{
			double amount = Double.parseDouble(thirdLine);
			if (amount != offer.amount) setFromOffer = true;
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
				player.sendMessage("rbuy - The price does not fit on the sign, type for buying:");
				player.sendMessage(ChatColor.GREEN+"      /rbuy "+offer.regionName);
				sign.setLine(2, "?");
				sign.update();
				return true;
			}
			sign.setLine(2, amount);
			String currency = getCurrency(offer.currency);
			if ( currency.length() >15 ){
				player.sendMessage("rbuy - The currency does not fit on the sign, type for buying:");
				player.sendMessage(ChatColor.GREEN+"      /rbuy "+offer.regionName);
				sign.setLine(3, "?");
				sign.update();
				return true;
			}
			sign.setLine(3, currency);
			player.sendMessage ("rbuy - Updated the price, right-click to buy.");
			sign.update();
			return true;
		} else if (isOwner){
			player.sendMessage("rbuy - The offer is valid.");
		} else{
			// process buy: delegates to the command-processing method.
			processCommand(player, null, "rbuy", new String[]{offer.regionName});
			// TODO: pop/change sign ?
		}
		
		return true;
	}
	
	public static String getSmallestString(double amount){
		String out = Double.toString(amount);
		if (out.endsWith(".0")){ // ...
			out = out.substring(0, out.length()-2);
		}
		return out;
	}
	
	public String getSomeMagicWordBuy(){
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
	public boolean processSignChange( SignChangeEvent event){
		String[] lines = event.getLines();
		if ( lines == null ) return false;
		String firstLine = lines[0].trim().toLowerCase();
		boolean setLines = false;
		if ( magicWordsSell.contains(firstLine)){
			lines[0] = getSomeMagicWordBuy();
			setLines = true; // at least set first line to '/rbuy'
		} else if (magicWordsBuy.contains(firstLine)){
			/// ok
		} else return false;
		Player player = event.getPlayer();
		if ( !hasPermission(player, "rbuy.sell") ){
			player.sendMessage("rbuy - You don't have permission to sell regions.");
			return true;
		}
		String secondLine = lines[1].trim().toLowerCase();
		Location loc = event.getBlock().getLocation();
		if (secondLine.equals("")){
			// Attempt to find the region associated with it.
			List<ProtectedRegion> regions = getSellableRegions(player, loc);
			if ( regions.size() == 0 ){
				player.sendMessage("rbuy - There is no sellable region at the given position.");
				return true;
			} else if ( regions.size() == 1 ){
				secondLine = regions.get(0).getId();
				if ( secondLine.length()>15){
					player.sendMessage("rbuy - The region name is too long to fit on a sign: "+secondLine);
					return true;
				}
				setLines = true;
				lines[1] = secondLine;
				secondLine = secondLine.toLowerCase(); // hmm
			} else{
				String names = "";
				// TODO: (maybe) feature: disregard regions that are already for sale ?
				for ( ProtectedRegion r : regions){
					names += " "+r.getId();
				}
				player.sendMessage("rbuy - There is more than one sellable region at the given position:"+names);
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
					player.sendMessage("rbuy - Placing signs off region is disabled.");
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
			player.sendMessage("rbuy - Leave currency blank or specify a price if you want to display an existing offers price.");
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
				player.sendMessage("rbuy - Specify a price, there is no offer for: "+lines[1].trim());
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
	
	public String getCurrency(String currency){
		if ( currency == null ) {
			currency = getAdmittanceEconomyInterface().getDefaultCurrency();
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
		ApplicableRegionSet set = getWorldGuard().getRegionManager(loc.getWorld()).getApplicableRegions(loc);
		List<ProtectedRegion> candidates = new LinkedList<ProtectedRegion>();
		String playerName = player.getName();
		for (ProtectedRegion region : set){
			if ( sellUnowned && !region.hasMembersOrOwners()){
				candidates.add(region);
			} else if (isExclusiveOwner(playerName, region)){
				candidates.add(region);
			}
		}
		return candidates;
	}

	public static void popSign(Block block) {
		Material mat = block.getType();
		if ( (mat==Material.SIGN) || (mat==Material.SIGN_POST)){
			block.setType(Material.AIR);
			block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(323,1));
			block.getState().update();
		}
	}
}