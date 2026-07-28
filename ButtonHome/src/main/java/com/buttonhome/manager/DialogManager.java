package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DialogManager implements Listener {

    private final ButtonHome plugin;

    // Temporary chat input tracking maps
    private final Map<UUID, Integer> pendingSetHomeSlot = new HashMap<>();
    private final Map<UUID, HomeManager.Home> pendingSetHomeExistingHome = new HashMap<>();

    public DialogManager(ButtonHome plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // Holders to identify inventory types securely
    public static class HomeGridHolder implements InventoryHolder {
        private final int page;
        public HomeGridHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
        public int getPage() { return page; }
    }

    public static class ManageHomeHolder implements InventoryHolder {
        private final HomeManager.Home home;
        public ManageHomeHolder(HomeManager.Home home) { this.home = home; }
        @Override public Inventory getInventory() { return null; }
        public HomeManager.Home getHome() { return home; }
    }

    public static class ConfirmSetHomeHolder implements InventoryHolder {
        private final int slot;
        private final String name;
        public ConfirmSetHomeHolder(int slot, String name) { this.slot = slot; this.name = name; }
        @Override public Inventory getInventory() { return null; }
        public int getSlot() { return slot; }
        public String getName() { return name; }
    }

    public static class ConfirmDeleteHolder implements InventoryHolder {
        private final HomeManager.Home home;
        public ConfirmDeleteHolder(HomeManager.Home home) { this.home = home; }
        @Override public Inventory getInventory() { return null; }
        public HomeManager.Home getHome() { return home; }
    }

    public static class IconPickerHolder implements InventoryHolder {
        private final HomeManager.Home home;
        public IconPickerHolder(HomeManager.Home home) { this.home = home; }
        @Override public Inventory getInventory() { return null; }
        public HomeManager.Home getHome() { return home; }
    }

    private boolean hasSlotPermission(Player player, int slot) {
        if (player.hasPermission("buttonhome.admin") || player.hasPermission("homebutton.admin") || player.isOp()) {
            return true;
        }
        return player.hasPermission("buttonhome." + slot) || player.hasPermission("homebutton." + slot);
    }

    public void openHomeGrid(Player player) {
        openHomeGrid(player, 1);
    }

    // ------------------------------------------------------------------
    // 5.1 — HOME GRID
    // ------------------------------------------------------------------
    public void openHomeGrid(Player player, int page) {
        if (page < 1) page = 1;

        int rows = plugin.getConfig().getInt("gui.settings.rows", 3);
        if (rows < 1) rows = 1;
        if (rows > 6) rows = 6;
        int size = rows * 9;

        String titleStr = plugin.getConfig().getString("gui.stage1.title", "Homes Grid");
        titleStr = titleStr.replace("%page%", String.valueOf(page));
        Component title = plugin.parseMiniMessage(titleStr, null);

        Inventory inv = Bukkit.createInventory(new HomeGridHolder(page), size, title);

        List<Integer> buttonSlots = plugin.getConfig().getIntegerList("gui.settings.button-slots");
        if (buttonSlots == null || buttonSlots.isEmpty()) {
            buttonSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16);
        }
        int slotsPerPage = buttonSlots.size();

        List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());

        for (int i = 0; i < slotsPerPage; i++) {
            int invSlot = buttonSlots.get(i);
            if (invSlot < 0 || invSlot >= size) continue;

            int homeSlotIndex = (page - 1) * slotsPerPage + i + 1;

            if (hasSlotPermission(player, homeSlotIndex)) {
                HomeManager.Home home = getHomeAtSlot(homes, homeSlotIndex);
                if (home != null) {
                    // Occupied slot
                    Map<String, String> placeholders = Map.of(
                            "%home%", home.getName(),
                            "%world%", home.getWorldName(),
                            "%x%", String.valueOf(Math.round(home.getX())),
                            "%y%", String.valueOf(Math.round(home.getY())),
                            "%z%", String.valueOf(Math.round(home.getZ()))
                    );
                    Material mat;
                    try {
                        mat = Material.valueOf(home.getIconMaterial().toUpperCase());
                    } catch (Exception e) {
                        mat = Material.LIME_BED;
                    }
                    inv.setItem(invSlot, createGuiItem("gui.items.occupied", placeholders, mat));
                } else {
                    // Empty / New slot
                    Map<String, String> placeholders = Map.of("%index%", String.valueOf(homeSlotIndex));
                    inv.setItem(invSlot, createGuiItem("gui.items.new", placeholders, Material.PAPER));
                }
            } else {
                // Locked slot
                Map<String, String> placeholders = Map.of("%index%", String.valueOf(homeSlotIndex));
                inv.setItem(invSlot, createGuiItem("gui.items.locked", placeholders, Material.BARRIER));
            }
        }

        // Previous page button
        int prevPageSlot = plugin.getConfig().getInt("gui.settings.prev-page-slot", 18);
        if (page > 1 && prevPageSlot >= 0 && prevPageSlot < size) {
            inv.setItem(prevPageSlot, createGuiItem("gui.items.prev-page", Map.of("%page%", String.valueOf(page - 1)), Material.ARROW));
        }

        // Next page button
        int nextPageSlot = plugin.getConfig().getInt("gui.settings.next-page-slot", 26);
        boolean hasMore = (page * slotsPerPage) < 50;
        if (hasMore && nextPageSlot >= 0 && nextPageSlot < size) {
            inv.setItem(nextPageSlot, createGuiItem("gui.items.next-page", Map.of("%page%", String.valueOf(page + 1)), Material.ARROW));
        }

        player.openInventory(inv);
    }

    private HomeManager.Home getHomeAtSlot(List<HomeManager.Home> homes, int slot) {
        for (HomeManager.Home h : homes) {
            if (h.getSlot() == slot) {
                return h;
            }
        }
        return null;
    }

    private ItemStack createGuiItem(String path, Map<String, String> placeholders, Material defaultMaterial) {
        String nameRaw = plugin.getConfig().getString(path + ".name");
        List<String> loreRaw = plugin.getConfig().getStringList(path + ".lore");
        int customModelData = plugin.getConfig().getInt(path + ".custom-model-data", -1);

        ItemStack item = new ItemStack(defaultMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameRaw != null && !nameRaw.isEmpty()) {
                meta.displayName(plugin.parseMiniMessage(nameRaw, placeholders));
            }
            if (loreRaw != null && !loreRaw.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreRaw) {
                    lore.add(plugin.parseMiniMessage(line, placeholders));
                }
                meta.lore(lore);
            }
            if (customModelData != -1) {
                meta.setCustomModelData(customModelData);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------
    // 5.2 — MANAGE HOME
    // ------------------------------------------------------------------
    public void openManageHome(Player player, HomeManager.Home home) {
        int rows = plugin.getConfig().getInt("gui.settings.rows", 3);
        int size = rows * 9;

        String titleStr = plugin.getConfig().getString("gui.stage2.title", "Manage %home%");
        titleStr = titleStr.replace("%home%", home.getName());
        Component title = plugin.parseMiniMessage(titleStr, null);

        Inventory inv = Bukkit.createInventory(new ManageHomeHolder(home), size, title);

        // Put home icon at slot 4
        Material mat;
        try {
            mat = Material.valueOf(home.getIconMaterial().toUpperCase());
        } catch (Exception e) {
            mat = Material.LIME_BED;
        }
        ItemStack iconItem = new ItemStack(mat);
        ItemMeta iconMeta = iconItem.getItemMeta();
        if (iconMeta != null) {
            iconMeta.displayName(plugin.parseMiniMessage("<aqua>⌂ <bold>" + home.getName() + "</bold></aqua>", null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseMiniMessage("<gray>World: <white>" + home.getWorldName() + "</white></gray>", null));
            lore.add(plugin.parseMiniMessage("<gray>Coordinates: <yellow>" + Math.round(home.getX()) + ", " + Math.round(home.getY()) + ", " + Math.round(home.getZ()) + "</yellow></gray>", null));
            iconMeta.lore(lore);
            iconItem.setItemMeta(iconMeta);
        }
        inv.setItem(4, iconItem);

        // Teleport slot
        int tpSlot = plugin.getConfig().getInt("gui.stage2.teleport-slot", 11);
        if (tpSlot >= 0 && tpSlot < size) {
            inv.setItem(tpSlot, createGuiItem("gui.items.teleport", Map.of("%home%", home.getName()), Material.EMERALD_BLOCK));
        }

        // Delete slot
        int delSlot = plugin.getConfig().getInt("gui.stage2.delete-slot", 15);
        if (delSlot >= 0 && delSlot < size) {
            inv.setItem(delSlot, createGuiItem("gui.items.delete", Map.of("%home%", home.getName()), Material.REDSTONE_BLOCK));
        }

        // Change Icon button at slot 13 (middle slot)
        inv.setItem(13, createGuiItem("gui.items.occupied", Map.of("%home%", home.getName(), "%world%", home.getWorldName(), "%x%", String.valueOf(Math.round(home.getX())), "%y%", String.valueOf(Math.round(home.getY())), "%z%", String.valueOf(Math.round(home.getZ()))), mat));
        ItemStack changeIconItem = inv.getItem(13);
        if (changeIconItem != null) {
            ItemMeta m = changeIconItem.getItemMeta();
            if (m != null) {
                m.displayName(plugin.parseMiniMessage("<yellow>Change Icon</yellow>", null));
                m.lore(List.of(plugin.parseMiniMessage("<gray>Click to change the material icon</gray>", null)));
                changeIconItem.setItemMeta(m);
            }
        }

        // Rename button at slot 12
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        if (renameMeta != null) {
            renameMeta.displayName(plugin.parseMiniMessage("<yellow>Rename Home</yellow>", null));
            renameMeta.lore(List.of(plugin.parseMiniMessage("<gray>Click to rename this home</gray>", null)));
            renameItem.setItemMeta(renameMeta);
        }
        inv.setItem(12, renameItem);

        // Back slot
        int backSlot = plugin.getConfig().getInt("gui.stage2.back-slot", 22);
        if (backSlot >= 0 && backSlot < size) {
            inv.setItem(backSlot, createGuiItem("gui.items.back", null, Material.BARRIER));
        }

        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // 5.3 — RENAME / SET HOME
    // ------------------------------------------------------------------
    public void openRenameDialog(Player player, HomeManager.Home existingHomeOrNull) {
        int slot = existingHomeOrNull != null ? existingHomeOrNull.getSlot() : 1;
        openRenameDialog(player, existingHomeOrNull, null, slot);
    }

    public void openRenameDialog(Player player, HomeManager.Home existingHomeOrNull, String initialName) {
        int slot = existingHomeOrNull != null ? existingHomeOrNull.getSlot() : 1;
        openRenameDialog(player, existingHomeOrNull, initialName, slot);
    }

    public void openRenameDialog(Player player, HomeManager.Home existingHomeOrNull, String initialName, int slot) {
        player.closeInventory();
        pendingSetHomeSlot.put(player.getUniqueId(), slot);
        pendingSetHomeExistingHome.put(player.getUniqueId(), existingHomeOrNull);

        if (existingHomeOrNull != null) {
            player.sendMessage(plugin.parseMiniMessage("<yellow>Please type the new name for home <white>" + existingHomeOrNull.getName() + "</white> in chat (or type '<red>cancel</red>' to abort):</yellow>", null));
        } else {
            String placeholderText = initialName != null ? " [default: " + initialName + "]" : "";
            player.sendMessage(plugin.parseMiniMessage("<yellow>Please type a name for your new home in chat (or type '<red>cancel</red>' to abort)" + placeholderText + ":</yellow>", null));
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingSetHomeSlot.containsKey(uuid)) {
            event.setCancelled(true);
            String message = event.getMessage().trim();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Integer slot = pendingSetHomeSlot.remove(uuid);
                HomeManager.Home existingHome = pendingSetHomeExistingHome.remove(uuid);

                if (slot == null) return;

                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(plugin.parseMiniMessage("<red>Set home cancelled.</red>", null));
                    if (existingHome != null) {
                        openManageHome(player, existingHome);
                    } else {
                        openHomeGrid(player);
                    }
                    return;
                }

                if (!message.matches("^[A-Za-z0-9_-]{1,16}$")) {
                    player.sendMessage(plugin.parseMiniMessage("<red>Invalid home name! Only letters, numbers, hyphens, and underscores are allowed (1-16 characters).</red>", null));
                    openRenameDialog(player, existingHome, message, slot);
                    return;
                }

                if (existingHome != null) {
                    // Rename / Update
                    plugin.getHomeManager().deleteHome(uuid, existingHome.getName());
                    plugin.getHomeManager().setHome(uuid, message, existingHome.toLocation(), existingHome.getIconMaterial(), slot);
                    player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + existingHome.getName() + "</white> renamed to <white>" + message + "</white>!</green>", null));
                    HomeManager.Home updated = plugin.getHomeManager().getHome(uuid, message);
                    if (updated != null) {
                        openManageHome(player, updated);
                    } else {
                        openHomeGrid(player);
                    }
                } else {
                    // Create new
                    plugin.getHomeManager().setHome(uuid, message, player.getLocation(), "LIME_BED", slot);
                    player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + message + "</white> set successfully!</green>", null));
                    openHomeGrid(player);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // 5.4 — CONFIRM DELETE
    // ------------------------------------------------------------------
    public void openConfirmDelete(Player player, HomeManager.Home home) {
        int rows = 3;
        int size = rows * 9;

        String titleStr = "Delete " + home.getName() + "?";
        Component title = plugin.parseMiniMessage(titleStr, null);

        Inventory inv = Bukkit.createInventory(new ConfirmDeleteHolder(home), size, title);

        int confirmSlot = plugin.getConfig().getInt("gui.stage3.confirm-slot", 11);
        int cancelSlot = plugin.getConfig().getInt("gui.stage3.cancel-slot", 15);

        if (confirmSlot >= 0 && confirmSlot < size) {
            inv.setItem(confirmSlot, createGuiItem("gui.items.delete", Map.of("%home%", home.getName()), Material.REDSTONE_BLOCK));
        }
        if (cancelSlot >= 0 && cancelSlot < size) {
            inv.setItem(cancelSlot, createGuiItem("gui.items.cancel", null, Material.BARRIER));
        }

        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // 5.5 — LOCKED NOTICE
    // ------------------------------------------------------------------
    public void openLockedNotice(Player player) {
        player.sendMessage(plugin.parseMiniMessage(plugin.getFormattedMessage("messages.locked-slot-clicked"), null));
    }

    // ------------------------------------------------------------------
    // 5.6 — ICON PICKER
    // ------------------------------------------------------------------
    public void openIconPicker(Player player, HomeManager.Home home) {
        List<Material> materials = Arrays.asList(
                Material.LIME_BED, Material.RED_BED, Material.BLUE_BED,
                Material.GRASS_BLOCK, Material.DIAMOND, Material.GOLD_INGOT,
                Material.IRON_SWORD, Material.BOW, Material.SHIELD,
                Material.CHEST, Material.CRAFTING_TABLE, Material.FURNACE,
                Material.BOOKSHELF, Material.ENCHANTING_TABLE, Material.ENDER_CHEST,
                Material.CAMPFIRE, Material.TORCH, Material.LANTERN,
                Material.OAK_LOG, Material.COBBLESTONE, Material.SAND,
                Material.REDSTONE, Material.TNT, Material.SLIME_BALL
        );

        int size = 36; // 4 rows
        Component title = plugin.parseMiniMessage("Choose Icon for " + home.getName(), null);
        Inventory inv = Bukkit.createInventory(new IconPickerHolder(home), size, title);

        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.parseMiniMessage("<white>" + formatMaterialName(material) + "</white>", null));
                meta.lore(List.of(plugin.parseMiniMessage("<yellow>Click to set as icon</yellow>", null)));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        inv.setItem(35, createGuiItem("gui.items.back", null, Material.BARRIER));

        player.openInventory(inv);
    }

    private String formatMaterialName(Material material) {
        String[] words = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase())
              .append(word.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;

        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof HomeGridHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= event.getInventory().getSize()) return;

            HomeGridHolder gridHolder = (HomeGridHolder) holder;
            int page = gridHolder.getPage();

            int rows = plugin.getConfig().getInt("gui.settings.rows", 3);
            int size = rows * 9;
            int prevPageSlot = plugin.getConfig().getInt("gui.settings.prev-page-slot", 18);
            int nextPageSlot = plugin.getConfig().getInt("gui.settings.next-page-slot", 26);

            int slotClicked = event.getRawSlot();

            if (slotClicked == prevPageSlot && page > 1) {
                openHomeGrid(player, page - 1);
                return;
            }

            List<Integer> buttonSlots = plugin.getConfig().getIntegerList("gui.settings.button-slots");
            if (buttonSlots == null || buttonSlots.isEmpty()) {
                buttonSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16);
            }
            int slotsPerPage = buttonSlots.size();

            if (slotClicked == nextPageSlot) {
                if ((page * slotsPerPage) < 50) {
                    openHomeGrid(player, page + 1);
                }
                return;
            }

            int buttonIndex = buttonSlots.indexOf(slotClicked);
            if (buttonIndex != -1) {
                int homeSlotIndex = (page - 1) * slotsPerPage + buttonIndex + 1;
                if (hasSlotPermission(player, homeSlotIndex)) {
                    List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
                    HomeManager.Home home = getHomeAtSlot(homes, homeSlotIndex);
                    if (home != null) {
                        openManageHome(player, home);
                    } else {
                        openRenameDialog(player, null, null, homeSlotIndex);
                    }
                } else {
                    openLockedNotice(player);
                }
            }
        } else if (holder instanceof ManageHomeHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= event.getInventory().getSize()) return;

            ManageHomeHolder manageHolder = (ManageHomeHolder) holder;
            HomeManager.Home home = manageHolder.getHome();

            int slotClicked = event.getRawSlot();
            int tpSlot = plugin.getConfig().getInt("gui.stage2.teleport-slot", 11);
            int delSlot = plugin.getConfig().getInt("gui.stage2.delete-slot", 15);
            int backSlot = plugin.getConfig().getInt("gui.stage2.back-slot", 22);

            if (slotClicked == tpSlot) {
                player.closeInventory();
                player.performCommand("hometp " + home.getName());
            } else if (slotClicked == delSlot) {
                openConfirmDelete(player, home);
            } else if (slotClicked == 13) {
                openIconPicker(player, home);
            } else if (slotClicked == 12) {
                openRenameDialog(player, home);
            } else if (slotClicked == backSlot) {
                openHomeGrid(player);
            }
        } else if (holder instanceof ConfirmDeleteHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= event.getInventory().getSize()) return;

            ConfirmDeleteHolder deleteHolder = (ConfirmDeleteHolder) holder;
            HomeManager.Home home = deleteHolder.getHome();

            int slotClicked = event.getRawSlot();
            int confirmSlot = plugin.getConfig().getInt("gui.stage3.confirm-slot", 11);
            int cancelSlot = plugin.getConfig().getInt("gui.stage3.cancel-slot", 15);

            if (slotClicked == confirmSlot) {
                plugin.getHomeManager().deleteHome(player.getUniqueId(), home.getName());
                player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + home.getName() + "</white> deleted.</green>", null));
                openHomeGrid(player);
            } else if (slotClicked == cancelSlot) {
                openManageHome(player, home);
            }
        } else if (holder instanceof IconPickerHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= event.getInventory().getSize()) return;

            IconPickerHolder pickerHolder = (IconPickerHolder) holder;
            HomeManager.Home home = pickerHolder.getHome();

            int slotClicked = event.getRawSlot();
            if (slotClicked == 35) {
                openManageHome(player, home);
                return;
            }

            List<Material> materials = Arrays.asList(
                    Material.LIME_BED, Material.RED_BED, Material.BLUE_BED,
                    Material.GRASS_BLOCK, Material.DIAMOND, Material.GOLD_INGOT,
                    Material.IRON_SWORD, Material.BOW, Material.SHIELD,
                    Material.CHEST, Material.CRAFTING_TABLE, Material.FURNACE,
                    Material.BOOKSHELF, Material.ENCHANTING_TABLE, Material.ENDER_CHEST,
                    Material.CAMPFIRE, Material.TORCH, Material.LANTERN,
                    Material.OAK_LOG, Material.COBBLESTONE, Material.SAND,
                    Material.REDSTONE, Material.TNT, Material.SLIME_BALL
            );

            if (slotClicked >= 0 && slotClicked < materials.size()) {
                Material selectedMaterial = materials.get(slotClicked);
                plugin.getHomeManager().setHome(player.getUniqueId(), home.getName(), home.toLocation(), selectedMaterial.name(), home.getSlot());
                player.sendMessage(plugin.parseMiniMessage("<green>Icon for home <white>" + home.getName() + "</white> set to <white>" + formatMaterialName(selectedMaterial) + "</white>!</green>", null));
                HomeManager.Home updated = plugin.getHomeManager().getHome(player.getUniqueId(), home.getName());
                if (updated != null) {
                    openManageHome(player, updated);
                } else {
                    openHomeGrid(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof HomeGridHolder || holder instanceof ManageHomeHolder || holder instanceof ConfirmDeleteHolder || holder instanceof IconPickerHolder) {
            event.setCancelled(true);
        }
    }
}
