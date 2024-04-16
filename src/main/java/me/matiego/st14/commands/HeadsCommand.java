package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.HeadsManager;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.objects.command.CommandHandler;
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
import org.bukkit.Bukkit;
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
        if (hasAdminPermission(sender) && args.length > 0) {
            if (args.length != 1 && args.length != 2) {
                sender.sendMessage(Utils.getComponentByString("&cPoprawne użycie: /heads download (kategoria)"));
                return 0;
            }

            HeadsManager manager = plugin.getHeadsManager();
            if (!manager.isAvailable()) {
                sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cPobieranie główek już zostało rozpoczęte."));
                return 3;
            }
            manager.setAvailable(false);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getInventory().getHolder() instanceof HeadsGUI) {
                    player.closeInventory();
                    player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pobieranie nowych główek... Otwórz sklep jeszcze raz."));
                }
            }

            if (args.length == 1) {
                Utils.async(() -> {
                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Rozpoczynam pobieranie główek..."));

                    for (HeadsCategory category : HeadsCategory.values()) {
                        if (!category.downloadCategory()) {
                            sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNapotkano błąd przy pobieraniu kategorii " + category + "."));
                        }
                    }

                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pobieranie główek zakończone!"));
                    manager.setAvailable(true);
                });
                return 15;
            }

            HeadsCategory category = HeadsCategory.getCategoryByApiName(args[1]);
            if (category == null) {
                sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie istnieje kategoria z taką nazwą."));
                manager.setAvailable(true);
                return 1;
            }

            Utils.async(() -> {
                if (category.downloadCategory()) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pomyślnie pobrano tę kategorie."));
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
        if (args.length != 0) return -1;

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "heads", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        int guiSize = HeadsCategory.values().length / 9;
        if (HeadsCategory.values().length % 9 != 0) {
            guiSize++;
        }
        guiSize = Math.max(9, guiSize * 9);

        Inventory inv = GUI.createInventory(guiSize, Prefix.HEADS + "Wybierz kategorie");
        for (HeadsCategory category : HeadsCategory.values()) {
            inv.addItem(category.getGuiHead());
        }
        player.openInventory(inv);

        return 15;
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
            headsGUI.processInventoryClick(event, plugin);
            return;
        }

        if (!GUI.checkInventory(event,Prefix.HEADS + "Wybierz kategorie")) return;
        Player player = (Player) event.getWhoClicked();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "spawn", '.')) {
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

        HeadsGUI gui = HeadsGUI.createHeadsGUI(category);
        if (gui == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "&cAktualnie nie możesz kupić żadnego główki w tej kategorii. Spróbuj później."));
            player.closeInventory();
            return;
        }

        player.openInventory(gui.getInventory());
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
        if (args.length == 1) return List.of("download");
        if (args.length == 2) return Arrays.stream(HeadsCategory.values()).map(HeadsCategory::toString).toList();
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

        HeadsCategory category;
        String categoryName = event.getOption("kategoria", OptionMapping::getAsString);
        if (categoryName != null) {
            category = HeadsCategory.getCategoryByApiName(categoryName);
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
                hook.sendMessage("Pomyślnie pobrano " + downloaded + "/" + HeadsCategory.values().length + " kategorii główek.").queue();
                manager.setAvailable(true);
            });
            return 15;
        }

        Utils.async(() -> {
            if (category.downloadCategory()) {
                hook.sendMessage("Pomyślnie pobrano tę kategorie.").queue();
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
