package me.matiego.st14.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.AccountsManager;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.managers.PremiumManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
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
        if (!(sender instanceof Player player) || (args.length > 0 && (player.isOp() || player.hasPermission("st14.economy.admin")))) {
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
                    amount = Utils.round(Double.parseDouble(args[2].replace(",", ".")), 2);
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

            if (Utils.checkIfCanNotExecuteCommandInWorld(player, "economy", '.')) {
                Utils.async(() -> player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Saldo twojego konta: &9" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)) + "&b. Nie możesz wykonać innych operacji w tym świecie.")));
                return 5;
            }

            DialogBase base = DialogBase.create(
                    Utils.getComponentByString(Prefix.ECONOMY + "Twoje konto"),
                    null,
                    true,
                    true,
                    DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(Utils.getComponentByString("&bSaldo twojego konta:\n&e" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player))))),
                    List.of()
            );

            List<ActionButton> actions = new ArrayList<>();
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&bWypłać pieniądze"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> handleWithdrawal(player, null, null), Utils.BUTTON_OPTIONS)
            ));
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&bPrzelej pieniądze"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> handleTransfer(player, null, null, null), Utils.BUTTON_OPTIONS)
            ));
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&bKup główki"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.staticAction(ClickEvent.runCommand("st14:heads"))
            ));
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&bKup status premium"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> handlePremiumStatusPurchase(player, null, null), Utils.BUTTON_OPTIONS)
            ));

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.multiAction(actions, Utils.getDialogExitButton("Gotowe"), 1))
            );
            player.showDialog(dialog);

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

    private void handleWithdrawal(@NotNull Player player, @Nullable String errorMessage, @Nullable String previousText) {
        EconomyManager economy = plugin.getEconomyManager();

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bSaldo twojego konta:\n&e" + economy.format(economy.getBalance(player)))));
        double taxPercent = Math.max(0, plugin.getConfig().getDouble("economy.banknote-tax.percent", 0)) * 100;
        double taxMin = Math.max(0, plugin.getConfig().getDouble("economy.banknote-tax.min", 0));
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bPodatek: &e" + Utils.formatDouble(taxPercent) + "% (min. " + economy.format(taxMin) + ")")));
        if (errorMessage != null) {
            body.add(DialogBody.plainMessage(Utils.getComponentByString("&cBłąd: " + errorMessage)));
        }

        DialogInput input = DialogInput.text(
                "amount",
                Utils.DIALOG_BUTTON_WIDTH,
                Utils.getComponentByString("&bWartość:"),
                true,
                previousText == null ? "" : previousText,
                8,
                null
        );

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.ECONOMY + "Wypłać pieniądze"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                body,
                List.of(input)
        );

        ActionButton yes = ActionButton.create(
                Utils.getComponentByString("&aWypłać"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                DialogAction.customClick((view, audience) -> finishWithdrawal(player, view.getText("amount")), Utils.BUTTON_OPTIONS)
        );
        ActionButton no = ActionButton.create(
                Utils.getComponentByString("&cAnuluj"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                null
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no))
        );
        player.showDialog(dialog);
    }

    private void finishWithdrawal(@NotNull Player player, @Nullable String amountString) {
        if (amountString == null) amountString = "";

        double amount;
        try {
            amount = Utils.round(Double.parseDouble(amountString.replace(",", ".").replace("$", "")), 2);
        } catch (Exception e) {
            handleWithdrawal(player, "Podaj poprawną kwotę", amountString);
            return;
        }
        if (amount <= 0d) {
            handleWithdrawal(player, "Podaj poprawną kwotę", amountString);
            return;
        }
        if (amount > 500d) {
            handleWithdrawal(player, "Możesz wypłacić tylko 500$", amountString);
            return;
        }

        amount = Utils.round(amount, 2);
        double tax = Utils.round(Math.max(
                Math.max(0, plugin.getConfig().getDouble("economy.banknote-tax.min", 0)),
                amount * Math.max(0, plugin.getConfig().getDouble("economy.banknote-tax.percent", 0))
        ), 2);

        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.has(player, amount + tax)) {
            handleWithdrawal(player, "Brak środków na koncie", amountString);
            return;
        }

        final double finalAmount = amount;
        Utils.async(() -> {
            ItemStack banknote = plugin.getBanknoteManager().createBanknote(finalAmount);
            if (banknote == null) {
                player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
                return;
            }

            EconomyResponse response = economy.withdrawPlayer(player, finalAmount + tax);
            if (!response.transactionSuccess()) {
                player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
                return;
            }

            HashMap<Integer, ItemStack> drop = player.getInventory().addItem(banknote);
            for (ItemStack item : drop.values()) {
                player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), item);
            }

            Logs.info("Gracz " + player.getName() + " wypłacił " + economy.format(finalAmount) + " ze swojego konta za opłatą " + economy.format(tax) + ". (Na ziemi? " + (drop.isEmpty() ? "Nie" : "Tak") + ")");

            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie wypłacono &9" + economy.format(finalAmount) + "&b z twojego konta za opłatą &9" + economy.format(tax)));
        });
    }

    private void handleTransfer(@NotNull Player player, @Nullable String errorMessage, @Nullable String previousAmount, @Nullable String previousReceiver) {
        EconomyManager economy = plugin.getEconomyManager();

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bSaldo twojego konta:\n&e" + economy.format(economy.getBalance(player)))));
        double taxPercent = Math.max(0, plugin.getConfig().getDouble("economy.transfer-tax.percent", 0)) * 100;
        double taxMin = Math.max(0, plugin.getConfig().getDouble("economy.transfer-tax.min", 0));
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bPodatek: &e" + Utils.formatDouble(taxPercent) + "% (min. " + economy.format(taxMin) + ")")));
        if (errorMessage != null) {
            body.add(DialogBody.plainMessage(Utils.getComponentByString("&cBłąd: " + errorMessage)));
        }

        DialogInput amount = DialogInput.text(
                "amount",
                Utils.DIALOG_BUTTON_WIDTH,
                Utils.getComponentByString("&bWartość:"),
                true,
                previousAmount == null ? "" : previousAmount,
                8,
                null
        );
        DialogInput receiver = DialogInput.text(
                "receiver",
                Utils.DIALOG_BUTTON_WIDTH,
                Utils.getComponentByString("&bOdbiorca:"),
                true,
                previousReceiver == null ? "" : previousReceiver,
                16,
                null
        );

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.ECONOMY + "Przelej pieniądze"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                body,
                List.of(amount, receiver)
        );

        ActionButton yes = ActionButton.create(
                Utils.getComponentByString("&aPrzelej"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                DialogAction.customClick((view, audience) -> finishTransfer(player, view.getText("amount"), view.getText("receiver")), Utils.BUTTON_OPTIONS)
        );
        ActionButton no = ActionButton.create(
                Utils.getComponentByString("&cAnuluj"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                null
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no))
        );
        player.showDialog(dialog);
    }

    private void finishTransfer(@NotNull Player player, @Nullable String amountString, @Nullable String receiverName) {
        if (amountString == null) amountString = "";
        if (receiverName == null) receiverName = "";

        double amount;
        try {
            amount = Utils.round(Double.parseDouble(amountString.replace(",", ".").replace("$", "")), 2);
        } catch (Exception e) {
            handleTransfer(player, "Podaj poprawną kwotę", amountString, receiverName);
            return;
        }
        if (amount <= 0d) {
            handleTransfer(player, "Podaj poprawną kwotę", amountString, receiverName);
            return;
        }
        if (amount > 1000d) {
            handleTransfer(player, "Możesz przelać tylko 1000$", amountString, receiverName);
            return;
        }

        UUID receiver = plugin.getOfflinePlayersManager().getIdByName(receiverName);
        if (receiver == null) {
            handleTransfer(player, "Podaj poprawny nick odbiorcy",  amountString, receiverName);
            return;
        }
        if (receiver.equals(player.getUniqueId())) {
            handleTransfer(player, "Podałeś swój nick", amountString, receiverName);
            return;
        }

        amount = Utils.round(amount, 2);
        double tax = Utils.round(Math.max(
                Math.max(0, plugin.getConfig().getDouble("economy.transfer-tax.min", 0)),
                amount * Math.max(0, plugin.getConfig().getDouble("economy.transfer-tax.percent", 0))
        ), 2);

        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.has(player, amount + tax)) {
            handleTransfer(player, "Brak środków na koncie", amountString, receiverName);
            return;
        }

        EconomyResponse r1 = economy.withdrawPlayer(player, amount + tax);
        if (!r1.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Spróbuj później."));
            return;
        }
        EconomyResponse r2 = economy.depositPlayer(Bukkit.getOfflinePlayer(receiver), amount);
        if (!r2.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "&cNapotkano niespodziewany błąd. Zgłoś się do administratora, żeby odzyskać swoje pieniądze. Przepraszamy."));
            Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + economy.format(amount + tax) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
            return;
        }

        Logs.info("Gracz " + player.getName() + " przelał " + economy.format(amount) + " graczowi " + receiverName + " za opłatą " + economy.format(tax) + ".");

        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie przelano " + economy.format(amount) + " graczowi " + receiverName + " za opłatą " + economy.format(tax) + "."));
        informPlayer(receiver, player.getName(), amount, Type.ADD);
    }

    private void handlePremiumStatusPurchase(@NotNull Player player, @Nullable String errorMessage, @Nullable String previousText) {
        EconomyManager economy = plugin.getEconomyManager();

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bStatus premium daje priorytetowy dostęp do serwera i &eżółty&b nick na liście graczy.")));
        double costConst = plugin.getConfig().getDouble("premium.cost-const", 0);
        double costMin = Math.max(plugin.getConfig().getDouble("premium.min-cost"), 0);
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bKoszt:&e sqrt(czas w godzinach) * " + economy.format(costConst) + " (min. " + economy.format(costMin) + ")")));
        body.add(DialogBody.plainMessage(Utils.getComponentByString("&bSaldo twojego konta:\n&e" + economy.format(economy.getBalance(player)))));
        if (errorMessage != null) {
            body.add(DialogBody.plainMessage(Utils.getComponentByString("&cBłąd: " + errorMessage)));
        }

        DialogInput input = DialogInput.text(
                "time",
                200,
                Utils.getComponentByString("&bCzas:"),
                true,
                previousText == null ? "np. 3d10h" : previousText,
                10,
                null
        );

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.ECONOMY + "Kup status premium"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                body,
                List.of(input)
        );

        ActionButton yes = ActionButton.create(
                Utils.getComponentByString("&aAkceptuj"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                DialogAction.customClick((view, audience) -> finishPremiumStatusPurchase(player, view.getText("time")), Utils.BUTTON_OPTIONS)
        );
        ActionButton no = ActionButton.create(
                Utils.getComponentByString("&cAnuluj"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                null
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no))
        );
        player.showDialog(dialog);
    }

    private void finishPremiumStatusPurchase(@NotNull Player player, @Nullable String timeString) {
        if (timeString == null) timeString = "";

        PremiumManager premium = plugin.getPremiumManager();
        if (premium.isSuperPremium(player.getUniqueId())) {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNie możesz kupić statusu premium, ponieważ jesteś graczem super premium."));
            return;
        }

        long time = 0;
        try {
            time = Utils.parseStringToMillis(timeString.replace(" ", "").toLowerCase());
            time /= (3600 * 1000);
        } catch (Exception ignored) {}

        if (time <= 0) {
            handlePremiumStatusPurchase(player, "Zły czas. Minimalny czas: 1h. Przykładowy czas: 3d10h", timeString);
            return;
        }

        double amount = Math.max(plugin.getConfig().getDouble("premium.min-cost", 0), plugin.getConfig().getDouble("premium.cost-const", 0) * Math.sqrt(time));
        if (amount <= 0) {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cKupowanie statusu premium jest wyłączone."));
            return;
        }

        time *= 3600 * 1000;

        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.has(player, amount)) {
            handlePremiumStatusPurchase(player, "Brak środków. Potrzebujesz " + economy.format(amount), timeString);
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNapotkano niespodziewany błąd. Spróbuj później."));
            return;
        }

        if (premium.extend(player.getUniqueId(), time)) {
            long remaining = premium.getRemainingTime(player.getUniqueId());
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Pomyślnie przedłużono twój status premium o &6" + Utils.parseMillisToString(time, false) + "&b za &6" + economy.format(amount)));

            Logs.info("Gracz " + player.getName() + " przedłużył status premium o `" + Utils.parseMillisToString(time, false) + "` za `" + economy.format(amount) + "`" + (remaining > 0 ? ". Pozostało: " + Utils.parseMillisToString(remaining, false) : ""));
        } else {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Napotkano niespodziewany błąd."));
            if (!economy.depositPlayer(player, amount).transactionSuccess()) {
                player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + plugin.getEconomyManager().format(amount) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
            }
        }
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("economy", "Wyświetla saldo twojego konta")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setContexts(InteractionContextType.GUILD);
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

    public void informPlayer(@NotNull UUID uuid, @NotNull String executor, double amount, @NotNull Type type) {
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

    public enum Type {
        UNKNOWN,
        ADD,
        REMOVE,
        SET
    }
}
