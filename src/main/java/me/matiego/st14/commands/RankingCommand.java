package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.RankingsManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RankingCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public RankingCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("ranking");
        if (command == null) {
            Logs.warning("The command /ranking does not exist in the plugin.yml file and cannot be registered.");
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
        if (!(args.length == 1 || args.length == 2)) return -1;

        RankingsManager.Type type = RankingsManager.Type.getByName(args[0]);
        if (type == null) {
            sender.sendMessage(Utils.getComponentByString(Prefix.RANKING + "Ten ranking nie istnieje!"));
            return 3;
        }

        switch (args.length) {
            case 1 -> Utils.async(() -> {
                List<RankingsManager.Data> top = type.getTop(10);
                if (top.isEmpty()) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.RANKING + "&cRanking jest pusty!"));
                    return;
                }

                StringBuilder builder = new StringBuilder(Prefix.RANKING + "&6===== Ranking " + type.getRankingName() + " =====\n");
                for (int i = 0; i < top.size(); i++) {
                    RankingsManager.Data data = top.get(i);
                    builder
                            .append(Prefix.RANKING)
                            .append( "&6")
                            .append(i + 1)
                            .append(".&e ")
                            .append(plugin.getOfflinePlayersManager().getEffectiveNameById(data.getUuid()))
                            .append(" &6-&7 ")
                            .append(type.formatScore(data.getScore()))
                            .append("\n");
                }
                builder
                        .append(Prefix.RANKING)
                        .append("&6====================");

                sender.sendMessage(Utils.getComponentByString(builder.toString()));
            });
            case 2 -> Utils.async(() -> {
                UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.RANKING + "&cZły gracz!"));
                    return;
                }

                RankingsManager.Data data = type.get(uuid);
                if (data == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.RANKING + "&cTen gracz nie jest w rankingu, albo napotkano niespodziewany błąd."));
                    return;
                }

                sender.sendMessage(Utils.getComponentByString(Prefix.RANKING + "Gracz " + args[1] + " zajmuję " + data.getRank() + " miejsce w rankingu " + type.getRankingName() + ", z wynikiem " + type.formatScore(data.getScore()) + "."));
            });
        }
        return 5;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(RankingsManager.Type.values())
                    .map(Enum::toString)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getOfflinePlayersManager().getNames();
        }
        return new ArrayList<>();
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("ranking", "Wyświetl ranking graczy")
                .addOptions(
                        new OptionData(OptionType.STRING, "typ", "typ rankingu", true)
                                .addChoices(
                                        Arrays.stream(RankingsManager.Type.values())
                                                .map(Enum::toString)
                                                .map(String::toLowerCase)
                                                .map(v -> new Command.Choice(v, v))
                                                .collect(Collectors.toList())
                                ),
                        new OptionData(OptionType.STRING, "gracz", "gracz, którego pozycję chcesz wyświetlić", false, true),
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

        RankingsManager.Type type = RankingsManager.Type.getByName(event.getOption("typ", "null", OptionMapping::getAsString));
        if (type == null) {
            hook.sendMessage("Ten ranking nie istnieje.").queue();
            return 3;
        }

        Utils.async(() -> {
            String playerName = event.getOption("gracz", OptionMapping::getAsString);
            if (playerName != null) {
                UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
                if (uuid == null) {
                    hook.sendMessage("Zły nick.").queue();
                    return;
                }

                RankingsManager.Data data = type.get(uuid);
                if (data == null) {
                    hook.sendMessage("Ten gracz nie jest w rankingu, albo napotkano niespodziewany błąd.").queue();
                    return;
                }

                hook.sendMessage("Gracz **" + playerName + "** zajmuję **" + data.getRank() + "** miejsce w rankingu **" + type.getRankingName() + "**, z wynikiem `" + type.formatScore(data.getScore()) + "`.").queue();
                return;
            }

            EmbedBuilder eb = plugin.getRankingsManager().getEmbed(type, 10);
            if (eb == null) {
                hook.sendMessage("Ranking jest pusty!").queue();
                return;
            }

            eb.setFooter(DiscordUtils.getName(event.getUser(), event.getMember()), DiscordUtils.getAvatar(event.getUser(), event.getMember()));
            hook.sendMessageEmbeds(eb.build()).queue();
        });
        return 5;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        event.replyChoices(plugin.getOfflinePlayersManager().getNames().stream()
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
