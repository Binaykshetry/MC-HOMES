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

        public Home(String name, String worldName, double x, double y, double z, float yaw, float pitch) {
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
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

                Home home = new Home(homeName, worldName, x, y, z, yaw, pitch);
                homesMap.put(homeName.toLowerCase(), home);
            }
            userHomes.put(uuid, homesMap);
        }
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
        LinkedHashMap<String, Home> homesMap = userHomes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        Home home = new Home(homeName, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        
        // Remove old entry to handle case updates and maintain position, or replace in-place
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
}
