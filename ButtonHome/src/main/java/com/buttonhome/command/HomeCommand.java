package com.buttonhome.command;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
        if (!player.hasPermission("homebutton.use") && !player.hasPermission("homebutton.admin") && !player.isOp()) {
            plugin.sendConfigMessage(player, "messages.no-permission", null);
            return true;
        }

        // Check menu mode configuration (GUI or CHAT)
        String mode = plugin.getConfig().getString("menu-mode", "GUI");

        // Check if the command is /homes
        if (command.getName().equalsIgnoreCase("homes")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("plain")) {
                showPlainHomesList(player, uuid, homeManager);
            } else if (mode.equalsIgnoreCase("CHAT")) {
                List<HomeManager.Home> homes = homeManager.getHomes(uuid);
                renderStage1Menu(player, homes, true); // true for expanded grid
            } else {
                plugin.getGuiManager().openGridGui(player, true);
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

            // Trigger Stage 2 directly
            if (mode.equalsIgnoreCase("CHAT")) {
                showStage2Menu(player, home);
            } else {
                plugin.getGuiManager().openStage2Gui(player, home);
            }
            return true;
        }

        // /home with no arguments (Stage 1 compact menu)
        if (mode.equalsIgnoreCase("CHAT")) {
            List<HomeManager.Home> homes = homeManager.getHomes(uuid);
            renderStage1Menu(player, homes, false); // false for compact grid
        } else {
            plugin.getGuiManager().openGridGui(player, false);
        }
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

    private void renderStage1Menu(Player player, List<HomeManager.Home> homes, boolean expanded) {
        int limit = plugin.getMaxHomes(player);
        int homesCount = homes.size();
        
        // Calculate grid slots to display (defaults to 9 minimum, or the player's active limit capped at 50)
        int totalSlots;
        if (expanded) {
            totalSlots = Math.min(50, limit == Integer.MAX_VALUE ? Math.max(9, homesCount + 2) : limit);
        } else {
            totalSlots = Math.min(9, limit == Integer.MAX_VALUE ? 9 : limit);
        }

        List<Component> elements = new ArrayList<>();

        // 1. Build all the slots
        for (int i = 0; i < totalSlots; i++) {
            if (i < homesCount) {
                // Occupied slot
                HomeManager.Home home = homes.get(i);
                String x = String.valueOf((int) home.getX());
                String y = String.valueOf((int) home.getY());
                String z = String.valueOf((int) home.getZ());

                Map<String, String> placeholders = Map.of(
                        "%index%", String.valueOf(i + 1),
                        "%home%", home.getName(),
                        "%world%", home.getWorldName(),
                        "%x%", x,
                        "%y%", y,
                        "%z%", z
                );
                String template = plugin.getFormattedMessage("messages.grid-occupied-button");
                if (template.isEmpty()) {
                    template = "<hover:show_text:'<gray>World: <white>%world%</white><br>Coordinates: <white>%x%, %y%, %z%</white><br><yellow>Click to manage this home</yellow>'><click:run_command:'/homeselect %home%'><dark_gray>[</dark_gray><white>■ %home%</white><dark_gray>]</dark_gray></click></hover>";
                }
                elements.add(plugin.parseMiniMessage(template, placeholders));
            } else if (limit == Integer.MAX_VALUE || i < limit) {
                // Empty / New Home slot
                String indexStr = String.valueOf(i + 1);
                String defaultHomeName = "home_" + indexStr;
                Map<String, String> placeholders = Map.of(
                        "%index%", indexStr,
                        "%home%", defaultHomeName
                );
                String template = plugin.getFormattedMessage("messages.grid-new-button");
                if (template.isEmpty()) {
                    template = "<hover:show_text:'<gray>Unused Slot %index%</gray><br><yellow>Click to set a home here</yellow>'><click:run_command:'/homesetprompt %home%'><dark_gray>[</dark_gray><gray>New Home</gray><dark_gray>]</dark_gray></click></hover>";
                }
                elements.add(plugin.parseMiniMessage(template, placeholders));
            } else {
                // Locked slot
                String template = plugin.getFormattedMessage("messages.grid-locked-button");
                if (template.isEmpty()) {
                    template = "<hover:show_text:'<red>Locked Slot!</red><br><gray>Upgrade your rank to unlock more homes.</gray>'><click:run_command:'/buttonhome locked'><dark_gray>[</dark_gray><red>Locked</red><dark_gray>]</dark_gray></click></hover>";
                }
                elements.add(plugin.parseMiniMessage(template, null));
            }
        }

        // 2. Append the "Show More" button at the end ONLY if in compact view and limit > 9
        if (!expanded && (limit > 9 || limit == Integer.MAX_VALUE)) {
            String showMoreTemplate = plugin.getFormattedMessage("messages.grid-showmore-button");
            if (showMoreTemplate.isEmpty()) {
                showMoreTemplate = "<hover:show_text:'<gray>Click to view plain-text list of homes</gray>'><click:run_command:'/homes'><dark_gray>[</dark_gray><white>Show More</white><dark_gray>]</dark_gray></click></hover>";
            }
            elements.add(plugin.parseMiniMessage(showMoreTemplate, null));
        }

        // 3. Render the Header
        String titleTemplate = plugin.getFormattedMessage("messages.grid-title");
        if (titleTemplate.isEmpty()) {
            titleTemplate = "<bold><white>Homes</white></bold> <yellow>⚠️</yellow>";
        }
        player.sendMessage(plugin.parseMiniMessage(titleTemplate, null));

        // 4. Render the elements in rows of 6
        TextComponent.Builder rowBuilder = Component.text();
        for (int i = 0; i < elements.size(); i++) {
            rowBuilder.append(elements.get(i));

            boolean isLastInRow = ((i + 1) % 6 == 0) || (i == elements.size() - 1);
            if (!isLastInRow) {
                rowBuilder.append(Component.text("  ")); // double spacing for beautiful layout breathing room
            } else {
                player.sendMessage(rowBuilder.build());
                rowBuilder = Component.text();
            }
        }
    }

    /**
     * Shared logic to render Stage 2 buttons.
     */
    public void showStage2Menu(Player player, HomeManager.Home home) {
        player.sendMessage(plugin.parseMiniMessage(plugin.getFormattedMessage("messages.stage2-title")
                .replace("%home%", home.getName()), null));
        player.sendMessage(plugin.parseMiniMessage(plugin.getFormattedMessage("messages.menu-divider"), null));

        Component tpBtn = plugin.parseMiniMessage(plugin.getFormattedMessage("messages.stage2-teleport-btn")
                .replace("%home%", home.getName()), null);
        Component cancelBtn = plugin.parseMiniMessage(plugin.getFormattedMessage("messages.stage2-cancel-btn")
                .replace("%home%", home.getName()), null);

        // Send side by side with spacing
        player.sendMessage(tpBtn.append(Component.text("   ")).append(cancelBtn));
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
