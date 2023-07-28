package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.AccountsManager;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.milkbowl.vault.economy.EconomyResponse;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EconomyCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public EconomyCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("economy");
        if (command == null) {
            Logs.warning("The command /economy does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;
    private final Main plugin;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player) || (player.isOp() && args.length > 0)) {
            if (args.length < 2) {
                sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne użycie: /economy [add|remove|set|get] <gracz> <ilość*>"));
                return 0;
            }
            EconomyManager economy = plugin.getEconomyManager();
            Utils.async(() -> {
                args[0] = args[0].toLowerCase();
                if (args[0].equals("get")) {
                    if (args.length != 2) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne użycie: /economy get <gracz>"));
                        return;
                    }
                    UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[1]);
                    if (uuid == null) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cTen gracz nie jest online."));
                        return;
                    }
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Gracz &9" + args[1] + "&b ma &9" + economy.format(economy.getBalance(Bukkit.getOfflinePlayer(uuid))) + "&b."));
                    return;
                }

                if (args.length != 3) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne użycie: /economy [add|remove|set] <gracz> <ilość>"));
                    return;
                }
                if (!(args[0].equals("add") || args[0].equals("remove") || args[0].equals("set"))) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne użycie: /economy [add|remove|set] <gracz> <ilość>"));
                    return;
                }

                UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNieznany gracz."));
                    return;
                }

                double amount;
                try {
                    amount = Utils.round(Double.parseDouble(args[2]), 2);
                } catch (Exception e) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cPodaj poprawną ilość pieniędzy."));
                    return;
                }
                if (amount < 0d) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNie można ustawić ujemnej wartości."));
                    return;
                }

                EconomyResponse response = switch (args[0]) {
                    case "add" -> economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                    case "remove" -> economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                    case "set" -> economy.setBalance(Bukkit.getOfflinePlayer(uuid), amount);
                    default -> new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
                };

                if (!response.transactionSuccess()) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd."));
                    return;
                }

                Logs.info(
                        "Administrator " +
                        switch (args[0]) {
                            case "add" -> "zwiększył";
                            case "remove" -> "zmniejszył";
                            case "set" -> "ustawił";
                            default -> null;
                        } +
                        " stan konta gracza " + args[1] + " o/na " + economy.format(amount) + "."
                );

                sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie zmieniono saldo konta gracza " + args[1] + " (" + uuid +") na " + economy.format(response.balance)));
                informPlayer(
                        uuid,
                        "[Administrator]",
                        amount,
                        switch (args[0]) {
                            case "add" -> Type.ADD;
                            case "remove" -> Type.REMOVE;
                            case "set" -> Type.SET;
                            default -> Type.UNKNOWN;
                        }
                );
            });
            return 0;
        } else {
            if (args.length != 0) return -1;

            if (Utils.checkIfCanNotExecuteCommandInWorld(player, "economy")) {
                Utils.async(() -> player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Saldo twojego konta: &9" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))));
                return 5;
            }

            Inventory inv = GUI.createInventory(9, Prefix.ECONOMY + "Twoje konto");
            inv.setItem(0, GUI.createGuiItem(Material.DISPENSER, "&9Przelew", "&bPrzelej pieniądze innemu graczowi"));
            inv.setItem(1, GUI.createGuiItem(Material.PAPER, "&9Wypłata", "&bWypłać pieniądze w postaci banknotu"));
            Utils.async(() -> inv.setItem(4, GUI.createGuiItem(Material.DIAMOND, "&9Saldo konta", "&b" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))));
            inv.setItem(7, GUI.createGuiItem(Material.VILLAGER_SPAWN_EGG, "&9Sklep", "&cMoże wkrótce!"));
            inv.setItem(8, GUI.createGuiItem(Material.CREEPER_HEAD, "&9Kup główkę", "&cJuż wkrótce!"));
            player.openInventory(inv);
            return 3;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player) || player.isOp()) {
            if (args.length == 1) {
                return Arrays.asList("add", "remove", "set", "get");
            }
            if (args.length == 2) {
                return plugin.getOfflinePlayersManager().getNames();
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.ECONOMY + "Twoje konto")) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        if (slot == 0) {
            new AnvilGUI.Builder()
                    .jsonTitle(Utils.getJsonByLegacyString(Prefix.ECONOMY + "Wpisz wartość"))
                    .text("Wpisz tutaj...")
                    .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Wprowadź wartość...", "&bKliknij &9ESC&b, aby wyjść", "&bKliknij przedmiot po prawej, aby zaakceptować"))
                    .plugin(plugin)
                    .interactableSlots(AnvilGUI.Slot.OUTPUT)
                    .onClick((anvilSlot, state) -> {
                        if (anvilSlot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                        double amount;
                        try {
                            amount = Utils.round(Double.parseDouble(state.getText()), 2);
                        } catch (Exception e) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczbę!"));
                        }
                        if (amount <= 0d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczbę!"));
                        }
                        amount = Utils.round(amount, 2);

                        EconomyManager economy = plugin.getEconomyManager();
                        if (!economy.has(player, amount)) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Brak środków"));
                        }

                        finishTransfer(player, amount);
                        return List.of(AnvilGUI.ResponseAction.close());
                    })
                    .open(player);
        } else if (slot == 1) {
            new AnvilGUI.Builder()
                    .jsonTitle(Utils.getJsonByLegacyString(Prefix.ECONOMY + "Wpisz wartość"))
                    .text("Wpisz tutaj...")
                    .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Wprowadź wartość...", "&bKliknij &9ESC&b, aby wyjść", "&bKliknij przedmiot po prawej, aby zaakceptować"))
                    .plugin(plugin)
                    .interactableSlots(AnvilGUI.Slot.OUTPUT)
                    .onClick((anvilSlot, state) -> {
                        if (anvilSlot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                        double amount;
                        try {
                            amount = Utils.round(Double.parseDouble(state.getText()), 2);
                        } catch (Exception e) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczbę!"));
                        }
                        if (amount <= 0d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczbę!"));
                        }
                        if (amount >= 500d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Za duża kwota!"));
                        }
                        amount = Utils.round(amount, 2);

                        EconomyManager economy = plugin.getEconomyManager();
                        if (!economy.has(player, amount)) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Brak środków"));
                        }

                        ItemStack banknote = plugin.getBanknoteManager().createBanknote(amount);
                        if (banknote == null) {
                            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
                            return List.of(AnvilGUI.ResponseAction.close());
                        }

                        EconomyResponse response = economy.withdrawPlayer(player, amount);
                        if (!response.transactionSuccess()) {
                            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
                            return List.of(AnvilGUI.ResponseAction.close());
                        }

                        HashMap<Integer, ItemStack> drop = player.getInventory().addItem();
                        for (ItemStack item : drop.values()) {
                            player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), item);
                        }

                        Logs.info("Gracz " + player.getName() + " wypłacił " + economy.format(amount) + " ze swojego konta. (Na ziemi? " + (drop.isEmpty() ? "Nie" : "Tak") + ")");

                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie wypłacono &9" + economy.format(amount) + "&b z twojego konta."));
                        return List.of(AnvilGUI.ResponseAction.close());
                    })
                    .open(player);
        }
    }

    private void finishTransfer(@NotNull Player player, double amount) {
        new AnvilGUI.Builder()
                .jsonTitle(Utils.getJsonByLegacyString(Prefix.ECONOMY + "Podaj nick"))
                .text("Wpisz tutaj...")
                .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Podaj nick odbiorcy...", "&bKliknij &9ESC&b, aby wyjść", "&bKliknij przedmiot po prawej, aby zaakceptować"))
                .plugin(plugin)
                .interactableSlots(AnvilGUI.Slot.OUTPUT)
                .onClick((anvilSlot, state) -> {
                    if (anvilSlot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    UUID target = plugin.getOfflinePlayersManager().getIdByName(state.getText());
                    if (target == null) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Zły nick!"));
                    }
                    if (target.equals(player.getUniqueId())) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("To twój nick!"));
                    }

                    EconomyManager economy = plugin.getEconomyManager();
                    if (!economy.has(player, amount)) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cBrak środków!"));
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    EconomyResponse r1 = economy.withdrawPlayer(player, amount);
                    if (!r1.transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    EconomyResponse r2 = economy.depositPlayer(Bukkit.getOfflinePlayer(target), amount);
                    if (!r2.transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Zgłoś się do administratora, żeby odzyskać swoje pieniądze. Przepraszamy."));
                        Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + economy.format(amount) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        return List.of(AnvilGUI.ResponseAction.close());
                    }

                    Logs.info("Gracz " + player.getName() + " przelał " + economy.format(amount) + " graczowi " + state.getText() + ".");

                    player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie przelano " + economy.format(amount) + " graczowi " + state.getText() + "."));
                    informPlayer(target, player.getName(), amount, Type.ADD);
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .open(player);
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("economy", "Wyświetla saldo twojego konta")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(event.getOption("incognito", "False", OptionMapping::getAsString).equals("True")).queue();
        InteractionHook hook = event.getHook();
        User user = event.getUser();
        AccountsManager manager = plugin.getAccountsManager();
        Utils.async(() -> {
            if (!manager.isLinked(user)) {
                hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = manager.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            EconomyManager economy = plugin.getEconomyManager();
            hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Saldo twojego konta: `" + economy.format(economy.getBalance(Bukkit.getOfflinePlayer(uuid))) + "`").queue();
        });
        return 5;
    }

    private void informPlayer(@NotNull UUID uuid, @NotNull String executor, double amount, @NotNull Type type) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + switch (type) {
                case ADD -> "Na twoje konto zostało przelane &9" + plugin.getEconomyManager().format(amount) + "&b przez &9" + executor + "&b.";
                case REMOVE -> "Z twojego konta zostało zabrane &9" + plugin.getEconomyManager().format(amount) + "&b przez &9" + executor + "&b.";
                case SET -> "Saldo twojego konta zostało ustawione na &9" + plugin.getEconomyManager().format(amount) + "&b przez &9" + executor + "&b.";
                case UNKNOWN -> null;
            }));
            return;
        }
        Utils.async(() -> {
            UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
            if (id == null) return;
            JDA jda = plugin.getJda();
            if (jda == null) return;
            jda.retrieveUserById(id.getId()).queue(
                    user -> DiscordUtils.sendPrivateMessage(user, Prefix.ECONOMY.getDiscord() + switch (type) {
                        case ADD -> "Na twoje konto zostało przelane **" + plugin.getEconomyManager().format(amount) + "** przez **" + executor + "**.";
                        case REMOVE -> "Z twojego konta zostało zabrane **" + plugin.getEconomyManager().format(amount) + "** przez **" + executor + "**.";
                        case SET -> "Saldo twojego konta zostało ustawione na **" + plugin.getEconomyManager().format(amount) + "** przez **" + executor + "**.";
                        case UNKNOWN -> null;
                    }),
                    failure -> {}
            );
        });
    }

    private enum Type {
        UNKNOWN,
        ADD,
        REMOVE,
        SET
    }
}
