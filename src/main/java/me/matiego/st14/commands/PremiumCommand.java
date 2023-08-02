package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.PremiumManager;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PremiumCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public PremiumCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("premium");
        if (command == null) {
            Logs.warning("The command /premium does not exist in the plugin.yml file and cannot be registered.");
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
        PremiumManager manager = plugin.getPremiumManager();

        if (args.length < 2) return -1;

        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[1]);
        if (uuid == null) {
            sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cZły nick!"));
            return getCooldown(sender, 3);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            Utils.async(() -> {
                long time = manager.getRemainingTime(uuid);
                if (manager.isSuperPremium(uuid)) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Ten gracz jest graczem super premium."));
                    return;
                }
                if (time > 0) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Temu graczowi pozostało &6" + Utils.parseMillisToString(time, false) + "&b do wygaśnięcia statusu premium."));
                } else {
                    sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Ten gracz nie jest premium."));
                }
            });
            return getCooldown(sender, 5);
        }

        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cBrak uprawnień!"));
            return 3;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            Utils.async(() -> {
                if (manager.remove(uuid)) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Pomyślnie usunięto status premium gracza."));
                } else {
                    sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                }
            });
            return 0;
        }

        if (args.length != 3) return -1;

        long time;
        try {
            time = Utils.parseStringToMillis(args[2]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cZły czas."));
            return 0;
        }
        if (time <= 0) {
            sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cZły czas."));
            return 0;
        }

        switch (args[0]) {
            case "extend" -> {
                Utils.async(() -> {
                    if (manager.extend(uuid, time)) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Pomyślnie wydłużono czas statusu premium gracza."));
                    } else {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    }
                });
                return 0;
            }
            case "set" -> {
                Utils.async(() -> {
                    if (manager.set(uuid, time)) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Pomyślnie ustawiono czas statusu premium gracza."));
                    } else {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    }
                });
                return 0;
            }
            case "reduce" -> {
                Utils.async(() -> {
                    if (manager.reduce(uuid, time)) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Pomyślnie skrócono czas statusu premium gracza."));
                    } else {
                        sender.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    }
                });
                return 0;
            }
        }
        return -1;
    }

    private int getCooldown(@NotNull CommandSender sender, int cooldown) {
        if (!(sender instanceof Player player)) return 0;
        return player.isOp() ? 0 : cooldown;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("get", "extend", "reduce", "set", "remove");
        }
        if (args.length == 2) {
            return plugin.getOfflinePlayersManager().getNames();
        }
        if (!(args[0].equalsIgnoreCase("extend") || args[0].equalsIgnoreCase("reduce") || args[0].equalsIgnoreCase("set"))) return new ArrayList<>();
        if (args.length == 3) {
            List<String> list = plugin.getConfig().getStringList("premium.popular-times");
            return list.isEmpty() ? Arrays.asList("30s", "30m", "1h", "1h30m", "1d") : list;
        }
        return new ArrayList<>();
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("premium", "Sprawdź status premium gracza")
                .addOptions(
                        new OptionData(OptionType.STRING, "gracz", "nick gracza, którego status premium chcesz sprawdzić", true, true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        String playerName = event.getOption("gracz", OptionMapping::getAsString);
        if (playerName == null) {
            event.reply("Zły nick.").setEphemeral(ephemeral).queue();
            return 3;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            event.reply("Zły nick.").setEphemeral(ephemeral).queue();
            return 3;
        }

        event.deferReply(ephemeral).queue();
        InteractionHook hook = event.getHook();

        PremiumManager manager = plugin.getPremiumManager();
        Utils.async(() -> {
            if (manager.isSuperPremium(player.getUniqueId())) {
                hook.sendMessage("Gracz **" + player.getName() + "** jest graczem super premium.").queue();
                return;
            }
            long time = manager.getRemainingTime(player.getUniqueId());
            if (time > 0) {
                hook.sendMessage("Graczowi pozostało " + Utils.parseMillisToString(time, false) + " do wygaśnięcia statusu premium.").queue();
            } else {
                hook.sendMessage("Ten gracz nie jest graczem premium.").queue();
            }
        });
        return 5;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        if (!event.getFocusedOption().getName().equals("gracz")) return;
        event.replyChoices(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
