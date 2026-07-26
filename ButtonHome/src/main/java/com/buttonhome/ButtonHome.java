package com.buttonhome;

import com.buttonhome.command.HomeCommand;
import com.buttonhome.command.SetHomeCommand;
import com.buttonhome.command.DelHomeCommand;
import com.buttonhome.command.AdminHomeCommand;
import com.buttonhome.command.InternalCommands;
import com.buttonhome.listener.PlayerListener;
import com.buttonhome.manager.HomeManager;
import com.buttonhome.manager.TeleportManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class ButtonHome extends JavaPlugin {

    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private com.buttonhome.manager.GuiManager guiManager;

    @Override
    public void onEnable() {
        // 1. Save default config
        saveDefaultConfig();

        // 2. Initialize Managers
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this);
        this.guiManager = new com.buttonhome.manager.GuiManager(this);

        // 3. Register Listener
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // 4. Register Commands
        registerCommand("home", new HomeCommand(this));
        registerCommand("homes", new HomeCommand(this)); // Handled inside HomeCommand
        registerCommand("sethome", new SetHomeCommand(this));
        registerCommand("delhome", new DelHomeCommand(this));
        registerCommand("adminhome", new AdminHomeCommand(this));
        registerCommand("homeselect", new InternalCommands(this));
        registerCommand("homesetprompt", new InternalCommands(this));
        registerCommand("hometp", new InternalCommands(this));
        registerCommand("homecancel", new InternalCommands(this));
        registerCommand("buttonhome", new InternalCommands(this));

        getLogger().info("ButtonHome plugin successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Save homes on shutdown to prevent any data loss
        if (homeManager != null) {
            homeManager.saveHomes();
        }
        getLogger().info("ButtonHome plugin successfully disabled!");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter) {
                getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        }
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public com.buttonhome.manager.GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Get a raw config message by path. Falls back to a default value if missing.
     */
    public String getFormattedMessage(String path) {
        String msg = getConfig().getString(path);
        return msg != null ? msg : "";
    }

    /**
     * Translate legacy color codes (e.g. &a, &l) and legacy RGB/hex codes (e.g. &#ffaa00) to MiniMessage tags.
     */
    public String translateAlternateColorCodes(String text) {
        if (text == null) {
            return null;
        }

        // 1. Convert hex legacy colors like &#ffaa00 or &#FFAA00 to <#ffaa00>
        text = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        text = text.replaceAll("&x&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])", "<#$1$2$3$4$5$6>");

        // 2. Map standard legacy color codes
        String[] legacyCodes = {"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f", "&k", "&l", "&m", "&n", "&o", "&r"};
        String[] miniTags = {"<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>", "<dark_red>", "<dark_purple>", "<gold>", "<gray>", "<dark_gray>", "<blue>", "<green>", "<aqua>", "<red>", "<light_purple>", "<yellow>", "<white>", "<obfuscated>", "<bold>", "<strikethrough>", "<underline>", "<italic>", "<reset>"};

        for (int i = 0; i < legacyCodes.length; i++) {
            text = text.replace(legacyCodes[i], miniTags[i]);
            text = text.replace(legacyCodes[i].toUpperCase(), miniTags[i]);
        }

        return text;
    }

    /**
     * Send a MiniMessage-formatted config message with replaced placeholders.
     */
    public void sendConfigMessage(Player player, String path, Map<String, String> placeholders) {
        String raw = getConfig().getString(path);
        if (raw == null || raw.isEmpty()) {
            return;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace(entry.getKey(), entry.getValue());
            }
        }

        raw = translateAlternateColorCodes(raw);

        // Deserialize using Adventure's MiniMessage
        Component message = MiniMessage.miniMessage().deserialize(raw);
        player.sendMessage(message);
    }

    /**
     * Parse and deserialize a single raw MiniMessage string directly.
     */
    public Component parseMiniMessage(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return Component.empty();
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace(entry.getKey(), entry.getValue());
            }
        }
        raw = translateAlternateColorCodes(raw);
        return MiniMessage.miniMessage().deserialize(raw);
    }

    /**
     * Dynamically calculate a player's maximum allowed homes based on wildcard permissions.
     */
    public int getMaxHomes(Player player) {
        if (player.hasPermission("homebutton.admin") || player.hasPermission("buttonhome.admin") || player.isOp()) {
            return 50; // Max allowed/supported is 50
        }

        // Loop from 50 down to 1 to find the highest permission
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("homebutton." + i)) {
                return i;
            }
        }

        // Also check buttonhome.limit.<number> as a fallback or default-max-homes from config
        int limit = getConfig().getInt("default-max-homes", 2);
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission();
            if (perm.startsWith("buttonhome.limit.")) {
                String limitStr = perm.substring("buttonhome.limit.".length());
                try {
                    int permLimit = Integer.parseInt(limitStr);
                    if (permLimit > limit) {
                        limit = permLimit;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.min(50, limit);
    }
}
