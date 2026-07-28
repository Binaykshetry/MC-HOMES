package com.buttonhome.manager;

import com.buttonhome.ButtonHome;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders every ButtonHome screen using Paper's native Dialog API.
 * No resource pack, no chest inventory, no chat text — this uses the
 * vanilla client's built-in Dialog UI (Minecraft 1.21.6+, Paper only).
 */
public class DialogManager {

    private final ButtonHome plugin;

    public DialogManager(ButtonHome plugin) {
        this.plugin = plugin;
    }

    private HomeManager.Home getHomeAtSlot(List<HomeManager.Home> homes, int slot) {
        for (HomeManager.Home h : homes) {
            if (h.getSlot() == slot) {
                return h;
            }
        }
        return null;
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
    // 5.1 — HOME GRID  (equivalent of your screenshot's "Homes" screen)
    // ------------------------------------------------------------------
    public void openHomeGrid(Player player, int page) {
        if (page < 1) page = 1;
        List<HomeManager.Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        List<ActionButton> buttons = new ArrayList<>();

        int startSlot = (page - 1) * 50 + 1;
        int endSlot = page * 50;

        for (int i = startSlot; i <= endSlot; i++) {
            final int slot = i;
            if (hasSlotPermission(player, slot)) {
                HomeManager.Home home = getHomeAtSlot(homes, slot);
                if (home != null) {
                    // Occupied slot
                    buttons.add(ActionButton.create(
                            Component.text("■ " + home.getName(), NamedTextColor.WHITE),
                            Component.text("Click to manage"),
                            90,
                            DialogAction.customClick(
                                    (response, audience) -> openManageHome(player, home),
                                    ClickCallback.Options.builder().build()
                            )
                    ));
                } else {
                    // Empty, unlocked slot
                    buttons.add(ActionButton.create(
                            Component.text("New Home (" + slot + ")", NamedTextColor.GRAY),
                            Component.text("Click to set a home here"),
                            90,
                            DialogAction.customClick(
                                    (response, audience) -> openRenameDialog(player, null, null, slot),
                                    ClickCallback.Options.builder().build()
                            )
                    ));
                }
            } else {
                // Locked slot (beyond this player's permission)
                buttons.add(ActionButton.create(
                        Component.text("Locked 🔒 (" + slot + ")", NamedTextColor.RED),
                        Component.text("No permission for slot " + slot),
                        90,
                        DialogAction.customClick(
                                (response, audience) -> {
                                    player.sendMessage(Component.text("You don't have permission to use home " + slot, NamedTextColor.RED));
                                },
                                ClickCallback.Options.builder().build()
                        )
                ));
            }
        }

        List<ActionButton> extraButtons = new ArrayList<>();
        final int currentPage = page;
        if (page > 1) {
            extraButtons.add(ActionButton.create(
                    Component.text("◀ Prev Page", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (page - 1)),
                    120,
                    DialogAction.customClick(
                            (response, audience) -> openHomeGrid(player, currentPage - 1),
                            ClickCallback.Options.builder().build()
                    )
            ));
        }

        extraButtons.add(ActionButton.create(
                Component.text("Next Page ▶", NamedTextColor.YELLOW),
                Component.text("Go to page " + (page + 1)),
                120,
                DialogAction.customClick(
                        (response, audience) -> openHomeGrid(player, currentPage + 1),
                        ClickCallback.Options.builder().build()
                )
        ));

        ActionButton[] extraButtonsArray = extraButtons.toArray(new ActionButton[0]);

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Homes - Page " + currentPage))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Select a home, or set a new one."))))
                        .build())
                .type(DialogType.multiAction(buttons, buildBackButton(player), 6, extraButtonsArray))
        );

        player.showDialog(dialog);
    }

    private ActionButton buildBackButton(Player player) {
        return ActionButton.create(
                Component.text("Close", NamedTextColor.RED),
                Component.text("Close menu"),
                90,
                DialogAction.customClick(
                        (response, audience) -> {},
                        ClickCallback.Options.builder().build()
                )
        );
    }

    // ------------------------------------------------------------------
    // 5.2 — MANAGE HOME  (Teleport / Change Icon / Rename / Delete / Back)
    // ------------------------------------------------------------------
    public void openManageHome(Player player, HomeManager.Home home) {
        ActionButton teleportBtn = ActionButton.create(
                Component.text("Teleport", NamedTextColor.WHITE),
                Component.text("Teleport to " + home.getName()),
                150,
                DialogAction.customClick((response, audience) -> {
                    plugin.getTeleportManager().startTeleport(player, home.getName(), home.toLocation());
                }, ClickCallback.Options.builder().build())
        );

        ActionButton changeIconBtn = ActionButton.create(
                Component.text("Change Icon", NamedTextColor.WHITE),
                Component.text("Pick an icon for this home"),
                150,
                DialogAction.customClick(
                        (response, audience) -> openIconPicker(player, home),
                        ClickCallback.Options.builder().build()
                )
        );

        ActionButton renameBtn = ActionButton.create(
                Component.text("Rename", NamedTextColor.WHITE),
                Component.text("Rename this home"),
                150,
                DialogAction.customClick(
                        (response, audience) -> openRenameDialog(player, home, null, home.getSlot()),
                        ClickCallback.Options.builder().build()
                )
        );

        ActionButton deleteBtn = ActionButton.create(
                Component.text("Delete", NamedTextColor.RED),
                Component.text("Delete this home"),
                150,
                DialogAction.customClick(
                        (response, audience) -> openConfirmDelete(player, home),
                        ClickCallback.Options.builder().build()
                )
        );

        long x = Math.round(home.getX());
        long y = Math.round(home.getY());
        long z = Math.round(home.getZ());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(home.getName()))
                        .body(List.of(
                                DialogBody.item(iconForHome(home), 32, 32, true, true),
                                DialogBody.plainMessage(Component.text(
                                        "World: " + home.getWorldName() + " | Coords: " + x + ", " + y + ", " + z,
                                        NamedTextColor.GRAY
                                ))
                        ))
                        .build())
                .type(DialogType.multiAction(
                        List.of(teleportBtn, changeIconBtn, renameBtn, deleteBtn),
                        buildBackToGridButton(player),
                        2 // 2 columns -> matches the 2x2 + Back layout in your screenshot
                ))
        );

        player.showDialog(dialog);
    }

    private ItemStack iconForHome(HomeManager.Home home) {
        Material material;
        try {
            material = Material.valueOf(home.getIconMaterial().toUpperCase());
        } catch (Exception e) {
            material = Material.LIME_BED;
        }
        return new ItemStack(material);
    }

    private ActionButton buildBackToGridButton(Player player) {
        return ActionButton.create(
                Component.text("Back", NamedTextColor.GRAY),
                Component.text("Go back to list"),
                120,
                DialogAction.customClick(
                        (response, audience) -> openHomeGrid(player),
                        ClickCallback.Options.builder().build()
                )
        );
    }

    // ------------------------------------------------------------------
    // 5.3 — RENAME / SET HOME  (text input dialog)
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
        String currentName = existingHomeOrNull != null ? existingHomeOrNull.getName() : (initialName != null ? initialName : "");

        TextDialogInput nameInput = TextDialogInput.builder("home_name", Component.text("Home name"))
                .initial(currentName)
                .maxLength(16)
                .labelVisible(true)
                .build();

        ActionButton saveBtn = ActionButton.create(
                Component.text("Save", NamedTextColor.GREEN),
                Component.text("Confirm"),
                120,
                DialogAction.customClick((response, audience) -> {
                    String newName = response.getText("home_name");
                    if (newName == null || !newName.matches("^[A-Za-z0-9_-]{1,16}$")) {
                        player.sendMessage(Component.text("Invalid home name! Only alphanumeric, underscores, and hyphens allowed (1-16 chars).", NamedTextColor.RED));
                        return;
                    }
                    if (existingHomeOrNull != null) {
                        plugin.getHomeManager().deleteHome(player.getUniqueId(), existingHomeOrNull.getName());
                        plugin.getHomeManager().setHome(player.getUniqueId(), newName, existingHomeOrNull.toLocation(), existingHomeOrNull.getIconMaterial(), existingHomeOrNull.getSlot());
                    } else {
                        plugin.getHomeManager().setHome(player.getUniqueId(), newName, player.getLocation(), null, slot);
                    }
                    openHomeGrid(player);
                }, ClickCallback.Options.builder().build())
        );

        ActionButton cancelBtn = ActionButton.create(
                Component.text("Cancel", NamedTextColor.RED),
                Component.text("Discard"),
                120,
                DialogAction.customClick(
                        (response, audience) -> {
                            if (existingHomeOrNull != null) {
                                openManageHome(player, existingHomeOrNull);
                            } else {
                                openHomeGrid(player);
                            }
                        },
                        ClickCallback.Options.builder().build()
                )
        );

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(
                                existingHomeOrNull != null ? "Rename Home" : "Set Home"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Enter a name:"))))
                        .inputs(List.of(nameInput))
                        .build())
                .type(DialogType.confirmation(saveBtn, cancelBtn))
        );

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.4 — CONFIRM DELETE  (yes/no confirmation dialog)
    // ------------------------------------------------------------------
    public void openConfirmDelete(Player player, HomeManager.Home home) {
        ActionButton yesBtn = ActionButton.create(
                Component.text("Delete", NamedTextColor.RED),
                Component.text("This cannot be undone"),
                120,
                DialogAction.customClick((response, audience) -> {
                    plugin.getHomeManager().deleteHome(player.getUniqueId(), home.getName());
                    player.sendMessage(Component.text("Home " + home.getName() + " deleted.", NamedTextColor.GREEN));
                    openHomeGrid(player);
                }, ClickCallback.Options.builder().build())
        );

        ActionButton noBtn = ActionButton.create(
                Component.text("Cancel", NamedTextColor.GRAY),
                Component.text("Keep this home"),
                120,
                DialogAction.customClick(
                        (response, audience) -> openManageHome(player, home),
                        ClickCallback.Options.builder().build()
                )
        );

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Delete " + home.getName() + "?"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Are you sure you want to delete this home?"))))
                        .build())
                .type(DialogType.confirmation(yesBtn, noBtn))
        );

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.5 — LOCKED NOTICE  (single button notice)
    // ------------------------------------------------------------------
    public void openLockedNotice(Player player) {
        ActionButton okBtn = ActionButton.create(
                Component.text("OK", NamedTextColor.GREEN),
                Component.text("Dismiss"),
                120,
                DialogAction.customClick(
                        (response, audience) -> openHomeGrid(player),
                        ClickCallback.Options.builder().build()
                )
        );

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Locked Slot"))
                        .body(List.of(DialogBody.plainMessage(Component.text("You do not have permission for this slot!"))))
                        .build())
                .type(DialogType.notice(okBtn))
        );

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------
    // 5.6 — ICON PICKER  (materials picker grid)
    // ------------------------------------------------------------------
    public void openIconPicker(Player player, HomeManager.Home home) {
        List<Material> materials = List.of(
                Material.LIME_BED, Material.RED_BED, Material.BLUE_BED,
                Material.GRASS_BLOCK, Material.DIAMOND, Material.GOLD_INGOT,
                Material.IRON_SWORD, Material.BOW, Material.SHIELD,
                Material.CHEST, Material.CRAFTING_TABLE, Material.FURNACE,
                Material.BOOKSHELF, Material.ENCHANTING_TABLE, Material.ENDER_CHEST,
                Material.CAMPFIRE, Material.TORCH, Material.LANTERN,
                Material.OAK_LOG, Material.COBBLESTONE, Material.SAND,
                Material.REDSTONE, Material.TNT, Material.SLIME_BALL
        );

        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : materials) {
            buttons.add(ActionButton.create(
                    Component.text(formatMaterialName(material), NamedTextColor.WHITE),
                    Component.text("Click to select " + formatMaterialName(material)),
                    90,
                    DialogAction.customClick(
                            (response, audience) -> {
                                plugin.getHomeManager().setHome(player.getUniqueId(), home.getName(), home.toLocation(), material.name(), home.getSlot());
                                player.sendMessage(Component.text("Icon changed successfully!", NamedTextColor.GREEN));
                                HomeManager.Home updated = plugin.getHomeManager().getHome(player.getUniqueId(), home.getName());
                                if (updated != null) {
                                    openManageHome(player, updated);
                                } else {
                                    openHomeGrid(player);
                                }
                            },
                            ClickCallback.Options.builder().build()
                    )
            ));
        }

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Choose Icon"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Select an icon for " + home.getName()))))
                        .build())
                .type(DialogType.multiAction(buttons, buildBackToGridButton(player), 6))
        );

        player.showDialog(dialog);
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
