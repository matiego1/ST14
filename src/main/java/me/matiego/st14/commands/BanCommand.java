package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.BansManager;
import me.matiego.st14.objects.bans.Ban;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public BanCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("ban");
        if (command == null) {
            Logs.warning("The command /coordinates does not exist in the plugin.yml file and cannot be registered.");
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
        if (args.length == 0) return -1;

        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[1]);
        if (uuid == null) {
            sender.sendMessage(Utils.getComponentByString("&cZły nick!"));
            return 0;
        }

        BansManager manager = plugin.getBansManager();

        switch (args[0]) {
            case "get" -> {
                if (args.length != 2) return -1;
                Utils.async(() -> {
                    if (!manager.isBanned(uuid)) {
                        sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest zbanowany."));
                        return;
                    }
                    Ban ban = manager.getBan(uuid);
                    if (ban == null) {
                        sender.sendMessage(Utils.getComponentByString("&cTen gracz jest zbanowany, ale nie udało się wczytać więcej informacji."));
                        return;
                    }
                    sender.sendMessage(Utils.getComponentByString("&aPozostały czas: &2" + Utils.parseMillisToString(ban.getExpiration() - Utils.now(), false) + "&a; Powód: &2" + ban.getReason()));
                });
                return 0;
            }
            case "pardon" -> {
                if (args.length != 2) return -1;
                Utils.async(() -> {
                    if (!manager.isBanned(uuid)) {
                        sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest zbanowany."));
                        return;
                    }
                    if (manager.setBan(new Ban(uuid))) {
                        sender.sendMessage(Utils.getComponentByString("&aPomyślnie odbanowano gracza."));
                    } else {
                        sender.sendMessage(Utils.getComponentByString("&cNapotkano błąd przy odbanowywaniu gracza."));
                    }
                });
                return 0;
            }
            case "set" -> {
                if (args.length < 4) return -1;
                Utils.async(() -> {
                    if (manager.isBanned(uuid)) {
                        sender.sendMessage(Utils.getComponentByString("&cTen gracz już jest zbanowany."));
                        return;
                    }

                    long time;
                    try {
                        time = Utils.parseStringToMillis(args[2]);
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Utils.getComponentByString("&cZły czas."));
                        return;
                    }

                    StringBuilder reason = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        reason.append(args[i]).append(" ");
                    }
                    reason.deleteCharAt(reason.length() - 1);

                    if (manager.setBan(new Ban(uuid, reason.toString(), Utils.now() + time))) {
                        sender.sendMessage(Utils.getComponentByString("&aPomyślnie zbanowano tego gracza."));
                    } else {
                        sender.sendMessage(Utils.getComponentByString("&cNapotkano błąd przy banowaniu gracza."));
                    }
                });
                return 0;
            }
            default -> {
                return -1;
            }
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("get", "set", "pardon");
        }
        if (args.length == 2) {
            return plugin.getOfflinePlayersManager().getNames();
        }
        if (!args[0].equalsIgnoreCase("set")) return new ArrayList<>();
        if (args.length == 3) {
            return getStringList("ban.popular-times", "30s", "30m", "1h", "1h30m", "1d");
        }
        if (args.length == 4) {
            return getStringList("ban.popular-reasons");
        }
        return new ArrayList<>();
    }

    private @NotNull List<String> getStringList(@NotNull String path, @NotNull String... def) {
        List<String> list = plugin.getConfig().getStringList(path);
        return list.isEmpty() ? Arrays.asList(def) : list;
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("ban", "zarządzaj banami graczy")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("get", "wyświetl informację o banie gracza")
                                .addOptions(new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true)),
                        new SubcommandData("pardon", "odbanuj gracza")
                                .addOptions(new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true)),
                        new SubcommandData("set", "zbanuj gracza")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true),
                                        new OptionData(OptionType.STRING, "czas", "czas bana", true, true),
                                        new OptionData(OptionType.STRING, "powod", "powód bana", true, true)
                                )
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        String playerName = event.getOption("gracz", OptionMapping::getAsString);
        if (playerName == null) {
            hook.sendMessage("Zły nick.").queue();
            return 1;
        }

        Utils.async(() -> {
            UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
            if (uuid == null) {
                hook.sendMessage("Zły nick.").queue();
                return;
            }

            BansManager manager = plugin.getBansManager();
            switch (String.valueOf(event.getSubcommandName())) {
                case "get" -> {
                    if (!manager.isBanned(uuid)) {
                        hook.sendMessage("Ten gracz nie jest zbanowany.").queue();
                        return;
                    }
                    Ban ban = manager.getBan(uuid);
                    if (ban == null) {
                        hook.sendMessage("Ten gracz jest zbanowany, ale nie udało się wczytać więcej informacji.").queue();
                        return;
                    }
                    String message = DiscordUtils.checkLength(
                            "**Ten gracz jest zbanowany.**\n" +
                            "Pozostały czas: `" + Utils.parseMillisToString(ban.getExpiration() - Utils.now(), true) + "`\n" +
                            "Powód: `" + ban.getReason(),
                            Message.MAX_CONTENT_LENGTH - 5
                    ) + "`";
                    hook.sendMessage(message).queue();
                }
                case "pardon" -> {
                    if (!manager.isBanned(uuid)) {
                        hook.sendMessage("Ten gracz nie jest zbanowany.").queue();
                        return;
                    }
                    if (manager.setBan(new Ban(uuid))) {
                        hook.sendMessage("Pomyślnie odbanowano gracza.").queue();
                    } else {
                        hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                    }
                }
                case "set" -> {
                    if (manager.isBanned(uuid)) {
                        hook.sendMessage("Ten gracz już jest zbanowany.").queue();
                        return;
                    }

                    long time;
                    try {
                        time = Utils.parseStringToMillis(event.getOption("czas", "", OptionMapping::getAsString));
                    } catch (IllegalArgumentException e) {
                        hook.sendMessage("Zły czas").queue();
                        return;
                    }

                    if (manager.setBan(new Ban(
                            uuid,
                            event.getOption("powod", OptionMapping::getAsString),
                            time
                    ))) {
                        hook.sendMessage("Pomyślnie zbanowano gracza").queue();
                    } else {
                        hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                    }
                }
            }
        });
        return 1;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        List<String> options = switch (event.getFocusedOption().getName()) {
            case "gracz" -> plugin.getOfflinePlayersManager().getNames();
            case "czas" -> getStringList("ban.popular-times", "30s", "30m", "1h", "1h30m", "1d");
            case "powod" -> getStringList("ban.popular-reasons");
            default -> new ArrayList<>();
        };
        if (options.isEmpty()) return;
        event.replyChoices(options.stream()
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
