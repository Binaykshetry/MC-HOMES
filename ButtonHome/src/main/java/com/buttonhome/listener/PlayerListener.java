package com.buttonhome.listener;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.TeleportManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ButtonHome plugin;

    public PlayerListener(ButtonHome plugin) {
        this.plugin = plugin;
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
