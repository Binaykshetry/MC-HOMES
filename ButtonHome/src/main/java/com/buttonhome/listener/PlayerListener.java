package com.buttonhome.listener;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.GuiManager;
import com.buttonhome.manager.HomeManager;
import com.buttonhome.manager.TeleportManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PlayerListener implements Listener {

    private final ButtonHome plugin;

    public PlayerListener(ButtonHome plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getInventory().getHolder() instanceof GuiManager.HomeGuiHolder) {
            event.setCancelled(true);

            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || !currentItem.hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            GuiManager guiManager = plugin.getGuiManager();
            PersistentDataContainer pdc = currentItem.getItemMeta().getPersistentDataContainer();

            String buttonType = pdc.get(guiManager.getButtonTypeKey(), PersistentDataType.STRING);
            String buttonData = pdc.get(guiManager.getButtonDataKey(), PersistentDataType.STRING);

            if (buttonType == null) return;

            switch (buttonType) {
                case "select_home":
                    if (buttonData != null) {
                        HomeManager.Home home = plugin.getHomeManager().getHome(player.getUniqueId(), buttonData);
                        if (home != null) {
                            guiManager.openStage2Gui(player, home);
                        }
                    }
                    break;
                case "new_home":
                    if (buttonData != null) {
                        guiManager.openStage3Gui(player, buttonData);
                    }
                    break;
                case "tp":
                    if (buttonData != null) {
                        player.closeInventory();
                        player.performCommand("hometp " + buttonData);
                    }
                    break;
                case "delete":
                    if (buttonData != null) {
                        player.closeInventory();
                        player.performCommand("delhome " + buttonData);
                    }
                    break;
                case "confirm_set":
                    if (buttonData != null) {
                        player.closeInventory();
                        player.performCommand("sethome " + buttonData);
                    }
                    break;
                case "show_more":
                    guiManager.openGridGui(player, true);
                    break;
                case "back":
                    guiManager.openGridGui(player, false);
                    break;
                case "locked":
                    player.performCommand("buttonhome locked");
                    break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TeleportManager teleportManager = plugin.getTeleportManager();

        if (teleportManager.hasPending(player.getUniqueId())) {
            // Check if player's block position changed (ignore head rotation/yaw/pitch shifts)
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

                if (plugin.getConfig().getBoolean("cancel-on-move", true)) {
                    teleportManager.cancelTeleport(player.getUniqueId(), TeleportManager.CancelReason.MOVE);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            TeleportManager teleportManager = plugin.getTeleportManager();

            if (teleportManager.hasPending(player.getUniqueId())) {
                if (plugin.getConfig().getBoolean("cancel-on-damage", true)) {
                    teleportManager.cancelTeleport(player.getUniqueId(), TeleportManager.CancelReason.DAMAGE);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Silently cancel and cleanup pending teleport scheduler tasks for logging out players
        plugin.getTeleportManager().cancelTeleport(event.getPlayer().getUniqueId(), TeleportManager.CancelReason.QUIT);
    }
}
