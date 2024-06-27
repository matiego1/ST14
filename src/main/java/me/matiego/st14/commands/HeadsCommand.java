package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.HeadsManager;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.objects.heads.Head;
import me.matiego.st14.objects.heads.HeadsCategory;
import me.matiego.st14.objects.heads.HeadsGUI;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HeadsCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public HeadsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("heads");
        if (command == null) {
            Logs.warning("The command /heads does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        HeadsManager manager = plugin.getHeadsManager();
        if (hasAdminPermission(sender) && args.length > 0 && args[0].equalsIgnoreCase("download")) {
            if (args.length > 2) {
                sender.sendMessage(Utils.getComponentByString("&cPoprawne użycie: /heads download (kategoria)"));
                return 0;
            }

            long time = Utils.now();

            if (!manager.isAvailable()) {
                sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cPobieranie główek już zostało rozpoczęte."));
                return 3;
            }
            manager.setAvailable(false);

            if (args.length == 1) {
                Utils.async(() -> {
                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Rozpoczynam pobieranie wszystkich kategorii..."));

                    for (HeadsCategory category : HeadsCategory.values()) {
                        if (category.downloadCategory()) {
                            sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "-> Pomyślnie pobrano kategorię " + category + "."));
                        } else {
                            sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "-> &cNapotkano błąd przy pobieraniu kategorii " + category + "."));
                        }
                    }

                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pobieranie wszystkich kategorii zakończone w " + Utils.parseMillisToString(Utils.now() - time, true) + "."));
                    manager.setAvailable(true);
                });
                return 15;
            }

            HeadsCategory category = HeadsCategory.getCategoryByName(args[1]);
            if (category == null) {
                sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie istnieje kategoria z taką nazwą."));
                manager.setAvailable(true);
                return 1;
            }

            Utils.async(() -> {
                sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Rozpoczynam pobieranie kategorii " + category + "..."));
                if (category.downloadCategory()) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pomyślnie pobrano tę kategorie w " + Utils.parseMillisToString(Utils.now() - time, true) + "."));
                } else {
                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNapotkano błąd przy pobieraniu tej kategorii."));
                }
                manager.setAvailable(true);
            });
            return 15;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cTej komendy mogą użyć tylko gracze."));
            return 0;
        }

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "heads", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        if (args.length >= 2) {
            if (!(args[0].equalsIgnoreCase("find-by-name") || args[0].equalsIgnoreCase("find-by-tag"))) return -1;
            if (!manager.isAvailable()) {
                player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cTrwa pobieranie nowych główek. Spróbuj później."));
                return 5;
            }

            StringBuilder arg = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                arg.append(args[i]).append(" ");
            }

            List<Head> heads = switch (args[0].toLowerCase()) {
                case "find-by-name" -> manager.findHeadsByName(arg.substring(0, arg.length() - 1).toLowerCase());
                case "find-by-tag" -> manager.findHeadsByTag(arg.substring(0, arg.length() - 1).toLowerCase());
                default -> null;
            };

            if (heads == null) {
                player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                return 7;
            }
            if (heads.isEmpty()) {
                player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie znaleziono żadnych główek z tą nazwą/tagiem."));
                return 7;
            }

            Utils.async(() -> {
                HeadsGUI gui = new HeadsGUI(heads, player.getWorld(), plugin);
                player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Znaleziono " + heads.size() + " główek!"));
                Utils.sync(() -> player.openInventory(gui.getInventory()));
            });

            return 3;
        }

        if (args.length != 0) return -1;

        int guiSize = HeadsCategory.values().length / 9;
        if (HeadsCategory.values().length % 9 != 0) {
            guiSize++;
        }
        guiSize = Math.max(9, guiSize * 9);

        Inventory inv = GUI.createInventory(guiSize, Prefix.HEADS + "Wybierz kategorie");
        int i = 0;
        for (HeadsCategory category : HeadsCategory.values()) {
            int finalI = i++;
            Utils.async(() -> {
                ItemStack guiHead = category.getGuiHead();
                ItemMeta meta = guiHead.getItemMeta();
                int headsAmount = category.getHeadsAmount();
                if (headsAmount < 0) {
                    meta.lore(List.of(Utils.getComponentByString("&bKliknij, aby wyświetlić!").decoration(TextDecoration.ITALIC, false)));
                } else {
                    meta.lore(List.of(
                            Utils.getComponentByString("&b" + headsAmount + " główek").decoration(TextDecoration.ITALIC, false),
                            Utils.getComponentByString("&bKliknij, aby wyświetlić!").decoration(TextDecoration.ITALIC, false)
                    ));
                }
                guiHead.setItemMeta(meta);

                inv.setItem(finalI, guiHead);
            });
        }
        player.openInventory(inv);

        return 5;
    }

    private boolean hasAdminPermission(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        return player.hasPermission("st14.heads.admin");
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof HeadsGUI headsGUI) {
            headsGUI.processInventoryClick(event);
            return;
        }

        if (!GUI.checkInventory(event,Prefix.HEADS + "Wybierz kategorie")) return;
        Player player = (Player) event.getWhoClicked();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "heads", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie możesz użyć tej komendy w tym świecie."));
            player.closeInventory();
            return;
        }

        HeadsCategory category = HeadsCategory.getCategoryByName(getItemName(event.getCurrentItem()));
        if (category == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
            player.closeInventory();
            return;
        }

        Utils.async(() -> {
            HeadsGUI gui = HeadsGUI.createHeadsGUI(category, player.getWorld(), plugin);
            Utils.sync(() -> {
                player.closeInventory();
                if (gui == null) {
                    player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cTrwa pobieranie nowych główek albo napotkano niespodziewany błąd. Spróbuj później."));
                    return;
                }
                if (gui.getHeads().isEmpty()) {
                    player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cW tej kategorii nie ma jeszcze żadnych główek."));
                    return;
                }

                player.openInventory(gui.getInventory());
            });
        });
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return Utils.getPlainTextByComponent(name);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (hasAdminPermission(sender)) {
            if (args.length == 1) return Arrays.asList("download", "find-by-name", "find-by-tag");
            if (args.length == 2 && args[1].equalsIgnoreCase("download")) return Arrays.stream(HeadsCategory.values()).map(HeadsCategory::toString).toList();
            return new ArrayList<>();
        }
        if (args.length == 1) return Arrays.asList("find-by-name", "find-by-tag");
        return new ArrayList<>();
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("heads-download", "pobierz główki")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true)
                .addOptions(
                        new OptionData(OptionType.STRING, "kategoria", "nazwa kategorii", false, true)
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        long time = Utils.now();

        HeadsCategory category;
        String categoryName = event.getOption("kategoria", OptionMapping::getAsString);
        if (categoryName != null) {
            category = HeadsCategory.getCategoryByName(categoryName);
            if (category == null) {
                hook.sendMessage("Nie istnieje kategoria z taką nazwą.").queue();
                return 1;
            }
        } else {
            category = null;
        }

        HeadsManager manager = plugin.getHeadsManager();

        if (!manager.isAvailable()) {
            hook.sendMessage("Pobieranie główek już zostało rozpoczęte.").queue();
            return 3;
        }
        manager.setAvailable(false);

        if (category == null) {
            Utils.async(() -> {
                int downloaded = 0;
                for (HeadsCategory cat : HeadsCategory.values()) {
                    if (cat.downloadCategory()) {
                        downloaded++;
                    }
                }
                hook.sendMessage("Pomyślnie pobrano " + downloaded + "/" + HeadsCategory.values().length + " kategorii główek w " + Utils.parseMillisToString(Utils.now() - time, true) + ".").queue();
                manager.setAvailable(true);
            });
            return 15;
        }

        Utils.async(() -> {
            if (category.downloadCategory()) {
                hook.sendMessage("Pomyślnie pobrano tę kategorie w " + Utils.parseMillisToString(Utils.now() - time, true) + ".").queue();
            } else {
                hook.sendMessage("Napotkano błąd przy pobieraniu tej kategorii.").queue();
            }
            manager.setAvailable(true);
        });
        return 15;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        event.replyChoices(Arrays.stream(HeadsCategory.values())
                .map(HeadsCategory::toString)
                .filter(category -> category.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(category -> new Command.Choice(category, category))
                .toList()
        ).queue();
    }
}
