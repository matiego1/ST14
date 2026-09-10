package me.matiego.st14.commands.minecraft;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.managers.HomeManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomeCommand implements CommandHandler.Minecraft {
    public HomeCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("home");
        if (command == null) {
            Logs.warning("The command /home does not exist in the plugin.yml file and cannot be registered.");
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz."));
            return 0;
        }
        if (args.length != 0) return -1;
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "home", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        Utils.async(() -> {
            HomeManager manager = plugin.getHomeManager();
            UUID uuid = player.getUniqueId();
            List<ActionButton> actions = new ArrayList<>();
            List<DialogBody> body = new ArrayList<>();

            if (manager.isHomeLocationSet(uuid)) {
                body.add(DialogBody.plainMessage(Utils.getComponentByString("&6Twój dom:\n\n" + parseLocationToString(manager.getHomeLocation(uuid)))));
                actions.add(ActionButton.create(
                        Utils.getComponentByString("&aTeleportuj się do domu"),
                        null,
                        Utils.DIALOG_BUTTON_WIDTH,
                        DialogAction.customClick((view, audience) -> Utils.async(() -> teleportToHome(player)), Utils.BUTTON_OPTIONS)
                ));
                actions.add(ActionButton.create(
                        Utils.getComponentByString("&cUsuń swój dom"),
                        null,
                        Utils.DIALOG_BUTTON_WIDTH,
                        DialogAction.customClick((view, audience) -> Utils.async(() -> deleteHome(player)), Utils.BUTTON_OPTIONS)
                ));
            } else {
                body.add(DialogBody.plainMessage(Utils.getComponentByString("&cNie ustawiłeś jeszcze swojego domu!")));
                actions.add(ActionButton.create(
                        Utils.getComponentByString("&aUstaw swój dom"),
                        null,
                        Utils.DIALOG_BUTTON_WIDTH,
                        DialogAction.customClick((view, audience) -> Utils.async(() -> setHome(player)), Utils.BUTTON_OPTIONS)
                ));
            }

            DialogBase base = DialogBase.create(
                    Utils.getComponentByString(Prefix.HOME + "Zarządzaj domem"),
                    null,
                    true,
                    true,
                    DialogBase.DialogAfterAction.CLOSE,
                    body,
                    List.of()
            );

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.multiAction(actions, Utils.getDialogExitButton("Gotowe"), 1))
            );
            Utils.sync(() -> player.showDialog(dialog));
        });
        return 5;
    }

    private @NotNull String parseLocationToString(@Nullable Location location) {
        if (location == null) return "&cNapotkano błąd przy wczytywaniu położenia";
        return "&eX: &a" + Utils.round(location.getX(), 2) + "&e Y: &a" + Utils.round(location.getY(), 2) + "&e Z: &a" + Utils.round(location.getZ(), 2) + "\n" +
                "&eŚwiat: &a" + Utils.getWorldName(location.getWorld());
    }

    private void teleportToHome(Player player) {
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "home", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNie możesz użyć tej komendy w tym świecie."));
            return;
        }

        Location location = plugin.getHomeManager().getHomeLocation(player.getUniqueId());
        if (location == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
            return;
        }
        teleportPlayer(player, location);
    }

    private void deleteHome(Player player) {
        if (plugin.getHomeManager().removeHome(player.getUniqueId())) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Pomyślnie usunięto twój dom."));
            Logs.info("Gracz " + player.getName() + " usunął swój dom.");
        } else {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
        }
    }

    private void setHome(Player player) {
        double creation = Math.max(0, Utils.round(plugin.getConfig().getDouble("home.creation"), 2));
        EconomyManager economy = plugin.getEconomyManager();
        if (creation != 0) {
            if (!economy.withdrawPlayer(player, creation).transactionSuccess()) {
                player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cUstawienie domu kosztuje " + economy.format(creation) + ", a masz tylko " + economy.format(economy.getBalance(player)) + "."));
                return;
            }
        }

        if (plugin.getHomeManager().setHomeLocation(player.getUniqueId(), player.getLocation())) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Pomyślnie ustawiono twój dom za " + economy.format(creation) + "."));
            Logs.info("Gracz " + player.getName() + " ustawił swój dom. (`" + player.getLocation() + "`)");
        } else {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
            if (creation != 0 && !economy.depositPlayer(player, creation).transactionSuccess()) {
                player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&c&lNapotkano niespodziewany błąd przy zwracaniu pobranych pieniędzy. Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + economy.format(creation) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
            }
        }
    }

    private void teleportPlayer(@NotNull Player player, @NotNull Location location) {
        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cProces teleportowania już jest aktywny!"));
            return;
        }

        double distance;
        try {
            distance = player.getLocation().distance(location);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Utils.getComponentByString("&cTwój dom jest w innym świecie!"));
            return;
        }

        if (distance <= plugin.getConfig().getInt("home.min", 0)) {
            player.sendMessage(Utils.getComponentByString("&cJesteś za blisko twojego domu!"));
            return;
        }

        final double cost = Utils.round(plugin.getConfig().getDouble("home.cost") * (distance / 16), 2);

        EconomyManager economy = plugin.getEconomyManager();
        if (cost != 0 && !economy.has(player, cost)) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(economy.getBalance(player))));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Za 5 sekund zostaniesz przeteleportowany do swojego domu. Nie ruszaj się!"));

        Utils.async(() -> {
            try {
                switch (plugin.getTeleportsManager().teleport(player, location, 5, () -> {
                    if (cost == 0) return true;
                    EconomyResponse response = economy.withdrawPlayer(player, cost);
                    if (response.transactionSuccess()) return true;
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(response.balance)));
                    return false;
                }).get()) {
                    case SUCCESS -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Przeteleportowałeś się do swojego domu za " + economy.format(cost)));
                        Logs.info("Gracz " + player.getName() + " przeteleportował do swojego domu.");
                    }
                    case PLAYER_MOVED -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Teleportowanie anulowane, poruszyłeś się."));
                    case CANCELLED_ANTY_LOGOUT -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Nie możesz teleportować się z aktywnym anty-logout'em."));
                    case ALREADY_ACTIVE -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Proces teleportowania już został rozpoczęty."));
                    case PLUGIN_DISABLED -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Teleportowanie anulowane."));
                    case CANCELLED_AFTER_COUNTDOWN -> {}
                    case FAILURE -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Napotkano błąd teleportowaniu."));
                        if (!plugin.getEconomyManager().depositPlayer(player, cost).transactionSuccess()) {
                            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                            Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + plugin.getEconomyManager().format(cost) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        }
                    }
                }
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                Logs.error("An error occurred while teleporting player", e);
            }
        });
    }
}
