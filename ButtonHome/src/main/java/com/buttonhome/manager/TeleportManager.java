package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    public enum CancelReason {
        MOVE,
        DAMAGE,
        MANUAL,
        QUIT
    }

    private static class PendingTeleport {
        private final String homeName;
        private final Location originBlock;
        private final Location destination;
        private final BukkitTask task;

        public PendingTeleport(String homeName, Location originBlock, Location destination, BukkitTask task) {
            this.homeName = homeName;
            this.originBlock = originBlock;
            this.destination = destination;
            this.task = task;
        }

        public String getHomeName() {
            return homeName;
        }

        public Location getOriginBlock() {
            return originBlock;
        }

        public Location getDestination() {
            return destination;
        }

        public BukkitTask getTask() {
            return task;
        }
    }

    private final ButtonHome plugin;
    private final Map<UUID, PendingTeleport> pendingTeleports;
    private final Map<UUID, Long> cooldowns; // UUID -> expiration timestamp in ms

    public TeleportManager(ButtonHome plugin) {
        this.plugin = plugin;
        this.pendingTeleports = new HashMap<>();
        this.cooldowns = new HashMap<>();
    }

    /**
     * Start the teleport process for a player to a specific home.
     */
    public void startTeleport(Player player, String homeName, Location destination) {
        UUID uuid = player.getUniqueId();
        boolean isAdmin = player.hasPermission("homebutton.admin") || player.hasPermission("buttonhome.admin") || player.isOp();

        // 1. Check for cooldown
        if (!isAdmin) {
            long remainingMs = getRemainingCooldown(uuid);
            if (remainingMs > 0) {
                double remainingSeconds = remainingMs / 1000.0;
                String formatSeconds = String.format("%.1f", remainingSeconds);
                plugin.sendConfigMessage(player, "messages.on-cooldown", Map.of("%seconds%", formatSeconds));
                return;
            }
        }

        // 2. Check for bypass / instant teleport
        int warmupSeconds = plugin.getConfig().getInt("warmup-seconds", 5);
        if (isAdmin || warmupSeconds <= 0) {
            executeTeleportDirectly(player, homeName, destination);
            return;
        }

        // 3. Cancel any existing pending teleport first
        if (pendingTeleports.containsKey(uuid)) {
            cancelTeleport(uuid, CancelReason.MANUAL);
        }

        // 4. Register block-level origin location
        Location originBlock = player.getLocation().getBlock().getLocation();

        // 5. Send starting message
        plugin.sendConfigMessage(player, "messages.warmup-start", Map.of(
                "%home%", homeName,
                "%seconds%", String.valueOf(warmupSeconds)
        ));

        // 6. Schedule task
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport pending = pendingTeleports.remove(uuid);
            if (pending != null) {
                // Ensure player is online and valid
                if (player.isOnline()) {
                    executeTeleportDirectly(player, pending.getHomeName(), pending.getDestination());
                }
            }
        }, warmupSeconds * 20L);

        pendingTeleports.put(uuid, new PendingTeleport(homeName, originBlock, destination, task));
    }

    /**
     * Execute the teleportation immediately, update cooldown on success.
     */
    private void executeTeleportDirectly(Player player, String homeName, Location destination) {
        // Use teleportAsync for optimized Paper async teleportation
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                plugin.sendConfigMessage(player, "messages.teleport-success", Map.of("%home%", homeName));

                // Apply cooldown if not admin
                boolean isAdmin = player.hasPermission("homebutton.admin") || player.hasPermission("buttonhome.admin") || player.isOp();
                if (!isAdmin) {
                    int cooldownSeconds = plugin.getConfig().getInt("teleport-cooldown-seconds", 0);
                    if (cooldownSeconds > 0) {
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
                    }
                }
            }
        });
    }

    /**
     * Cancel a player's pending teleport.
     */
    public boolean cancelTeleport(UUID uuid, CancelReason reason) {
        PendingTeleport pending = pendingTeleports.remove(uuid);
        if (pending == null) {
            return false;
        }

        pending.getTask().cancel();

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline() && reason != CancelReason.QUIT) {
            String messageKey;
            switch (reason) {
                case MOVE:
                    messageKey = "messages.warmup-cancelled-move";
                    break;
                case DAMAGE:
                    messageKey = "messages.warmup-cancelled-damage";
                    break;
                case MANUAL:
                default:
                    messageKey = "messages.warmup-cancelled-manual";
                    break;
            }
            plugin.sendConfigMessage(player, messageKey, null);
        }

        return true;
    }

    /**
     * Get remaining cooldown time in milliseconds. Returns 0 if none.
     */
    public long getRemainingCooldown(UUID uuid) {
        Long expiration = cooldowns.get(uuid);
        if (expiration == null) {
            return 0;
        }
        long remaining = expiration - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(uuid);
            return 0;
        }
        return remaining;
    }

    /**
     * Check if a player has an active pending teleport.
     */
    public boolean hasPending(UUID uuid) {
        return pendingTeleports.containsKey(uuid);
    }

    /**
     * Check if a player moved block-level from their starting warmup position.
     */
    public void checkMovement(Player player) {
        UUID uuid = player.getUniqueId();
        PendingTeleport pending = pendingTeleports.get(uuid);
        if (pending == null) {
            return;
        }

        Location currentBlock = player.getLocation().getBlock().getLocation();
        if (!currentBlock.equals(pending.getOriginBlock())) {
            cancelTeleport(uuid, CancelReason.MOVE);
        }
    }
}
