package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final ButtonHome plugin;

    public HomeCommand(ButtonHome plugin) {
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
            player.sendMessage(plugin.parseMiniMessage("<red>You don't have permission to use home</red>", null));
            return true;
        }

        // Check if the command is /homes
        if (command.getName().equalsIgnoreCase("homes")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("plain")) {
                showPlainHomesList(player, uuid, homeManager);
            } else {
                int page = 1;
                if (args.length > 0) {
                    try {
                        page = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                        page = 1;
                    }
                }
                plugin.getDialogManager().openHomeGrid(player, page);
            }
            return true;
        }

        // /home with arguments (shortcut to Stage 2)
        if (args.length > 0) {
            String homeName = args[0];
            HomeManager.Home home = homeManager.getHome(uuid, homeName);
            if (home == null) {
                plugin.sendConfigMessage(player, "messages.no-such-home", Map.of("%home%", homeName));
                return true;
            }

            // Trigger Stage 2
            plugin.getDialogManager().openManageHome(player, home);
            return true;
        }

        // /home with no arguments (Stage 1 compact menu)
        plugin.getDialogManager().openHomeGrid(player, 1);
        return true;
    }

    private void showPlainHomesList(Player player, UUID uuid, HomeManager homeManager) {
        List<HomeManager.Home> homes = homeManager.getHomes(uuid);
        int limit = plugin.getMaxHomes(player);
        int count = homes.size();

        if (homes.isEmpty()) {
            plugin.sendConfigMessage(player, "messages.no-homes", null);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < homes.size(); i++) {
            sb.append(homes.get(i).getName());
            if (i < homes.size() - 1) {
                sb.append(", ");
            }
        }

        Map<String, String> placeholders = Map.of(
                "%count%", String.valueOf(count),
                "%limit%", limit == Integer.MAX_VALUE ? "Unlimited" : String.valueOf(limit)
        );

        String prefix = plugin.getFormattedMessage("messages.plain-list-prefix");
        Component prefixComp = plugin.parseMiniMessage(prefix, placeholders);
        Component listComp = Component.text(sb.toString());

        player.sendMessage(prefixComp.append(listComp));
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
