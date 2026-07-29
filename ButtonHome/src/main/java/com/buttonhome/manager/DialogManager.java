package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import com.buttonhome.manager.HomeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

// Paper Dialog imports
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;

import java.util.*;

public class DialogManager implements Listener {

    private final ButtonHome plugin;

    public DialogManager(ButtonHome plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

        String titleStr = plugin.getConfig().getString("gui.stage1.title", "Homes Grid");
        titleStr = titleStr.replace("%page%", String.valueOf(page));
        Component title = plugin.parseMiniMessage(titleStr, null);

        DialogBase base = DialogBase.builder(title)
            .canCloseWithEscape(true)
            .build();

        int slotsPerPage = plugin.getConfig().getInt("gui.settings.homes-per-page", 7);
        if (slotsPerPage < 1) slotsPerPage = 7;

        List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        List<ActionButton> buttons = new ArrayList<>();

        for (int i = 0; i < slotsPerPage; i++) {
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

                    Component label = plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.occupied.name", "<green>%home%</green>"), placeholders);
                    Component tooltip = getTooltipFromConfig("gui.items.occupied.lore", placeholders);

                    buttons.add(ActionButton.create(
                        label,
                        tooltip,
                        100,
                        DialogAction.customClick((response, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                openManageHome(p, home);
                            });
                        }, ClickCallback.Options.builder().build())
                    ));
                } else {
                    // Empty / New slot
                    Map<String, String> placeholders = Map.of("%index%", String.valueOf(homeSlotIndex));
                    Component label = plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.new.name", "<gray>Empty Slot %index%</gray>"), placeholders);
                    Component tooltip = getTooltipFromConfig("gui.items.new.lore", placeholders);

                    buttons.add(ActionButton.create(
                        label,
                        tooltip,
                        100,
                        DialogAction.customClick((response, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                openRenameDialog(p, null, null, homeSlotIndex);
                            });
                        }, ClickCallback.Options.builder().build())
                    ));
                }
            } else {
                // Locked slot
                Map<String, String> placeholders = Map.of("%index%", String.valueOf(homeSlotIndex));
                Component label = plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.locked.name", "<red>Locked Slot %index%</red>"), placeholders);
                Component tooltip = getTooltipFromConfig("gui.items.locked.lore", placeholders);

                buttons.add(ActionButton.create(
                    label,
                    tooltip,
                    100,
                    DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            openLockedNotice(p);
                        });
                    }, ClickCallback.Options.builder().build())
                ));
            }
        }

        int prevPage = page - 1;
        ActionButton backButton = null;
        if (page > 1) {
            backButton = ActionButton.create(
                plugin.parseMiniMessage("<yellow><- Previous Page</yellow>", null),
                plugin.parseMiniMessage("Return to page " + prevPage, null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openHomeGrid(p, prevPage);
                    });
                }, ClickCallback.Options.builder().build())
            );
        }

        boolean hasMore = (page * slotsPerPage) < 50;
        int nextPage = page + 1;
        if (hasMore) {
            buttons.add(ActionButton.create(
                plugin.parseMiniMessage("<yellow>Next Page -></yellow>", null),
                plugin.parseMiniMessage("Advance to page " + nextPage, null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openHomeGrid(p, nextPage);
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        DialogType type = DialogType.multiAction(buttons, backButton, 3);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(base)
                .type(type);
        });

        player.showDialog(dialog);
    }

    private HomeManager.Home getHomeAtSlot(List<HomeManager.Home> homes, int slot) {
        for (HomeManager.Home h : homes) {
            if (h.getSlot() == slot) {
                return h;
            }
        }
        return null;
    }

    private Component getTooltipFromConfig(String path, Map<String, String> placeholders) {
        List<String> loreRaw = plugin.getConfig().getStringList(path);
        if (loreRaw == null || loreRaw.isEmpty()) {
            return Component.empty();
        }
        Component tooltip = Component.empty();
        for (int i = 0; i < loreRaw.size(); i++) {
            if (i > 0) tooltip = tooltip.append(Component.newline());
            tooltip = tooltip.append(plugin.parseMiniMessage(loreRaw.get(i), placeholders));
        }
        return tooltip;
    }

    // ------------------------------------------------------------------
    // 5.2 — MANAGE HOME
    // ------------------------------------------------------------------
    public void openManageHome(Player player, HomeManager.Home home) {
        String titleStr = plugin.getConfig().getString("gui.stage2.title", "Manage %home%");
        titleStr = titleStr.replace("%home%", home.getName());
        Component title = plugin.parseMiniMessage(titleStr, null);

        String coords = home.getWorldName() + " (" + Math.round(home.getX()) + ", " + Math.round(home.getY()) + ", " + Math.round(home.getZ()) + ")";
        Component coordsLine = plugin.parseMiniMessage("<gray>" + coords + "</gray>", null);

        String iconMatStr = home.getIconMaterial();
        Material mat = Material.matchMaterial(iconMatStr);
        if (mat == null) mat = Material.LIME_BED;
        ItemStack iconItem = new ItemStack(mat);

        DialogBase base = DialogBase.builder(title)
            .body(List.of(DialogBody.item(iconItem), DialogBody.plainMessage(coordsLine)))
            .canCloseWithEscape(true)
            .build();

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.teleport.name", "<green>Teleport</green>"), null),
            getTooltipFromConfig("gui.items.teleport.lore", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.performCommand("hometp " + home.getName());
                });
            }, ClickCallback.Options.builder().build())
        ));

        buttons.add(ActionButton.create(
            plugin.parseMiniMessage("<yellow>Rename Home</yellow>", null),
            plugin.parseMiniMessage("Change the name of this home", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openRenameDialog(p, home);
                });
            }, ClickCallback.Options.builder().build())
        ));

        buttons.add(ActionButton.create(
            plugin.parseMiniMessage("<yellow>Change Icon</yellow>", null),
            plugin.parseMiniMessage("Change material icon display", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openIconPicker(p, home);
                });
            }, ClickCallback.Options.builder().build())
        ));

        buttons.add(ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.delete.name", "<red>Delete</red>"), null),
            getTooltipFromConfig("gui.items.delete.lore", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openConfirmDelete(p, home);
                });
            }, ClickCallback.Options.builder().build())
        ));

        ActionButton backButton = ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.back.name", "<gray>Back</gray>"), null),
            plugin.parseMiniMessage("Return to homes grid", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openHomeGrid(p);
                });
            }, ClickCallback.Options.builder().build())
        );

        DialogType type = DialogType.multiAction(buttons, backButton, 2);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(base)
                .type(type);
        });

        player.showDialog(dialog);
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
        String titleStr = existingHomeOrNull != null ? "Rename " + existingHomeOrNull.getName() : "Set New Home";
        DialogBase.Builder baseBuilder = DialogBase.builder(plugin.parseMiniMessage(titleStr, null))
            .canCloseWithEscape(true);

        io.papermc.paper.registry.data.dialog.input.TextDialogInput.Builder inputBuilder = DialogInput.text(
            "home_name",
            plugin.parseMiniMessage("Enter Home Name:", null)
        );
        if (existingHomeOrNull != null) {
            inputBuilder.initial(existingHomeOrNull.getName());
        } else if (initialName != null) {
            inputBuilder.initial(initialName);
        }
        inputBuilder.maxLength(16);
        inputBuilder.width(300);

        baseBuilder.inputs(List.of(inputBuilder.build()));

        ActionButton saveButton = ActionButton.create(
            plugin.parseMiniMessage("<green>Save</green>", null),
            plugin.parseMiniMessage("Save home details", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = response.getText("home_name");
                    if (message == null) message = "";
                    message = message.trim();

                    if (!message.matches("^[A-Za-z0-9_-]{1,16}$")) {
                        p.sendMessage(plugin.parseMiniMessage("<red>Invalid home name! Only letters, numbers, hyphens, and underscores are allowed (1-16 characters).</red>", null));
                        String finalMsg = message;
                        openRenameDialog(p, existingHomeOrNull, finalMsg, slot);
                        return;
                    }

                    String finalMessage = message;
                    if (existingHomeOrNull != null) {
                        plugin.getHomeManager().deleteHome(p.getUniqueId(), existingHomeOrNull.getName());
                        plugin.getHomeManager().setHome(p.getUniqueId(), finalMessage, existingHomeOrNull.toLocation(), existingHomeOrNull.getIconMaterial(), slot);
                        p.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + existingHomeOrNull.getName() + "</white> renamed to <white>" + finalMessage + "</white>!</green>", null));
                        HomeManager.Home updated = plugin.getHomeManager().getHome(p.getUniqueId(), finalMessage);
                        if (updated != null) {
                            openManageHome(p, updated);
                        } else {
                            openHomeGrid(p);
                        }
                    } else {
                        plugin.getHomeManager().setHome(p.getUniqueId(), finalMessage, p.getLocation(), "LIME_BED", slot);
                        p.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + finalMessage + "</white> set successfully!</green>", null));
                        openHomeGrid(p);
                    }
                });
            }, ClickCallback.Options.builder().build())
        );

        ActionButton cancelButton = ActionButton.create(
            plugin.parseMiniMessage("<red>Cancel</red>", null),
            plugin.parseMiniMessage("Cancel operation", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (existingHomeOrNull != null) {
                        openManageHome(p, existingHomeOrNull);
                    } else {
                        openHomeGrid(p);
                    }
                });
            }, ClickCallback.Options.builder().build())
        );

        DialogType type = DialogType.confirmation(saveButton, cancelButton);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(baseBuilder.build())
                .type(type);
        });

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.4 — CONFIRM DELETE
    // ------------------------------------------------------------------
    public void openConfirmDelete(Player player, HomeManager.Home home) {
        String titleStr = "Delete " + home.getName() + "?";
        Component title = plugin.parseMiniMessage(titleStr, null);

        DialogBase base = DialogBase.builder(title)
            .canCloseWithEscape(true)
            .build();

        ActionButton confirmButton = ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.delete.name", "<red>Delete</red>"), null),
            plugin.parseMiniMessage("Permanently delete home", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getHomeManager().deleteHome(p.getUniqueId(), home.getName());
                    p.sendMessage(plugin.parseMiniMessage("<green>Home <white>" + home.getName() + "</white> deleted.</green>", null));
                    openHomeGrid(p);
                });
            }, ClickCallback.Options.builder().build())
        );

        ActionButton cancelButton = ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.cancel.name", "<gray>Cancel</gray>"), null),
            plugin.parseMiniMessage("Keep home and go back", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openManageHome(p, home);
                });
            }, ClickCallback.Options.builder().build())
        );

        DialogType type = DialogType.confirmation(confirmButton, cancelButton);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(base)
                .type(type);
        });

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.5 — LOCKED NOTICE
    // ------------------------------------------------------------------
    public void openLockedNotice(Player player) {
        String msg = plugin.getFormattedMessage("messages.locked-slot-clicked");
        Component text = plugin.parseMiniMessage(msg, null);

        DialogBase base = DialogBase.builder(plugin.parseMiniMessage("<red>Slot Locked</red>", null))
            .canCloseWithEscape(true)
            .build();

        ActionButton okButton = ActionButton.create(
            plugin.parseMiniMessage("<gray>Close</gray>", null),
            text,
            100,
            DialogAction.customClick((response, audience) -> {}, ClickCallback.Options.builder().build())
        );

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(base)
                .type(DialogType.notice(okButton));
        });
        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.6 — ICON PICKER
    // ------------------------------------------------------------------
    public void openIconPicker(Player player, HomeManager.Home home) {
        openIconPicker(player, home, "", 1);
    }

    public void openIconPicker(Player player, HomeManager.Home home, String searchQuery, int page) {
        if (page < 1) page = 1;

        // Get all materials that are items
        List<Material> allMaterials = new ArrayList<>();
        String normalizedQuery = searchQuery.trim().toLowerCase();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isLegacy() && material != Material.AIR) {
                String name = material.name().toLowerCase();
                if (normalizedQuery.isEmpty() || name.contains(normalizedQuery)) {
                    allMaterials.add(material);
                }
            }
        }

        // Sort alphabetically so it's clean and predictable
        allMaterials.sort(Comparator.comparing(Material::name));

        int itemsPerPage = 20; // 20 icons per page is a good dense grid
        int totalItems = allMaterials.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
        if (totalPages < 1) totalPages = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        Component title = plugin.parseMiniMessage("Choose Icon for " + home.getName() + " (Page " + page + "/" + totalPages + ")", null);
        DialogBase.Builder baseBuilder = DialogBase.builder(title)
            .canCloseWithEscape(true);

        // Add search input
        io.papermc.paper.registry.data.dialog.input.TextDialogInput.Builder inputBuilder = DialogInput.text(
            "search_query",
            plugin.parseMiniMessage("Search Icons:", null)
        );
        if (!searchQuery.isEmpty()) {
            inputBuilder.initial(searchQuery);
        }
        inputBuilder.maxLength(32);
        inputBuilder.width(200);
        baseBuilder.inputs(List.of(inputBuilder.build()));

        List<ActionButton> buttons = new ArrayList<>();

        // Add search action button
        buttons.add(ActionButton.create(
            plugin.parseMiniMessage("<gold>🔍 Search</gold>", null),
            plugin.parseMiniMessage("Filter items by query", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                String query = response.getText("search_query");
                if (query == null) query = "";
                String finalQuery = query;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openIconPicker(p, home, finalQuery, 1);
                });
            }, ClickCallback.Options.builder().build())
        ));

        // Add clear search button if a query is active
        if (!searchQuery.isEmpty()) {
            buttons.add(ActionButton.create(
                plugin.parseMiniMessage("<red>❌ Clear Search</red>", null),
                plugin.parseMiniMessage("Clear active filter", null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openIconPicker(p, home, "", 1);
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        // Add items for current page
        for (int i = startIndex; i < endIndex; i++) {
            Material material = allMaterials.get(i);
            String formattedName = formatMaterialName(material);
            buttons.add(ActionButton.create(
                plugin.parseMiniMessage("<white>" + formattedName + "</white>", null),
                plugin.parseMiniMessage("<yellow>Click to set as icon</yellow>", null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getHomeManager().setHome(p.getUniqueId(), home.getName(), home.toLocation(), material.name(), home.getSlot());
                        p.sendMessage(plugin.parseMiniMessage("<green>Icon for home <white>" + home.getName() + "</white> set to <white>" + formattedName + "</white>!</green>", null));
                        HomeManager.Home updated = plugin.getHomeManager().getHome(p.getUniqueId(), home.getName());
                        if (updated != null) {
                            openManageHome(p, updated);
                        } else {
                            openHomeGrid(p);
                        }
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        // Add back button
        ActionButton backButton = ActionButton.create(
            plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.back.name", "<gray>Back</gray>"), null),
            plugin.parseMiniMessage("Return to manage home screen", null),
            100,
            DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player p)) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openManageHome(p, home);
                });
            }, ClickCallback.Options.builder().build())
        );

        // Add pagination buttons if multiple pages exist
        if (page > 1) {
            int prevPage = page - 1;
            buttons.add(ActionButton.create(
                plugin.parseMiniMessage("<yellow>◀ Previous Page</yellow>", null),
                plugin.parseMiniMessage("Go to page " + prevPage, null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String query = response.getText("search_query");
                    if (query == null) query = searchQuery;
                    String finalQuery = query;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openIconPicker(p, home, finalQuery, prevPage);
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        if (page < totalPages) {
            int nextPage = page + 1;
            buttons.add(ActionButton.create(
                plugin.parseMiniMessage("<yellow>Next Page ▶</yellow>", null),
                plugin.parseMiniMessage("Go to page " + nextPage, null),
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String query = response.getText("search_query");
                    if (query == null) query = searchQuery;
                    String finalQuery = query;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openIconPicker(p, home, finalQuery, nextPage);
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        DialogType type = DialogType.multiAction(buttons, backButton, 4);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(baseBuilder.build())
                .type(type);
        });

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.7 — ADMIN HOME GRID
    // ------------------------------------------------------------------
    public void openAdminHomeGrid(Player admin, String targetName, UUID targetUuid, List<HomeManager.Home> homes) {
        String titleStr = plugin.getConfig().getString("gui.stage1.admin-title", "%player%'s Homes");
        titleStr = titleStr.replace("%player%", targetName);
        Component title = plugin.parseMiniMessage(titleStr, null);

        DialogBase base = DialogBase.builder(title)
            .canCloseWithEscape(true)
            .build();

        List<ActionButton> buttons = new ArrayList<>();

        for (HomeManager.Home home : homes) {
            Map<String, String> placeholders = Map.of(
                "%home%", home.getName(),
                "%world%", home.getWorldName(),
                "%x%", String.valueOf(Math.round(home.getX())),
                "%y%", String.valueOf(Math.round(home.getY())),
                "%z%", String.valueOf(Math.round(home.getZ()))
            );

            Component label = plugin.parseMiniMessage(plugin.getConfig().getString("gui.items.occupied.name", "<green>%home%</green>"), placeholders);
            Component tooltip = getTooltipFromConfig("gui.items.occupied.lore", placeholders);

            buttons.add(ActionButton.create(
                label,
                tooltip,
                100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Location loc = home.toLocation();
                        if (loc == null || loc.getWorld() == null) {
                            p.sendMessage(plugin.parseMiniMessage("<red>✘ Teleport failed — the world <white>" + home.getWorldName() + "</white> is not loaded.</red>", null));
                            return;
                        }
                        p.teleport(loc);
                        p.sendMessage(plugin.parseMiniMessage("<green>✔ Instantly teleported to " + targetName + "'s home: <white>" + home.getName() + "</white>.</green>", null));
                    });
                }, ClickCallback.Options.builder().build())
            ));
        }

        DialogType type = DialogType.multiAction(buttons, null, 3);

        Dialog dialog = Dialog.create(factory -> {
            ((DialogRegistryEntry.Builder) factory.empty())
                .base(base)
                .type(type);
        });

        admin.showDialog(dialog);
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
}