package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

public class DifficultyCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public DifficultyCommand(@NotNull Main plugin) {
        command = plugin.getCommand("difficulty");
        if (command == null) {
            Logs.warning("The command /difficulty does not exist in the plugin.yml file and cannot be registered.");
        }
    }
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
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "difficulty")) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić poziomu trudności w tym świecie."));
            return 3;
        }
        Inventory inv = GUI.createInventory(9, "&6Ustaw poziom trudności");
        inv.setItem(2, GUI.createGuiItem(Material.LIME_WOOL, "&aŁatwy", "Kliknij, aby ustawić"));
        inv.setItem(4, GUI.createGuiItem(Material.YELLOW_WOOL, "&eNormalny", "Kliknij, aby ustawić"));
        inv.setItem(6, GUI.createGuiItem(Material.RED_WOOL, "&cTrudny", "Kliknij, aby ustawić"));
        player.openInventory(inv);
        return 60;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, "&6Ustaw poziom trudności")) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        event.getInventory().close();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "difficulty")) {
            player.sendMessage(Utils.getComponentByString("&cNie możesz zmienić poziomu trudności w tym świecie."));
            return;
        }

        switch (slot) {
            case 2 -> change(player, Difficulty.EASY, "łatwy");
            case 4 -> change(player, Difficulty.NORMAL, "normalny");
            case 6 -> change(player, Difficulty.HARD, "trudny");
        }
    }

    private void change(@NotNull Player player, @NotNull Difficulty difficulty, @NotNull String name) {
        World world = player.getWorld();
        if (world.getDifficulty() == difficulty) {
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już obowiązuje ten poziom trudności"));
            return;
        }
        world.setDifficulty(difficulty);

        Utils.broadcastMessage(
                player,
                Prefix.DIFFICULTY,
                "&aPomyślnie zmieniono poziom trudności na &2" + name + "&a.",
                "&aGracz &2" + player.getName() + "&a zmienił poziom trudności na &2" + name + "&a.",
                "**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** zmienił poziom trudności na **" + name + "**."
        );
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("difficulty", "Sprawdź poziom trudności w wybranym świecie")
                .addOptions(
                        new OptionData(OptionType.STRING, "swiat", "nazwa świata, w którym chcesz sprawdzić poziom trudności", true, true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        String worldName = event.getOption("swiat", OptionMapping::getAsString);
        if (worldName == null) {
            event.reply("Nie istnieje świat o takiej nazwie.").setEphemeral(ephemeral).queue();
            return 3;
        }

        World world = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (worldName.equals(Utils.getWorldName(candidate))) {
                world = candidate;
                break;
            }
        }
        if (world == null) {
            event.reply("Nie istnieje świat o takiej nazwie.").setEphemeral(ephemeral).queue();
            return 3;
        }

        String difficulty = switch (world.getDifficulty()) {
            case PEACEFUL -> "pokojowy";
            case EASY -> "łatwy";
            case NORMAL -> "normalny";
            case HARD -> "trudny";
        };

        event.reply("W świecie **" + world.getName() + "** obowiązuje **" + difficulty + "** poziom trudności.").setEphemeral(ephemeral).queue();
        return 5;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        if (!event.getFocusedOption().getName().equals("swiat")) return;
        event.replyChoices(Bukkit.getWorlds().stream()
                .map(Utils::getWorldName)
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
