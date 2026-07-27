package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final ButtonHome plugin;

    public SetHomeCommand(ButtonHome plugin) {
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

        // 1. Resolve home name (default to "home" if empty)
        String homeName = "home";
        if (args.length > 0) {
            homeName = args[0];
        }

        // 2. Validate home name (prevent injection / bad characters)
        if (!homeName.matches("^[A-Za-z0-9_-]{1,16}$")) {
            plugin.sendConfigMessage(player, "messages.invalid-name", null);
            return true;
        }

        // 3. Resolve slot and check limit
        HomeManager.Home existingHome = homeManager.getHome(uuid, homeName);
        int slotToUse;
        if (existingHome != null) {
            slotToUse = existingHome.getSlot();
        } else {
            slotToUse = homeManager.getFirstAvailableSlot(player);
            if (slotToUse == -1) {
                plugin.sendConfigMessage(player, "messages.limit-reached", Map.of(
                        "%limit%", "50"
                ));
                return true;
            }
        }

        // 4. Save home
        homeManager.setHome(uuid, homeName, player.getLocation(), null, slotToUse);
        plugin.sendConfigMessage(player, "messages.home-set", Map.of(
                "%home%", homeName
        ));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        if (args.length == 1) {
            // Suggest current homes for overwrite
            List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
            List<String> suggestions = new ArrayList<>();
            for (HomeManager.Home home : homes) {
                if (home.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(home.getName());
                }
            }
            if (suggestions.isEmpty() && "home".startsWith(args[0].toLowerCase())) {
                suggestions.add("home");
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
