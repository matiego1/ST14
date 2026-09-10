package me.matiego.st14.commands.minecraft;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WorldsCommand implements CommandHandler.Minecraft {
    public WorldsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("worlds");
        if (command == null) {
            Logs.warning("The command /worlds does not exist in the plugin.yml file and cannot be registered.");
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
            sender.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dTej komendy może użyć tylko gracz."));
            return 0;
        }

        List<Pair<World, Component>> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            String material = plugin.getConfig().getString("worlds-command." + world.getName() + ".material");
            if (material == null) continue;
            Component component = MiniMessage.miniMessage().deserialize("<sprite:" + material + ">");
            worlds.add(new Pair<>(world, component));
        }

        if (worlds.isEmpty()) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNie znaleziono światów do których możesz się przenieść."));
            return 5;
        }

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.WORLDS + "Wybierz świat"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(),
                List.of()
        );

        List<ActionButton> actions = new ArrayList<>();
        for (Pair<World, Component> pair : worlds) {
            Component label = pair.getSecond().append(Utils.getComponentByString("&f " + Utils.getWorldName(pair.getFirst())));
            actions.add(ActionButton.create(
                    label,
                    Utils.getComponentByString("&aKliknij, aby się przenieść!"),
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> handleWorldChoice(pair.getFirst(), player), Utils.BUTTON_OPTIONS)
            ));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(actions, Utils.getDialogExitButton("Anuluj"), 1))
        );
        player.showDialog(dialog);
        return 6;
    }

    private void handleWorldChoice(@NotNull World target, @NotNull Player player) {
        World world = player.getWorld();
        World.Environment env = world.getEnvironment();
        if (env == World.Environment.NETHER) {
            World normalWorld = Bukkit.getWorld(world.getName().replace("_nether", ""));
            if (normalWorld != null) world = normalWorld;
        } else if (env == World.Environment.THE_END) {
            World normalWorld = Bukkit.getWorld(world.getName().replace("_the_end", ""));
            if (normalWorld != null) world = normalWorld;
        }

        if (target.equals(world)) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dJuż jesteś w tym świecie."));
            return;
        }
        if (!hasPermission(player, target)) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNie masz uprawnień, aby przenieść się do tego świata."));
            return;
        }

        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dProces teleportowania już został rozpoczęty"));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "Zostaniesz przeteleportowany za 5 sekund. Nie ruszaj się!"));

        plugin.getWorldsLastLocationManager().setLastLocation(player.getUniqueId(), world, player.getLocation());
        Location loc = plugin.getWorldsLastLocationManager().getLastLocation(player.getUniqueId(), target);

        Utils.async(() -> {
            try {
                String msg = switch (plugin.getTeleportsManager().teleport(player, loc, 5, () -> hasPermission(player, loc.getWorld())).get()) {
                    case SUCCESS -> null;
                    case PLAYER_MOVED -> "&dTeleportowanie anulowane, poruszyłeś się.";
                    case ALREADY_ACTIVE -> "&dProces teleportowania już został rozpoczęty.";
                    case CANCELLED_AFTER_COUNTDOWN -> "&dNie masz uprawnień, aby przenieść się do tego świata.";
                    case PLUGIN_DISABLED -> "&dTeleportowanie anulowane.";
                    case CANCELLED_ANTY_LOGOUT -> "&dNie możesz się teleportować z aktywnym anty-logout'em.";
                    case FAILURE -> "&dNapotkano niespodziewany błąd. Spróbuj ponownie.";
                };
                if (msg == null) {
                    Utils.broadcastMessage(
                            player,
                            Prefix.WORLDS,
                            "Przeteleportowano pomyślnie.",
                            "Gracz &1" + player.getName() + "&3 przeszedł do świata &1" + Utils.getWorldName(target) + "&3!",
                            "Gracz **" + player.getName() + "** przeszedł do świata **" + Utils.getWorldName(target) + "**!"
                    );
                    return;
                }
                player.sendMessage(Utils.getComponentByString(msg));
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNapotkano niespodziewany błąd! Spróbuj ponownie."));
            }
        });
    }

    private boolean hasPermission(@NotNull Player player, @NotNull World world) {
        if (player.isOp()) return true;
        if (player.hasPermission("st14.worlds." + world.getName())) return true;
        return !plugin.getConfig().getBoolean("worlds-command." + world.getName() + ".private");
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return Utils.getPlainTextByComponent(name);
    }
}
