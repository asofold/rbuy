package me.asofold.bpl.rbuy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.asofold.bpl.rbuy.Rbuy.Offer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class RbuyTabExecutor implements TabExecutor {
	
	public static final Set<String> getSet(String... args){
		Set<String> set = new LinkedHashSet<String>(args.length);
		for (String arg : args){
			set.add(arg);
		}
		return set;
	}
	
	
	private final Rbuy plugin;
	
	private final Set<String> cmdRegion = getSet(
			"rbuy", "rsell", "rinfo", "rtransfer"
			);

	public RbuyTabExecutor(final Rbuy plugin){
		this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
	{
		String label = command.getLabel().toLowerCase();
		List<String> res = new ArrayList<String>(20);
		// TODO: Region completion for simple commands !
		int len = args.length;
		
		if (len == 1 && cmdRegion.contains(label) && (sender instanceof Player)){
			String arg = args[0];
			Player player = (Player) sender;
			String playerName = player.getName();
			// TODO: check permissions or otherwise restrict it!
			String worldName = player.getWorld().getName();
			WorldGuardPlugin wg = Rbuy.getWorldGuard();
			LocalPlayer lp = wg.wrapPlayer(player);
			ApplicableRegionSet set = wg.getRegionManager(player.getWorld()).getApplicableRegions(player.getLocation());
			if (set.size() == 0) return res;
			if (label.equals("rsell")){
				for (ProtectedRegion region : set){
					if (region.getId().toLowerCase().startsWith(arg) && region.isOwner(lp)) res.add(region.getId());
				}
			}
			else if (label.equals("rbuy")){
				for (ProtectedRegion region : set){
					String rid = region.getId();
					if (!rid.toLowerCase().startsWith(arg)) continue;
					Offer offer = plugin.getOffer(rid, worldName);
					if (offer == null) continue;
					if (playerName.equalsIgnoreCase(offer.benefits)) continue;
					res.add(rid);
				}
			}
			else if (label.equals("rinfo") || label.equals("rtransfer")){
				for (ProtectedRegion region : set){
					String rid = region.getId();
					if (!rid.toLowerCase().startsWith(arg)) continue;
					Offer offer = plugin.getOffer(rid, worldName);
					if (offer == null) continue;
					res.add(rid);
				}
			}
			if (res.isEmpty()) return res;
		}
		
		return res.isEmpty() ? null : res;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args)
	{
		return plugin.processCommand(sender, command, alias, args);
	}

}
