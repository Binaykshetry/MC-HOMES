package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.util.FormImage;

import java.util.List;
import java.util.UUID;

public class GeyserFormHandler {

    private final ButtonHome plugin;

    public GeyserFormHandler(ButtonHome plugin) {
        this.plugin = plugin;
    }

    public boolean isBedrock(Player player) {
        try {
            return GeyserApi.api().isBedrockPlayer(player.getUniqueId());
        } catch (Throwable e) {
            return false;
        }
    }

    public void openHomeGrid(Player player) {
        UUID uuid = player.getUniqueId();
        List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(uuid);
        int limit = plugin.getMaxHomes(player);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Homes")
                .content("Select a home slot to manage or teleport:");

        // Add 50 buttons (supporting up to 50 slots/homes maximum as shown in the grid image)
        for (int i = 1; i <= 50; i++) {
            HomeManager.Home home = getHomeAtSlot(homes, i);
            if (i <= limit) {
                if (home != null) {
                    // Occupied
                    String iconPath = getGeyserIconPath(home.getIconMaterial());
                    builder.button(home.getName() + "\n§aClick to manage", FormImage.Type.PATH, iconPath);
                } else {
                    // Empty / Set
                    builder.button("New Home\n§e+ Click to set", FormImage.Type.PATH, "textures/items/paper");
                }
            } else {
                // Locked
                builder.button("Locked\n§cRequires permission", FormImage.Type.PATH, "textures/blocks/barrier");
            }
        }

        builder.validResultHandler(response -> {
            int slotClickedIndex = response.clickedButtonId();
            int homeSlot = slotClickedIndex + 1;

            if (homeSlot > limit) {
                player.sendMessage(plugin.parseMiniMessage(plugin.getFormattedMessage("messages.locked-slot-clicked"), null));
                return;
            }

            HomeManager.Home clickedHome = getHomeAtSlot(homes, homeSlot);
            if (clickedHome != null) {
                // Open Manage Home
                openManageHome(player, clickedHome);
            } else {
                // Set New Home
                openRenameDialog(player, null, "home_" + homeSlot, homeSlot);
            }
        });

        GeyserApi.api().sendForm(uuid, builder);
    }

    public void openManageHome(Player player, HomeManager.Home home) {
        String iconPath = getGeyserIconPath(home.getIconMaterial());
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(home.getName())
                .content("Manage Home " + home.getName() + " (Slot " + home.getSlot() + ")\n" +
                        "World: " + home.getWorldName() + "\n" +
                        "Coordinates: " + Math.round(home.getX()) + ", " + Math.round(home.getY()) + ", " + Math.round(home.getZ()))
                .button("Teleport", FormImage.Type.PATH, "textures/items/emerald")
                .button("Change Icon", FormImage.Type.PATH, iconPath)
                .button("Rename", FormImage.Type.PATH, "textures/items/name_tag")
                .button("Delete", FormImage.Type.PATH, "textures/blocks/redstone_block")
                .button("Back", FormImage.Type.PATH, "textures/blocks/barrier");

        builder.validResultHandler(response -> {
            int clickedId = response.clickedButtonId();
            switch (clickedId) {
                case 0: // Teleport
                    player.performCommand("hometp " + home.getName());
                    break;
                case 1: // Change Icon
                    openIconPicker(player, home);
                    break;
                case 2: // Rename
                    openRenameDialog(player, home, home.getName(), home.getSlot());
                    break;
                case 3: // Delete
                    openConfirmDelete(player, home);
                    break;
                case 4: // Back
                    openHomeGrid(player);
                    break;
            }
        });

        GeyserApi.api().sendForm(player.getUniqueId(), builder);
    }

    public void openConfirmDelete(Player player, HomeManager.Home home) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Delete " + home.getName() + "?")
                .content("Are you absolutely sure you want to delete home " + home.getName() + "? This action is irreversible.")
                .button("Delete Home", FormImage.Type.PATH, "textures/blocks/redstone_block")
                .button("Cancel", FormImage.Type.PATH, "textures/blocks/barrier");

        builder.validResultHandler(response -> {
            int clickedId = response.clickedButtonId();
            if (clickedId == 0) {
                plugin.getHomeManager().deleteHome(player.getUniqueId(), home.getName());
                player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + home.getName() + "</white> deleted.</green>", null));
                openHomeGrid(player);
            } else {
                openManageHome(player, home);
            }
        });

        GeyserApi.api().sendForm(player.getUniqueId(), builder);
    }

    public void openIconPicker(Player player, HomeManager.Home home) {
        List<String> materials = List.of(
                "LIME_BED", "RED_BED", "BLUE_BED",
                "GRASS_BLOCK", "DIAMOND", "GOLD_INGOT",
                "IRON_SWORD", "BOW", "SHIELD",
                "CHEST", "CRAFTING_TABLE", "FURNACE",
                "BOOKSHELF", "ENCHANTING_TABLE", "ENDER_CHEST",
                "CAMPFIRE", "TORCH", "LANTERN",
                "OAK_LOG", "COBBLESTONE", "SAND",
                "REDSTONE", "TNT", "SLIME_BALL"
        );

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Choose Icon for " + home.getName())
                .content("Select a new icon for your home:");

        for (String mat : materials) {
            builder.button(formatMaterialName(mat), FormImage.Type.PATH, getGeyserIconPath(mat));
        }
        builder.button("Back", FormImage.Type.PATH, "textures/blocks/barrier");

        builder.validResultHandler(response -> {
            int clickedId = response.clickedButtonId();
            if (clickedId == materials.size()) {
                openManageHome(player, home);
                return;
            }

            String selectedMaterial = materials.get(clickedId);
            plugin.getHomeManager().setHome(player.getUniqueId(), home.getName(), home.toLocation(), selectedMaterial, home.getSlot());
            player.sendMessage(plugin.parseMiniMessage("<green>Icon for home <white>" + home.getName() + "</white> set to <white>" + formatMaterialName(selectedMaterial) + "</white>!</green>", null));
            HomeManager.Home updated = plugin.getHomeManager().getHome(player.getUniqueId(), home.getName());
            if (updated != null) {
                openManageHome(player, updated);
            } else {
                openHomeGrid(player);
            }
        });

        GeyserApi.api().sendForm(player.getUniqueId(), builder);
    }

    private HomeManager.Home getHomeAtSlot(List<HomeManager.Home> homes, int slot) {
        for (HomeManager.Home h : homes) {
            if (h.getSlot() == slot) {
                return h;
            }
        }
        return null;
    }

    private String getGeyserIconPath(String material) {
        String matLower = material.toLowerCase();
        if (matLower.contains("bed")) {
            if (matLower.equals("lime_bed")) return "textures/items/bed_lime";
            if (matLower.equals("red_bed")) return "textures/items/bed_red";
            if (matLower.equals("blue_bed")) return "textures/items/bed_blue";
            return "textures/items/bed_lime";
        }
        if (matLower.equals("grass_block")) return "textures/blocks/grass_side_carried";
        if (matLower.equals("diamond")) return "textures/items/diamond";
        if (matLower.equals("gold_ingot")) return "textures/items/gold_ingot";
        if (matLower.equals("iron_sword")) return "textures/items/iron_sword";
        if (matLower.equals("bow")) return "textures/items/bow_standby";
        if (matLower.equals("shield")) return "textures/items/shield";
        if (matLower.equals("chest")) return "textures/blocks/chest_front_double";
        if (matLower.equals("crafting_table")) return "textures/blocks/crafting_table_side";
        if (matLower.equals("furnace")) return "textures/blocks/furnace_front_off";
        if (matLower.equals("bookshelf")) return "textures/blocks/bookshelf";
        if (matLower.equals("enchanting_table")) return "textures/blocks/enchanting_table_side";
        if (matLower.equals("ender_chest")) return "textures/blocks/ender_chest_front";
        if (matLower.equals("campfire")) return "textures/items/campfire";
        if (matLower.equals("torch")) return "textures/blocks/torch_on";
        if (matLower.equals("lantern")) return "textures/items/lantern";
        if (matLower.equals("oak_log")) return "textures/blocks/log_oak";
        if (matLower.equals("cobblestone")) return "textures/blocks/cobblestone";
        if (matLower.equals("sand")) return "textures/blocks/sand";
        if (matLower.equals("redstone")) return "textures/items/redstone_dust";
        if (matLower.equals("tnt")) return "textures/blocks/tnt_side";
        if (matLower.equals("slime_ball")) return "textures/items/slimeball";
        return "textures/items/bed_lime";
    }

    private String formatMaterialName(String material) {
        String[] words = material.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase())
              .append(word.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }

    public void openRenameDialog(Player player, HomeManager.Home existingHomeOrNull, String initialName, int slot) {
        String title = existingHomeOrNull != null ? "Rename " + existingHomeOrNull.getName() : "Set New Home";
        String placeholder = initialName != null ? initialName : (existingHomeOrNull != null ? existingHomeOrNull.getName() : "home_" + slot);
        String defaultVal = placeholder;

        CustomForm.Builder builder = CustomForm.builder()
                .title(title)
                .input("Enter a custom name for this home location (1-16 characters):", placeholder, defaultVal);

        builder.validResultHandler(response -> {
            String message = response.next();
            if (message == null) message = "";
            message = message.trim();

            if (message.isEmpty()) {
                player.sendMessage(plugin.parseMiniMessage("<red>Home name cannot be empty!</red>", null));
                openRenameDialog(player, existingHomeOrNull, initialName, slot);
                return;
            }

            if (!message.matches("^[A-Za-z0-9_-]{1,16}$")) {
                player.sendMessage(plugin.parseMiniMessage("<red>Invalid home name! Only letters, numbers, hyphens, and underscores are allowed (1-16 characters).</red>", null));
                openRenameDialog(player, existingHomeOrNull, message, slot);
                return;
            }

            final String finalName = message;
            UUID uuid = player.getUniqueId();

            if (existingHomeOrNull != null) {
                // Check if name already exists for another slot
                List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(uuid);
                for (HomeManager.Home h : homes) {
                    if (h.getSlot() != slot && h.getName().equalsIgnoreCase(finalName)) {
                        player.sendMessage(plugin.parseMiniMessage("<red>A home named <yellow>" + finalName + "</yellow> already exists!</red>", null));
                        openRenameDialog(player, existingHomeOrNull, finalName, slot);
                        return;
                    }
                }

                // Rename / Update
                plugin.getHomeManager().deleteHome(uuid, existingHomeOrNull.getName());
                plugin.getHomeManager().setHome(uuid, finalName, existingHomeOrNull.toLocation(), existingHomeOrNull.getIconMaterial(), slot);
                player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + existingHomeOrNull.getName() + "</white> renamed to <white>" + finalName + "</white>!</green>", null));
                HomeManager.Home updated = plugin.getHomeManager().getHome(uuid, finalName);
                if (updated != null) {
                    openManageHome(player, updated);
                } else {
                    openHomeGrid(player);
                }
            } else {
                // Check if name already exists
                List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(uuid);
                for (HomeManager.Home h : homes) {
                    if (h.getName().equalsIgnoreCase(finalName)) {
                        player.sendMessage(plugin.parseMiniMessage("<red>A home named <yellow>" + finalName + "</yellow> already exists!</red>", null));
                        openRenameDialog(player, null, finalName, slot);
                        return;
                    }
                }

                // Create new
                plugin.getHomeManager().setHome(uuid, finalName, player.getLocation(), "LIME_BED", slot);
                player.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + finalName + "</white> set successfully!</green>", null));
                openHomeGrid(player);
            }
        });

        GeyserApi.api().sendForm(player.getUniqueId(), builder);
    }
}
