package me.asofold.bpl.rbuy.mixin.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MixinUtil {

	public static void sendErrorMessage(CommandSender sender, String message) {
		if ( sender instanceof Player) send(sender, message, ChatColor.DARK_RED);
		else send(sender, "[ERROR] "+message);
	}

	public static void sendSuccessMessage(CommandSender sender, String message) {
		send(sender, message, ChatColor.DARK_GREEN);
	}
	
	public static void send(CommandSender sender, String message, ChatColor color){
		if (sender instanceof Player) sender.sendMessage(color.toString()+message);
		else sender.sendMessage(ChatColor.stripColor(message));
	}
	
	public static void send(CommandSender sender, String message){
		if (sender instanceof Player) sender.sendMessage(message);
		else sender.sendMessage(ChatColor.stripColor(message));
	}

}
