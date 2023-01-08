package me.matiego.st14.utils;

import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public interface CommandHandler {
    interface Minecraft extends CommandHandler {
        @Nullable PluginCommand getMinecraftCommand();
        int onCommand(@NotNull CommandSender sender, @NotNull String[] args);
        default @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
            return new ArrayList<>();
        }
        default void onInventoryClick(@NotNull InventoryClickEvent event) {}
    }

    interface Discord extends CommandHandler {
        @NotNull CommandData getDiscordCommand();

        default int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
            return -1;
        }
        default int onModalInteraction(@NotNull ModalInteraction event) {
            return -1;
        }
        default int onStringSelectInteraction(@NotNull StringSelectInteraction event) {
            return -1;
        }
        default int onButtonInteraction(@NotNull ButtonInteraction event) {
            return -1;
        }
        default void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteraction event) {}
    }
}
