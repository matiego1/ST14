package me.matiego.st14.utils;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface CommandHandler {
    interface Minecraft extends CommandHandler {
        @Nullable PluginCommand getMinecraftCommand();
        boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args);
        default @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
            return new ArrayList<>();
        }
        default void onInventoryClick(@NotNull InventoryClickEvent event) {}
    }

    interface Discord extends CommandHandler {
        @NotNull CommandData getDiscordCommand();

        default void onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {}
        default void onModalInteraction(@NotNull ModalInteraction event) {}
        default void onStringSelectInteraction(@NotNull StringSelectInteraction event) {}
        default void onMessageContextInteraction(@NotNull MessageContextInteraction event) {}
        default void onUserContextInteraction(@NotNull UserContextInteraction event) {}
        default void onButtonInteraction(@NotNull ButtonInteraction event) {}
    }
}
