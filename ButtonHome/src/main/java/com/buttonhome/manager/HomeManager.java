package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class HomeManager {

    public static class Home {
        private final String name;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final String iconMaterial;
        private final int slot;

        public Home(String name, String worldName, double x, double y, double z, float yaw, float pitch) {
            this(name, worldName, x, y, z, yaw, pitch, "LIME_BED", 1);
        }

        public Home(String name, String worldName, double x, double y, double z, float yaw, float pitch, String iconMaterial) {
            this(name, worldName, x, y, z, yaw, pitch, iconMaterial, 1);
        }

        public Home(String name, String worldName, double x, double y, double z, float yaw, float pitch, String iconMaterial, int slot) {
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.iconMaterial = iconMaterial != null ? iconMaterial : "LIME_BED";
            this.slot = slot;
        }

        public String getName() {
            return name;
        }

        public String getWorldName() {
            return worldName;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public String getIconMaterial() {
            return iconMaterial;
        }

        public int getSlot() {
            return slot;
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private final ButtonHome plugin;
    private final File homesFile;
    private final Map<UUID, LinkedHashMap<String, Home>> userHomes;

    public HomeManager(ButtonHome plugin) {
        this.plugin = plugin;
        this.homesFile = new File(plugin.getDataFolder(), "homes.yml");
        this.userHomes = new HashMap<>();
        loadHomes();
    }

    /**
     * Load homes from homes.yml into memory.
     */
    public void loadHomes() {
        userHomes.clear();
        if (!homesFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(homesFile);
        for (String uuidStr : config.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid UUID key in homes.yml: " + uuidStr);
                continue;
            }

            ConfigurationSection playerSection = config.getConfigurationSection(uuidStr);
            if (playerSection == null) {
                continue;
            }

            LinkedHashMap<String, Home> homesMap = new LinkedHashMap<>();
            int nextAutoSlot = 1;
            for (String homeName : playerSection.getKeys(false)) {
                ConfigurationSection homeSection = playerSection.getConfigurationSection(homeName);
                if (homeSection == null) {
                    continue;
                }

                String worldName = homeSection.getString("world");
                if (worldName == null) {
                    plugin.getLogger().warning("Skipping invalid home entry '" + homeName + "' for UUID " + uuidStr + " (missing world).");
                    continue;
                }

                double x = homeSection.getDouble("x");
                double y = homeSection.getDouble("y");
                double z = homeSection.getDouble("z");
                float yaw = (float) homeSection.getDouble("yaw");
                float pitch = (float) homeSection.getDouble("pitch");
                String iconMaterial = homeSection.getString("icon", "LIME_BED");
                int slot = homeSection.getInt("slot", 0);

                if (slot <= 0 || slot > 50) {
                    slot = nextAutoSlot;
                    while (hasHomeAtSlot(homesMap, slot) && slot <= 50) {
                        slot++;
                    }
                    nextAutoSlot = slot + 1;
                }

                Home home = new Home(homeName, worldName, x, y, z, yaw, pitch, iconMaterial, slot);
                homesMap.put(homeName.toLowerCase(), home);
            }
            userHomes.put(uuid, homesMap);
        }
    }

    private boolean hasHomeAtSlot(Map<String, Home> map, int slot) {
        for (Home h : map.values()) {
            if (h.getSlot() == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Save homes from memory to homes.yml.
     */
    public void saveHomes() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, LinkedHashMap<String, Home>> entry : userHomes.entrySet()) {
            String uuidStr = entry.getKey().toString();
            LinkedHashMap<String, Home> homesMap = entry.getValue();

            ConfigurationSection playerSection = config.createSection(uuidStr);
            for (Home home : homesMap.values()) {
                ConfigurationSection homeSection = playerSection.createSection(home.getName());
                homeSection.set("world", home.getWorldName());
                homeSection.set("x", home.getX());
                homeSection.set("y", home.getY());
                homeSection.set("z", home.getZ());
                homeSection.set("yaw", home.getYaw());
                homeSection.set("pitch", home.getPitch());
                homeSection.set("icon", home.getIconMaterial());
                homeSection.set("slot", home.getSlot());
            }
        }

        try {
            config.save(homesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save homes.yml", e);
        }
    }

    /**
     * Get all homes for a player, preserving insertion order.
     */
    public List<Home> getHomes(UUID uuid) {
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        if (homesMap == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(homesMap.values());
    }

    /**
     * Get a specific home for a player, case-insensitively.
     */
    public Home getHome(UUID uuid, String homeName) {
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        if (homesMap == null) {
            return null;
        }
        return homesMap.get(homeName.toLowerCase());
    }

    /**
     * Add or update a home for a player. Saves to disk immediately.
     */
    public void setHome(UUID uuid, String homeName, Location loc) {
        int slot = 1;
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        if (homesMap != null) {
            for (int i = 1; i <= 50; i++) {
                if (!hasHomeAtSlot(homesMap, i)) {
                    slot = i;
                    break;
                }
            }
        }
        setHome(uuid, homeName, loc, null, slot);
    }

    /**
     * Add or update a home for a player with a specific icon material.
     */
    public void setHome(UUID uuid, String homeName, Location loc, String iconMaterial) {
        int slot = 1;
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        if (homesMap != null) {
            Home oldHome = homesMap.get(homeName.toLowerCase());
            if (oldHome != null) {
                slot = oldHome.getSlot();
            } else {
                for (int i = 1; i <= 50; i++) {
                    if (!hasHomeAtSlot(homesMap, i)) {
                        slot = i;
                        break;
                    }
                }
            }
        }
        setHome(uuid, homeName, loc, iconMaterial, slot);
    }

    /**
     * Add or update a home for a player with a specific icon material and slot.
     */
    public void setHome(UUID uuid, String homeName, Location loc, String iconMaterial, int slot) {
        LinkedHashMap<String, Home> homesMap = userHomes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        Home oldHome = homesMap.get(homeName.toLowerCase());
        
        // Remove any home already occupying this slot for this player to avoid slot conflict
        Home homeToReplace = null;
        for (Home h : homesMap.values()) {
            if (h.getSlot() == slot) {
                homeToReplace = h;
                break;
            }
        }
        if (homeToReplace != null) {
            homesMap.remove(homeToReplace.getName().toLowerCase());
        }

        String materialToUse = iconMaterial != null ? iconMaterial : (oldHome != null ? oldHome.getIconMaterial() : "LIME_BED");
        Home home = new Home(homeName, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), materialToUse, slot);
        
        homesMap.put(homeName.toLowerCase(), home);
        saveHomes();
    }

    /**
     * Delete a home for a player. Saves to disk immediately.
     */
    public boolean deleteHome(UUID uuid, String homeName) {
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        if (homesMap == null) {
            return false;
        }

        Home removed = homesMap.remove(homeName.toLowerCase());
        if (removed != null) {
            if (homesMap.isEmpty()) {
                userHomes.remove(uuid);
            }
            saveHomes();
            return true;
        }
        return false;
    }

    /**
     * Check how many homes a player currently has.
     */
    public int getHomeCount(UUID uuid) {
        LinkedHashMap<String, Home> homesMap = userHomes.get(uuid);
        return homesMap == null ? 0 : homesMap.size();
    }

    /**
     * Get the first available (unlocked & empty) slot for a player.
     */
    public int getFirstAvailableSlot(org.bukkit.entity.Player player) {
        LinkedHashMap<String, Home> homesMap = userHomes.get(player.getUniqueId());
        for (int i = 1; i <= 50; i++) {
            // Check if player has permission for slot i
            if (player.hasPermission("buttonhome." + i) || player.hasPermission("buttonhome.admin") || player.hasPermission("homebutton.admin") || player.isOp()) {
                // Check if slot is empty
                if (homesMap == null || !hasHomeAtSlot(homesMap, i)) {
                    return i;
                }
            }
        }
        return -1; // No empty unlocked slot found
    }
}
