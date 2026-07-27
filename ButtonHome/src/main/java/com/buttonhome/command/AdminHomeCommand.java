package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class AdminHomeCommand implements CommandExecutor, TabCompleter {

    private final ButtonHome plugin;

    public AdminHomeCommand(ButtonHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getFormattedMessage("messages.player-only")
                    .replace("<red>", "").replace("</red>", ""));
            return true;
        }

        Player admin = (Player) sender;

        // Admin check: either OP or has homebutton.admin permission
        if (!admin.hasPermission("homebutton.admin") && !admin.isOp()) {
            plugin.sendConfigMessage(admin, "messages.no-permission", null);
            return true;
        }

        if (args.length == 0) {
            admin.sendMessage(plugin.parseMiniMessage("<red>Usage: /adminhome <playername> [homename]</red>", null));
            return true;
        }

        String targetName = args[0];

        // Retrieve player's UUID
        UUID targetUuid = null;
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
            targetName = targetPlayer.getName(); // Normalize case
        } else {
            // Check offline players
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            if (offlinePlayer.hasPlayedBefore()) {
                targetUuid = offlinePlayer.getUniqueId();
                if (offlinePlayer.getName() != null) {
                    targetName = offlinePlayer.getName(); // Normalize case
                }
            }
        }

        if (targetUuid == null) {
            admin.sendMessage(plugin.parseMiniMessage("<red>Player not found or has never played before.</red>", null));
            return true;
        }

        List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(targetUuid);

        // If a specific home is requested, teleport there instantly
        if (args.length >= 2) {
            String homeName = args[1];
            HomeManager.Home home = plugin.getHomeManager().getHome(targetUuid, homeName);
            if (home == null) {
                admin.sendMessage(plugin.parseMiniMessage("<red>" + targetName + " does not have a home named <yellow>" + homeName + "</yellow>.</red>", null));
                return true;
            }

            Location loc = home.toLocation();
            if (loc == null || loc.getWorld() == null) {
                admin.sendMessage(plugin.parseMiniMessage("<red>✘ Teleport failed — the world <white>" + home.getWorldName() + "</white> is not loaded.</red>", null));
                return true;
            }

            // Admins teleport instantly (bypassing cooldown and warmup)
            admin.teleport(loc);
            admin.sendMessage(plugin.parseMiniMessage("<green>✔ Instantly teleported to " + targetName + "'s home: <white>" + home.getName() + "</white>.</green>", null));
            return true;
        }

        // Otherwise, render target player's homes list grid
        if (homes.isEmpty()) {
            admin.sendMessage(plugin.parseMiniMessage("<gray>" + targetName + " has no homes set.</gray>", null));
            return true;
        }

        renderAdminStage1Menu(admin, targetName, homes);
        return true;
    }

    private void renderAdminStage1Menu(Player admin, String targetName, List<HomeManager.Home> homes) {
        List<Component> elements = new ArrayList<>();

        for (int i = 0; i < homes.size(); i++) {
            HomeManager.Home home = homes.get(i);
            String x = String.valueOf((int) home.getX());
            String y = String.valueOf((int) home.getY());
            String z = String.valueOf((int) home.getZ());

            // Button template for admin teleport
            String template = "<hover:show_text:'<gray>World: <white>%world%</white><br>Coordinates: <white>%x%, %y%, %z%</white><br><yellow>Click to teleport to %player%\\'s home</yellow>'><click:run_command:'/adminhome %player% %home%'><dark_gray>[</dark_gray><gold>■ %home%</gold><dark_gray>]</dark_gray></click></hover>";

            Component button = plugin.parseMiniMessage(template
                    .replace("%player%", targetName)
                    .replace("%home%", home.getName())
                    .replace("%world%", home.getWorldName())
                    .replace("%x%", x)
                    .replace("%y%", y)
                    .replace("%z%", z), null);
            elements.add(button);
        }

        // Render Header
        admin.sendMessage(plugin.parseMiniMessage("<gold>✦ <bold>" + targetName.toUpperCase() + "'S HOMES</bold> ✦</gold>", null));
        admin.sendMessage(plugin.parseMiniMessage("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>", null));

        // Render rows of 6
        TextComponent.Builder rowBuilder = Component.text();
        for (int i = 0; i < elements.size(); i++) {
            rowBuilder.append(elements.get(i));

            boolean isLastInRow = ((i + 1) % 6 == 0) || (i == elements.size() - 1);
            if (!isLastInRow) {
                rowBuilder.append(Component.text("  "));
            } else {
                admin.sendMessage(rowBuilder.build());
                rowBuilder = Component.text();
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player admin = (Player) sender;
        if (!admin.hasPermission("homebutton.admin") && !admin.isOp()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Suggest online players
            List<String> suggestions = new ArrayList<>();
            String current = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(current)) {
                    suggestions.add(player.getName());
                }
            }
            return suggestions;
        }

        if (args.length == 2) {
            String targetName = args[0];
            Player targetPlayer = Bukkit.getPlayer(targetName);
            UUID targetUuid = null;
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
            } else {
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                if (offlinePlayer != null) {
                    targetUuid = offlinePlayer.getUniqueId();
                }
            }

            if (targetUuid != null) {
                List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(targetUuid);
                List<String> suggestions = new ArrayList<>();
                String current = args[1].toLowerCase();
                for (HomeManager.Home home : homes) {
                    if (home.getName().toLowerCase().startsWith(current)) {
                        suggestions.add(home.getName());
                    }
                }
                return suggestions;
            }
        }

        return Collections.emptyList();
    }
}
