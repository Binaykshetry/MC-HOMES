package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import com.buttonhome.manager.TeleportManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InternalCommands implements CommandExecutor, TabCompleter {

    private final ButtonHome plugin;

    public InternalCommands(ButtonHome plugin) {
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
        String cmdName = command.getName().toLowerCase();

        // Enforce basic permissions for internal teleportation / selection commands
        if (cmdName.equals("homeselect") || cmdName.equals("hometp") || cmdName.equals("homecancel") || cmdName.equals("homesetprompt")) {
            if (!player.hasPermission("buttonhome.use") && !player.hasPermission("home.use") && !player.hasPermission("homebutton.use") && !player.hasPermission("homebutton.admin") && !player.hasPermission("buttonhome.admin") && !player.isOp()) {
                player.sendMessage(plugin.parseMiniMessage("<red>You don't have permission to use home</red>", null));
                return true;
            }
        }

        switch (cmdName) {
            case "homeselect":
                handleHomeSelect(player, uuid, args);
                break;
            case "homesetprompt":
                handleHomeSetPrompt(player, uuid, args);
                break;
            case "hometp":
                handleHomeTp(player, uuid, args);
                break;
            case "homecancel":
                handleHomeCancel(player, uuid);
                break;
            case "buttonhome":
                handleButtonHome(player, args);
                break;
        }

        return true;
    }

    private void handleHomeSelect(Player player, UUID uuid, String[] args) {
        if (args.length == 0) {
            plugin.sendConfigMessage(player, "messages.usage-hint", Map.of("%usage%", "/homeselect <home>"));
            return;
        }

        String homeName = args[0];
        HomeManager.Home home = plugin.getHomeManager().getHome(uuid, homeName);
        if (home == null) {
            plugin.sendConfigMessage(player, "messages.no-such-home", Map.of("%home%", homeName));
            return;
        }

        plugin.getDialogManager().openManageHome(player, home);
    }

    private void handleHomeSetPrompt(Player player, UUID uuid, String[] args) {
        if (args.length == 0) {
            plugin.sendConfigMessage(player, "messages.usage-hint", Map.of("%usage%", "/homesetprompt <home>"));
            return;
        }

        String homeName = args[0];
        plugin.getDialogManager().openRenameDialog(player, null, homeName);
    }

    private void handleHomeTp(Player player, UUID uuid, String[] args) {
        if (args.length == 0) {
            plugin.sendConfigMessage(player, "messages.usage-hint", Map.of("%usage%", "/hometp <home>"));
            return;
        }

        String homeName = args[0];
        HomeManager.Home home = plugin.getHomeManager().getHome(uuid, homeName);
        if (home == null) {
            plugin.sendConfigMessage(player, "messages.no-such-home", Map.of("%home%", homeName));
            return;
        }

        // 1. Re-check if world is loaded at teleport time
        Location loc = home.toLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.sendConfigMessage(player, "messages.world-not-loaded", Map.of(
                    "%world%", home.getWorldName()
            ));
            return;
        }

        // 2. Start teleportation warmup
        plugin.getTeleportManager().startTeleport(player, home.getName(), loc);
    }

    private void handleHomeCancel(Player player, UUID uuid) {
        boolean cancelled = plugin.getTeleportManager().cancelTeleport(uuid, TeleportManager.CancelReason.MANUAL);
        if (!cancelled) {
            plugin.sendConfigMessage(player, "messages.warmup-nothing-pending", null);
        }
    }

    private void handleButtonHome(Player player, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("locked")) {
                String index = args.length > 1 ? args[1] : "";
                plugin.sendConfigMessage(player, "messages.locked-slot-clicked", Map.of("%index%", index));
                return;
            } else if (sub.equals("reload")) {
                if (!player.hasPermission("homebutton.admin") && !player.hasPermission("buttonhome.admin") && !player.isOp()) {
                    plugin.sendConfigMessage(player, "messages.no-permission", null);
                    return;
                }
                plugin.reloadConfig();
                player.sendMessage(plugin.parseMiniMessage("<green>ButtonHome configuration reloaded successfully!</green>", null));
                return;
            }
        }

        // Help menu fallback
        player.sendMessage(plugin.parseMiniMessage("<gold>✦ ButtonHome Help ✦</gold>", null));
        player.sendMessage(plugin.parseMiniMessage("<yellow>/home</yellow> - Open the interactive homes grid.", null));
        player.sendMessage(plugin.parseMiniMessage("<yellow>/home [name]</yellow> - Quick-select a home.", null));
        player.sendMessage(plugin.parseMiniMessage("<yellow>/sethome [name]</yellow> - Set a home at your location.", null));
        player.sendMessage(plugin.parseMiniMessage("<yellow>/delhome <name></yellow> - Delete a home.", null));
        if (player.hasPermission("homebutton.admin") || player.hasPermission("buttonhome.admin") || player.isOp()) {
            player.sendMessage(plugin.parseMiniMessage("<yellow>/buttonhome reload</yellow> - Reload the plugin config.", null));
            player.sendMessage(plugin.parseMiniMessage("<yellow>/adminhome <playername> [homename]</yellow> - Manage or teleport to other players' homes.", null));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("buttonhome")) {
            if (args.length == 1) {
                java.util.List<String> suggestions = new java.util.ArrayList<>();
                if (sender.hasPermission("homebutton.admin") || sender.hasPermission("buttonhome.admin") || sender.isOp()) {
                    suggestions.add("reload");
                }
                suggestions.add("help");
                return suggestions;
            }
        }
        return Collections.emptyList();
    }
}
