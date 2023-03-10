package me.matiego.st14.commands;

import me.matiego.st14.AccountsManager;
import me.matiego.st14.Economy;
import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
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
import org.bukkit.ChatColor;
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
                sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne u??ycie: /economy [add|remove|set|get] <gracz> <ilo????*>"));
                return 0;
            }
            Economy economy = plugin.getEconomy();
            Utils.async(() -> {
                args[0] = args[0].toLowerCase();
                if (args[0].equals("get")) {
                    if (args.length != 2) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne u??ycie: /economy get <gracz>"));
                        return;
                    }
                    UUID uuid = plugin.getOfflinePlayers().getIdByName(args[1]);
                    if (uuid == null) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cTen gracz nie jest online."));
                        return;
                    }
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Gracz &9" + args[1] + "&b ma &9" + economy.format(economy.getBalance(Bukkit.getOfflinePlayer(uuid))) + "&b."));
                    return;
                }

                if (args.length != 3) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne u??ycie: /economy [add|remove|set] <gracz> <ilo????>"));
                    return;
                }
                if (!(args[0].equals("add") || args[0].equals("remove") || args[0].equals("set"))) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne u??ycie: /economy [add|remove|set] <gracz> <ilo????>"));
                    return;
                }

                UUID uuid = plugin.getOfflinePlayers().getIdByName(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNieznany gracz."));
                    return;
                }

                double amount;
                try {
                    amount = Utils.round(Double.parseDouble(args[2]), 2);
                } catch (Exception e) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cPodaj poprawn?? ilo???? pieni??dzy."));
                    return;
                }
                if (amount < 0d) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNie mo??na ustawi?? ujemnej warto??ci."));
                    return;
                }

                EconomyResponse response = switch (args[0]) {
                    case "add" -> economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                    case "remove" -> economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                    case "set" -> economy.setBalance(Bukkit.getOfflinePlayer(uuid), amount);
                    default -> new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
                };

                if (!response.transactionSuccess()) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany b????d."));
                    return;
                }

                Logs.info(
                        "Administrator " +
                        switch (args[0]) {
                            case "add" -> "zwi??kszy??";
                            case "remove" -> "zmniejszy??";
                            case "set" -> "ustawi??";
                            default -> null;
                        } +
                        " stan konta gracza " + args[1] + " o/na " + economy.format(amount) + "."
                );

                sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomy??lnie zmieniono saldo konta gracza " + args[1] + " (" + uuid +") na " + economy.format(response.balance)));
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
                Utils.async(() -> player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Saldo twojego konta: &9" + plugin.getEconomy().format(plugin.getEconomy().getBalance(player)))));
                return 5;
            }

            Inventory inv = GUI.createInventory(9, Prefix.ECONOMY + "Twoje konto");
            inv.setItem(0, GUI.createGuiItem(Material.DISPENSER, "&9Przelew", "&bPrzelej pieni??dze innemu graczowi"));
            inv.setItem(1, GUI.createGuiItem(Material.PAPER, "&9Wyp??ata", "&bWyp??a?? pieni??dze w postaci banknotu"));
            Utils.async(() -> inv.setItem(4, GUI.createGuiItem(Material.DIAMOND, "&9Saldo konta", "&b" + plugin.getEconomy().format(plugin.getEconomy().getBalance(player)))));
            inv.setItem(7, GUI.createGuiItem(Material.VILLAGER_SPAWN_EGG, "&9Sklep", "&cMo??e wkr??tce!"));
            inv.setItem(8, GUI.createGuiItem(Material.CREEPER_HEAD, "&9Kup g????wk??", "&cJu?? wkr??tce!"));
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
                return plugin.getOfflinePlayers().getNames();
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
                    .title(ChatColor.translateAlternateColorCodes('&', Prefix.ECONOMY + "Wpisz warto????"))
                    .text("Wpisz tutaj...")
                    .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Wprowad?? warto????...", "&bKliknij &9ESC&b, aby wyj????", "&bKliknij przedmiot po prawej, aby zaakceptowa??"))
                    .plugin(plugin)
                    .interactableSlots(AnvilGUI.Slot.INPUT_RIGHT)
                    .onComplete(completion -> {
                        double amount;
                        try {
                            amount = Utils.round(Double.parseDouble(completion.getText()), 2);
                        } catch (Exception e) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczb??!"));
                        }
                        if (amount <= 0d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczb??!"));
                        }
                        amount = Utils.round(amount, 2);

                        Economy economy = plugin.getEconomy();
                        if (!economy.has(player, amount)) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Brak ??rodk??w"));
                        }

                        finishTransfer(player, amount);
                        return List.of(AnvilGUI.ResponseAction.close());
                    })
                    .open(player);
        } else if (slot == 1) {
            new AnvilGUI.Builder()
                    .title(ChatColor.translateAlternateColorCodes('&', Prefix.ECONOMY + "Wpisz warto????"))
                    .text("Wpisz tutaj...")
                    .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Wprowad?? warto????...", "&bKliknij &9ESC&b, aby wyj????", "&bKliknij przedmiot po prawej, aby zaakceptowa??"))
                    .plugin(plugin)
                    .interactableSlots(AnvilGUI.Slot.INPUT_RIGHT)
                    .onComplete(completion -> {
                        double amount;
                        try {
                            amount = Utils.round(Double.parseDouble(completion.getText()), 2);
                        } catch (Exception e) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczb??!"));
                        }
                        if (amount <= 0d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Podaj liczb??!"));
                        }
                        if (amount >= 500d) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Za du??a kwota!"));
                        }
                        amount = Utils.round(amount, 2);

                        Economy economy = plugin.getEconomy();
                        if (!economy.has(player, amount)) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Brak ??rodk??w"));
                        }
                        EconomyResponse response = economy.withdrawPlayer(player, amount);
                        if (!response.transactionSuccess()) {
                            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany b????d. Spr??buj p????niej."));
                            return List.of(AnvilGUI.ResponseAction.close());
                        }

                        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(plugin.getBanknoteManager().createBanknote(amount));
                        for (ItemStack item : drop.values()) {
                            player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), item);
                        }

                        Logs.info("Gracz " + player.getName() + " wyp??aci?? " + economy.format(amount) + " ze swojego konta. (Na ziemi? " + (drop.isEmpty() ? "Nie" : "Tak") + ")");

                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomy??lnie wyp??acono &9" + economy.format(amount) + "&b z twojego konta."));
                        return List.of(AnvilGUI.ResponseAction.close());
                    })
                    .open(player);
        }
    }

    private void finishTransfer(@NotNull Player player, double amount) {
        new AnvilGUI.Builder()
                .title(ChatColor.translateAlternateColorCodes('&', Prefix.ECONOMY + "Podaj nick"))
                .text("Wpisz tutaj...")
                .itemLeft(GUI.createGuiItem(Material.PAPER, "&9Podaj nick odbiorcy...", "&bKliknij &9ESC&b, aby wyj????", "&bKliknij przedmiot po prawej, aby zaakceptowa??"))
                .plugin(plugin)
                .interactableSlots(AnvilGUI.Slot.INPUT_RIGHT)
                .onComplete(completion -> {
                    UUID target = plugin.getOfflinePlayers().getIdByName(completion.getText());
                    if (target == null) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Z??y nick!"));
                    }
                    if (target.equals(player.getUniqueId())) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("To tw??j nick!"));
                    }

                    Economy economy = plugin.getEconomy();
                    if (!economy.has(player, amount)) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cBrak ??rodk??w!"));
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    EconomyResponse r1 = economy.withdrawPlayer(player, amount);
                    if (!r1.transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany b????d. Spr??buj p????niej."));
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    EconomyResponse r2 = economy.depositPlayer(Bukkit.getOfflinePlayer(target), amount);
                    if (!r2.transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany b????d. Zg??o?? si?? do administratora, ??eby odzyska?? swoje pieni??dze. Przepraszamy."));
                        Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") straci?? " + economy.format(amount) + " ze swojego konta! Kwota musi by?? przywr??cona r??cznie.");
                        return List.of(AnvilGUI.ResponseAction.close());
                    }

                    Logs.info("Gracz " + player.getName() + " przela?? " + economy.format(amount) + " graczowi " + completion.getText() + ".");

                    player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomy??lnie przelano " + economy.format(amount) + " graczowi " + completion.getText() + "."));
                    informPlayer(target, player.getName(), amount, Type.ADD);
                    return List.of(AnvilGUI.ResponseAction.close());
                })
                .open(player);
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("economy", "Wy??wietla saldo twojego konta")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomo???? ma by?? widoczna tylko dla ciebie", false)
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
                hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Twoje konto nie jest jeszcze po????czone z kontem minecraft! Aby je po????czy??, u??yj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = manager.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Napotkano niespodziewany b????d. Spr??buj p????niej.").queue();
                return;
            }
            Economy economy = plugin.getEconomy();
            hook.sendMessage(Prefix.ECONOMY.getDiscord() + "Saldo twojego konta: `" + economy.format(economy.getBalance(Bukkit.getOfflinePlayer(uuid))) + "`").queue();
        });
        return 5;
    }

    private void informPlayer(@NotNull UUID uuid, @NotNull String executor, double amount, @NotNull Type type) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + switch (type) {
                case ADD -> "Na twoje konto zosta??o przelane &9" + plugin.getEconomy().format(amount) + "&b przez &9" + executor + "&b.";
                case REMOVE -> "Z twojego konta zosta??o zabrane &9" + plugin.getEconomy().format(amount) + "&b przez &9" + executor + "&b.";
                case SET -> "Saldo twojego konta zosta??o ustawione na &9" + plugin.getEconomy().format(amount) + "&b przez &9" + executor + "&b.";
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
                        case ADD -> "Na twoje konto zosta??o przelane **" + plugin.getEconomy().format(amount) + "** przez **" + executor + "**.";
                        case REMOVE -> "Z twojego konta zosta??o zabrane **" + plugin.getEconomy().format(amount) + "** przez **" + executor + "**.";
                        case SET -> "Saldo twojego konta zosta??o ustawione na **" + plugin.getEconomy().format(amount) + "** przez **" + executor + "**.";
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
