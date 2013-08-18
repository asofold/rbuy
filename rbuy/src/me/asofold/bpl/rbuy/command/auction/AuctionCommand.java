package me.asofold.bpl.rbuy.command.auction;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import me.asofold.bpl.rbuy.Rbuy;
import me.asofold.bpl.rbuy.command.AbstractCommand;

public class AuctionCommand extends AbstractCommand<Rbuy> {

	public AuctionCommand(Rbuy access) {
		super(access, "rauction", "rbuy.filter.command.auction");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args)
	{
		// TODO Auto-generated method stub
		return super.onCommand(sender, command, alias, args);
	}
	
	

}
