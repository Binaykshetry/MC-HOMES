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
                List<HomeManager.Home> homes = homeManager.getHomes(uuid);
                renderStage1Paginated(player, homes, page);
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
            showStage2Menu(player, home);
            return true;
        }

        // /home with no arguments (Stage 1 compact menu - 9 slots)
        List<HomeManager.Home> homes = homeManager.getHomes(uuid);
        renderStage1Compact(player, homes);
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

    private void renderStage1Compact(Player player, List<HomeManager.Home> homes) {
        int limit = plugin.getMaxHomes(player);
        int homesCount = homes.size();
        int totalSlots = Math.min(9, limit == Integer.MAX_VALUE ? 9 : limit);

        List<Component> elements = new ArrayList<>();

        for (int i = 0; i < totalSlots; i++) {
            elements.add(createSlotButton(player, homes, i, limit, homesCount));
        }

        // Append [Show More] button if limit > 9 or unlimited
        if (limit > 9 || limit == Integer.MAX_VALUE) {
            String showMoreTemplate = plugin.getFormattedMessage("messages.grid-showmore-button");
            if (showMoreTemplate.isEmpty()) {
                showMoreTemplate = "<hover:show_text:'<gray>Click to open expanded paginated homes list</gray>'><click:run_command:'/homes 1'><dark_gray>[</dark_gray><white>Show More</white><dark_gray>]</dark_gray></click></hover>";
            }
            elements.add(plugin.parseMiniMessage(showMoreTemplate, null));
        }

        // Render Header
        String titleTemplate = plugin.getFormattedMessage("messages.grid-title");
        if (titleTemplate.isEmpty()) {
            titleTemplate = "<bold><white>Homes</white></bold> <yellow>⚠️</yellow>";
        }
        player.sendMessage(plugin.parseMiniMessage(titleTemplate, null));

        // Render buttons in rows of 6
        renderGridRows(player, elements);
    }

    private void renderStage1Paginated(Player player, List<HomeManager.Home> homes, int page) {
        int limit = plugin.getMaxHomes(player);
        int homesCount = homes.size();
        int totalSlots = limit == Integer.MAX_VALUE ? Math.max(9, homesCount) : limit;
        int totalPages = (int) Math.ceil((double) totalSlots / 9.0);
        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startSlot = (page - 1) * 9;
        int endSlot = Math.min(startSlot + 9, totalSlots);

        List<Component> elements = new ArrayList<>();
        for (int i = startSlot; i < endSlot; i++) {
            elements.add(createSlotButton(player, homes, i, limit, homesCount));
        }

        // Render Header
        Map<String, String> pageMap = Map.of("%page%", String.valueOf(page), "%total_pages%", String.valueOf(totalPages));
        String titleTemplate = plugin.getFormattedMessage("messages.grid-title-paginated");
        if (titleTemplate.isEmpty()) {
            titleTemplate = "<bold><white>Homes</white></bold> <yellow>⚠️</yellow> <gray>(Page %page%/%total_pages%)</gray>";
        }
        player.sendMessage(plugin.parseMiniMessage(titleTemplate, pageMap));

        // Render buttons in rows of 6
        renderGridRows(player, elements);

        // Render Pagination Nav Controls
        if (totalPages > 1) {
            Component nav = Component.empty();
            if (page > 1) {
                String prevTemplate = plugin.getFormattedMessage("messages.prev-page-button");
                if (prevTemplate.isEmpty()) {
                    prevTemplate = "<hover:show_text:'<gray>Click for previous page</gray>'><click:run_command:'/homes %page%'><dark_gray>[</dark_gray><yellow>◀ Prev</yellow><dark_gray>]</dark_gray></click></hover>";
                }
                nav = nav.append(plugin.parseMiniMessage(prevTemplate, Map.of("%page%", String.valueOf(page - 1)))).append(Component.text("  "));
            }

            nav = nav.append(plugin.parseMiniMessage("<gray>Page <white>" + page + "</white>/<white>" + totalPages + "</white></gray>", null));

            if (page < totalPages) {
                String nextTemplate = plugin.getFormattedMessage("messages.next-page-button");
                if (nextTemplate.isEmpty()) {
                    nextTemplate = "<hover:show_text:'<gray>Click for next page</gray>'><click:run_command:'/homes %page%'><dark_gray>[</dark_gray><yellow>Next ▶</yellow><dark_gray>]</dark_gray></click></hover>";
                }
                nav = nav.append(Component.text("  ")).append(plugin.parseMiniMessage(nextTemplate, Map.of("%page%", String.valueOf(page + 1))));
            }
            player.sendMessage(nav);
        }
    }

    private Component createSlotButton(Player player, List<HomeManager.Home> homes, int slotIndex, int limit, int homesCount) {
        if (slotIndex < homesCount) {
            // Occupied slot
            HomeManager.Home home = homes.get(slotIndex);
            Map<String, String> placeholders = Map.of(
                    "%index%", String.valueOf(slotIndex + 1),
                    "%home%", home.getName(),
                    "%world%", home.getWorldName(),
                    "%x%", String.valueOf((int) home.getX()),
                    "%y%", String.valueOf((int) home.getY()),
                    "%z%", String.valueOf((int) home.getZ())
            );
            String template = plugin.getFormattedMessage("messages.grid-occupied-button");
            if (template.isEmpty()) {
                template = "<hover:show_text:'<gray>World: <white>%world%</white><br>Coordinates: <white>%x%, %y%, %z%</white><br><yellow>Click to manage this home</yellow>'><click:run_command:'/homeselect %home%'><dark_gray>[</dark_gray><white>■ %home%</white><dark_gray>]</dark_gray></click></hover>";
            }
            return plugin.parseMiniMessage(template, placeholders);
        } else if (limit == Integer.MAX_VALUE || slotIndex < limit) {
            // New Home slot
            String indexStr = String.valueOf(slotIndex + 1);
            String defaultHomeName = "home_" + indexStr;
            Map<String, String> placeholders = Map.of(
                    "%index%", indexStr,
                    "%home%", defaultHomeName
            );
            String template = plugin.getFormattedMessage("messages.grid-new-button");
            if (template.isEmpty()) {
                template = "<hover:show_text:'<gray>Unused Slot %index%</gray><br><yellow>Click to set a home here</yellow>'><click:run_command:'/homesetprompt %home%'><dark_gray>[</dark_gray><gray>New Home</gray><dark_gray>]</dark_gray></click></hover>";
            }
            return plugin.parseMiniMessage(template, placeholders);
        } else {
            // Locked slot
            Map<String, String> placeholders = Map.of("%index%", String.valueOf(slotIndex + 1));
            String template = plugin.getFormattedMessage("messages.grid-locked-button");
            if (template.isEmpty()) {
                template = "<hover:show_text:'<red>Locked Slot %index%!</red><br><gray>Requires permission homebutton.limit.%index%</gray>'><click:run_command:'/buttonhome locked'><dark_gray>[</dark_gray><red>Locked</red><dark_gray>]</dark_gray></click></hover>";
            }
            return plugin.parseMiniMessage(template, placeholders);
        }
    }

    private void renderGridRows(Player player, List<Component> elements) {
        TextComponent.Builder rowBuilder = Component.text();
        for (int i = 0; i < elements.size(); i++) {
            rowBuilder.append(elements.get(i));

            boolean isLastInRow = ((i + 1) % 6 == 0) || (i == elements.size() - 1);
            if (!isLastInRow) {
                rowBuilder.append(Component.text("  "));
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
