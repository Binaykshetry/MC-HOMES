package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final ButtonHome plugin;

    public DelHomeCommand(ButtonHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getFormattedMessage("messages.player-only")
                    .replace("<red>", "").replace("</red>", ""));
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        HomeManager homeManager = plugin.getHomeManager();

        // Check command permission
        if (!player.hasPermission("buttonhome.use") && !player.hasPermission("home.use") && !player.hasPermission("homebutton.use") && !player.hasPermission("homebutton.admin") && !player.hasPermission("buttonhome.admin") && !player.isOp()) {
            player.sendMessage(net.kyori.adventure.text.Component.text("You don't have permission to use home", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        // 1. Check arguments
        if (args.length == 0) {
            plugin.sendConfigMessage(player, "messages.usage-hint", Map.of(
                    "%usage%", "/delhome <name>"
            ));
            return true;
        }

        String homeName = args[0];

        // 2. Delete the home
        boolean success = homeManager.deleteHome(uuid, homeName);
        if (success) {
            plugin.sendConfigMessage(player, "messages.home-deleted", Map.of(
                    "%home%", homeName
            ));
        } else {
            plugin.sendConfigMessage(player, "messages.no-such-home", Map.of(
                    "%home%", homeName
            ));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        if (args.length == 1) {
            List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
            List<String> suggestions = new ArrayList<>();
            for (HomeManager.Home home : homes) {
                if (home.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(home.getName());
                }
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
