package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiManager {

    private final ButtonHome plugin;
    private final NamespacedKey buttonTypeKey;
    private final NamespacedKey buttonDataKey;

    public GuiManager(ButtonHome plugin) {
        this.plugin = plugin;
        this.buttonTypeKey = new NamespacedKey(plugin, "button_type");
        this.buttonDataKey = new NamespacedKey(plugin, "button_data");
    }

    public NamespacedKey getButtonTypeKey() {
        return buttonTypeKey;
    }

    public NamespacedKey getButtonDataKey() {
        return buttonDataKey;
    }

    public static class HomeGuiHolder implements InventoryHolder {
        private final String guiType; // "grid", "stage2", "stage3"
        private final String data;    // home name or metadata

        public HomeGuiHolder(String guiType, String data) {
            this.guiType = guiType;
            this.data = data;
        }

        public String getGuiType() {
            return guiType;
        }

        public String getData() {
            return data;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * Open Stage 1 Grid Inventory GUI for player
     */
    public void openGridGui(Player player, boolean expanded) {
        HomeManager homeManager = plugin.getHomeManager();
        List<HomeManager.Home> homes = homeManager.getHomes(player.getUniqueId());
        int limit = plugin.getMaxHomes(player);
        int count = homes.size();

        int totalSlots = expanded ? Math.min(50, limit == Integer.MAX_VALUE ? Math.max(9, count + 2) : limit) : Math.min(9, limit == Integer.MAX_VALUE ? 9 : limit);

        // Determine inventory rows (minimum 27 slots = 3 rows, or 54 slots = 6 rows)
        int invSize = (totalSlots > 18 || (!expanded && limit > 9)) ? 27 : 18;
        if (totalSlots > 27) invSize = 54;

        Component title = plugin.parseMiniMessage(plugin.getFormattedMessage("messages.grid-title"), null);
        if (title.equals(Component.empty())) {
            title = plugin.parseMiniMessage("<bold><white>Homes</white></bold> <yellow>⚠️</yellow>", null);
        }

        Inventory gui = Bukkit.createInventory(new HomeGuiHolder("grid", expanded ? "expanded" : "compact"), invSize, title);

        for (int i = 0; i < totalSlots; i++) {
            if (i < count) {
                // Occupied Home slot
                HomeManager.Home home = homes.get(i);
                ItemStack item = new ItemStack(Material.WHITE_BED);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(plugin.parseMiniMessage("<white>■ <gold>" + home.getName() + "</gold></white>", null));
                    List<Component> lore = new ArrayList<>();
                    lore.add(plugin.parseMiniMessage("<gray>World: <white>" + home.getWorldName() + "</white></gray>", null));
                    lore.add(plugin.parseMiniMessage("<gray>X: <white>" + (int) home.getX() + "</white> Y: <white>" + (int) home.getY() + "</white> Z: <white>" + (int) home.getZ() + "</white></gray>", null));
                    lore.add(Component.empty());
                    lore.add(plugin.parseMiniMessage("<yellow>▶ Click to manage or teleport!</yellow>", null));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "select_home");
                    meta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, home.getName());
                    item.setItemMeta(meta);
                }
                gui.setItem(i, item);
            } else if (limit == Integer.MAX_VALUE || i < limit) {
                // Available New Home slot
                String defaultHomeName = "home_" + (i + 1);
                ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(plugin.parseMiniMessage("<gray>[New Home]</gray>", null));
                    List<Component> lore = new ArrayList<>();
                    lore.add(plugin.parseMiniMessage("<gray>Unused Slot " + (i + 1) + "</gray>", null));
                    lore.add(plugin.parseMiniMessage("<yellow>▶ Click to set a home here</yellow>", null));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "new_home");
                    meta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, defaultHomeName);
                    item.setItemMeta(meta);
                }
                gui.setItem(i, item);
            } else {
                // Locked slot
                ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(plugin.parseMiniMessage("<red>[Locked]</red>", null));
                    List<Component> lore = new ArrayList<>();
                    lore.add(plugin.parseMiniMessage("<red>Locked Slot!</red>", null));
                    lore.add(plugin.parseMiniMessage("<gray>Requires permission homebutton.limit." + (i + 1) + "</gray>", null));
                    meta.lore(lore);

                    meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "locked");
                    meta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, String.valueOf(i + 1));
                    item.setItemMeta(meta);
                }
                gui.setItem(i, item);
            }
        }

        // Show More Button
        if (!expanded && (limit > 9 || limit == Integer.MAX_VALUE)) {
            ItemStack showMore = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = showMore.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.parseMiniMessage("<light_purple><bold>Show More</bold></light_purple>", null));
                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseMiniMessage("<gray>Click to expand all available home slots</gray>", null));
                meta.lore(lore);

                meta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "show_more");
                meta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, "true");
                showMore.setItemMeta(meta);
            }
            gui.setItem(invSize - 1, showMore);
        }

        player.openInventory(gui);
    }

    /**
     * Open Stage 2 Manage Home GUI for player
     */
    public void openStage2Gui(Player player, HomeManager.Home home) {
        Component title = plugin.parseMiniMessage("<dark_gray>Manage: </dark_gray><gold>" + home.getName() + "</gold>", null);
        Inventory gui = Bukkit.createInventory(new HomeGuiHolder("stage2", home.getName()), 27, title);

        // Teleport Button
        ItemStack tpItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta tpMeta = tpItem.getItemMeta();
        if (tpMeta != null) {
            tpMeta.displayName(plugin.parseMiniMessage("<green><bold>✔ TELEPORT</bold></green>", null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseMiniMessage("<gray>Teleport to <gold>" + home.getName() + "</gold></gray>", null));
            lore.add(plugin.parseMiniMessage("<gray>World: " + home.getWorldName() + " (" + (int)home.getX() + ", " + (int)home.getY() + ", " + (int)home.getZ() + ")</gray>", null));
            tpMeta.lore(lore);
            tpMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "tp");
            tpMeta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, home.getName());
            tpItem.setItemMeta(tpMeta);
        }
        gui.setItem(11, tpItem);

        // Delete Button
        ItemStack delItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta delMeta = delItem.getItemMeta();
        if (delMeta != null) {
            delMeta.displayName(plugin.parseMiniMessage("<red><bold>❌ DELETE HOME</bold></red>", null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseMiniMessage("<gray>Delete home <gold>" + home.getName() + "</gold></gray>", null));
            lore.add(plugin.parseMiniMessage("<red>This action cannot be undone!</red>", null));
            delMeta.lore(lore);
            delMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "delete");
            delMeta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, home.getName());
            delItem.setItemMeta(delMeta);
        }
        gui.setItem(15, delItem);

        // Back Button
        ItemStack backItem = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(plugin.parseMiniMessage("<gray>↩ Back to Homes</gray>", null));
            backMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "back");
            backMeta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, "grid");
            backItem.setItemMeta(backMeta);
        }
        gui.setItem(22, backItem);

        player.openInventory(gui);
    }

    /**
     * Open Stage 3 Set Home Confirmation GUI for player
     */
    public void openStage3Gui(Player player, String homeName) {
        Component title = plugin.parseMiniMessage("<dark_gray>Set Home: </dark_gray><gold>" + homeName + "</gold><dark_gray>?</dark_gray>", null);
        Inventory gui = Bukkit.createInventory(new HomeGuiHolder("stage3", homeName), 27, title);

        // Confirm Set Home Button
        ItemStack setItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta setMeta = setItem.getItemMeta();
        if (setMeta != null) {
            setMeta.displayName(plugin.parseMiniMessage("<green><bold>✔ SET HOME HERE</bold></green>", null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseMiniMessage("<gray>Save home <gold>" + homeName + "</gold> at current location</gray>", null));
            setMeta.lore(lore);
            setMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "confirm_set");
            setMeta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, homeName);
            setItem.setItemMeta(setMeta);
        }
        gui.setItem(11, setItem);

        // Cancel Button
        ItemStack cancelItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(plugin.parseMiniMessage("<red><bold>✘ CANCEL</bold></red>", null));
            cancelMeta.getPersistentDataContainer().set(buttonTypeKey, PersistentDataType.STRING, "back");
            cancelMeta.getPersistentDataContainer().set(buttonDataKey, PersistentDataType.STRING, "grid");
            cancelItem.setItemMeta(cancelMeta);
        }
        gui.setItem(15, cancelItem);

        player.openInventory(gui);
    }
}
