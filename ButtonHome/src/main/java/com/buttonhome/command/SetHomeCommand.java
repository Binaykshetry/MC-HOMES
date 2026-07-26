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
        if (!player.hasPermission("homebutton.use") && !player.hasPermission("homebutton.admin") && !player.isOp()) {
            plugin.sendConfigMessage(player, "messages.no-permission", null);
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

        // 3. Check home limit
        int currentCount = homeManager.getHomeCount(uuid);
        int maxHomes = plugin.getMaxHomes(player);
        boolean isOverwrite = homeManager.getHome(uuid, homeName) != null;

        if (!isOverwrite && currentCount >= maxHomes) {
            plugin.sendConfigMessage(player, "messages.limit-reached", Map.of(
                    "%limit%", String.valueOf(maxHomes)
            ));
            return true;
        }

        // 4. Save home
        homeManager.setHome(uuid, homeName, player.getLocation());
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
