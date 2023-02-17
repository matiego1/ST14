package me.matiego.st14;

import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.UUID;

public class ChatMinecraft extends ListenerAdapter {
    private final Main plugin;
    public ChatMinecraft(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final String DISALLOWED_CHARS = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]";
    private final FixedSizeMap<UUID, Pair<Long, Integer>> joinCooldown = new FixedSizeMap<>(100);

    public void block() {
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn == null) {
            Logs.error("An error occurred while blocking the chat-minecraft channel.");
            return;
        }
        try {
            chn.upsertPermissionOverride(chn.getGuild().getSelfMember()).grant(Permission.MESSAGE_SEND).complete();
            chn.upsertPermissionOverride(chn.getGuild().getPublicRole()).deny(Permission.MESSAGE_SEND).complete();
        } catch (Exception e) {
            Logs.error("An error occurred while blocking the chat-minecraft channel.", e);
        }
    }
    public void unblock() {
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn == null) {
            Logs.error("An error occurred while unblocking the chat-minecraft channel.");
            return;
        }
        try {
            try {
                chn.getManager().sync().queue();
            } catch (IllegalStateException e) {
                chn.upsertPermissionOverride(chn.getGuild().getPublicRole()).clear(Permission.MESSAGE_SEND).queue();
            }
        } catch (Exception e) {
            Logs.error("An error occurred while unblocking the chat-minecraft channel.", e);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        User user = event.getAuthor();
        if (user.isBot()) return;

        MessageChannelUnion union = event.getChannel();
        if (union.getType() != ChannelType.TEXT) return;
        TextChannel chn = union.asTextChannel();
        if (chn.getIdLong() != plugin.getConfig().getLong("discord.channel-ids.chat-minecraft")) return;

        Message message = event.getMessage();
        message.delete().queue();
        String msgContent = message.getContentDisplay().replaceAll(DISALLOWED_CHARS, "");
        if (msgContent.isBlank()) {
            DiscordUtils.sendPrivateMessage(user, "**Ups!** Po usunięciu niedozwolonych znaków z twojej wiadomości, nic nie zostało. Spróbuj ponownie, bez używania emoji.");
            return;
        }

        int players = 0;
        IncognitoManager incognitoManager = plugin.getIncognitoManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!incognitoManager.isIncognito(player.getUniqueId())) players++;
        }
        if (players == 0) {
            DiscordUtils.sendPrivateMessage(user, "**Ups!** Na serwerze aktualnie nikt nie gra.");
            return;
        }
        Utils.async(() -> {
            if (DiscordUtils.sendWebhook(
                    plugin.getConfig().getString("discord.webhook-urls.chat-minecraft", ""),
                    DiscordUtils.getAvatar(user, event.getMember()),
                    "[DC] " + DiscordUtils.getName(user, event.getMember()),
                    DiscordUtils.escapeFormatting(msgContent))) {
                for (String line : msgContent.split("\n")) {
                    Bukkit.broadcast(Utils.getComponentByString("&a[DC] &7" + DiscordUtils.getName(user, event.getMember()) + "&f: " + line));
                }
            } else {
                DiscordUtils.sendPrivateMessage(user, "**Ups!** Napotkano błąd przy wysyłaniu twojej wiadomości. Spróbuj później.");
            }
        });
    }

    public void sendJoinMessage(@NotNull Player player) {
        if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
        sendFakeJoinMessage(player);
    }

    public void sendConsoleJoinMessage(@NotNull Player player) {
        Logs.discord("Gracz **" + player.getName() + "** dołączył do gry.");
    }

    public void sendFakeJoinMessage(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor("Gracz " + player.getName() + " dołączył do gry", null, Utils.getSkinUrl(uuid));
        eb.setColor(Color.YELLOW);
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn != null) {
            chn.sendMessageEmbeds(eb.build()).queue();
        }

        long now = Utils.now();
        Pair<Long, Integer> pair = joinCooldown.getOrDefault(uuid, new Pair<>(now, 0));
        if (now - pair.getFirst() >= 10_000) {
            joinCooldown.put(uuid, new Pair<>(now, 1));
        } else if (pair.getSecond() < 2) {
            joinCooldown.put(uuid, new Pair<>(now, pair.getSecond() + 1));
        } else {
            //TODO: ban for 15 seconds
            player.kick(Utils.getComponentByString("&cNie możesz dołączać do serwera tak często."));
        }
    }

    public void sendQuitMessage(@NotNull Player player) {
        if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
        sendFakeQuitMessage(player);
    }

    public void sendFakeQuitMessage(@NotNull Player player) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor("Gracz " + player.getName() + " opuścił grę", null, Utils.getSkinUrl(player.getUniqueId()));
        eb.setColor(Color.YELLOW);
        PlayerTime playerTime = plugin.getTimeManager().getTime(player.getUniqueId());
        if (playerTime != null) {
            GameTime time = playerTime.getFakeSession();
            eb.setFooter("Czas gry: " + Utils.parseMillisToString(time.getNormal() + time.getAfk(), false));
        }
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn != null) {
            chn.sendMessageEmbeds(eb.build()).queue();
        }
    }

    public void sendConsoleQuitMessage(@NotNull Player player) {
        PlayerTime playerTime = plugin.getTimeManager().getTime(player.getUniqueId());
        String time = "";
        if (playerTime != null) {
            GameTime gameTime = playerTime.getSession();
            time = "Czas gry: `" +
                    Utils.parseMillisToString(gameTime.getNormal() + gameTime.getAfk() + gameTime.getIncognito(), true) +
                    "` [`" +
                    Utils.parseMillisToString(gameTime.getNormal(), true) +
                    "` | `" +
                    Utils.parseMillisToString(gameTime.getAfk(), true) +
                    "` | `" +
                    Utils.parseMillisToString(gameTime.getIncognito(), true) +
                    "`]";
        }
        Logs.discord("Gracz **" + player.getName() + "** opuścił grę. " + time);
    }

    public void sendDeathMessage(@NotNull String message, @NotNull Player... players) {
        Logs.discord(message);

        for (Player player : players) {
            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(message);
        eb.setColor(Color.RED);
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn != null) {
            chn.sendMessageEmbeds(eb.build()).queue();
        }
    }

    public void sendChatMessage(@NotNull String message, @NotNull Player player) {
        Logs.discord(message);

        if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
        Utils.async(() -> DiscordUtils.sendWebhook(
                plugin.getConfig().getString("discord.webhook-urls.chat-minecraft", ""),
                Utils.getSkinUrl(player.getUniqueId()),
                "[" + Utils.getWorldPrefix(player.getWorld()) + "] " + player.getName(),
                DiscordUtils.escapeFormatting(DiscordUtils.checkLength(message, Message.MAX_CONTENT_LENGTH))
        ));
    }

    public void sendMessage(@NotNull String message, @NotNull String name) {
        Logs.discord(message);

        Utils.async(() -> DiscordUtils.sendWebhook(
                plugin.getConfig().getString("discord.webhook-urls.chat-minecraft", ""),
                DiscordUtils.getBotIcon(),
                name,
                DiscordUtils.checkLength(message, Message.MAX_CONTENT_LENGTH)
        ));
    }

    public void sendMessageEmbed(@NotNull MessageEmbed embed) {
        TextChannel chn = DiscordUtils.getChatMinecraftChannel();
        if (chn != null) {
            chn.sendMessageEmbeds(embed).queue();
        }
    }
}
