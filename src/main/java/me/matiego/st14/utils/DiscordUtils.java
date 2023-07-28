package me.matiego.st14.utils;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.FixedSizeMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.utils.IOUtil;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.minidns.DnsClient;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.record.Record;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordUtils {
    //Based on: https://github.com/DiscordSRV/DiscordSRV/blob/master/src/main/java/github/scarsz/discordsrv/DiscordSRV.java
    public static @NotNull OkHttpClient getHttpClient() {
        Dns dns = Dns.SYSTEM;
        try {
            List<InetAddress> fallbackDnsServers = new CopyOnWriteArrayList<>(Arrays.asList(
                    // CloudFlare resolvers
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    // Google resolvers
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
            ));
            dns = new Dns() {
                // maybe drop minidns in favor of something else
                // https://github.com/dnsjava/dnsjava/blob/master/src/main/java/org/xbill/DNS/SimpleResolver.java
                // https://satreth.blogspot.com/2015/01/java-dns-query.html

                private final DnsClient client = new DnsClient();
                private int failedRequests = 0;
                @NotNull
                @Override
                public List<InetAddress> lookup(@NotNull String host) throws UnknownHostException {
                    int max = 5;
                    if (failedRequests < max) {
                        try {
                            List<InetAddress> result = Dns.SYSTEM.lookup(host);
                            failedRequests = 0; // reset on successful lookup
                            return result;
                        } catch (Exception e) {
                            failedRequests++;
                            Logs.error("System DNS FAILED to resolve hostname " + host + ", " + (failedRequests >= max ? "using fallback DNS for this request" : "switching to fallback DNS servers") + "!");
                        }
                    }
                    return lookupPublic(host);
                }
                private @NotNull List<InetAddress> lookupPublic(String host) throws UnknownHostException {
                    for (InetAddress dnsServer : fallbackDnsServers) {
                        try {
                            DnsMessage query = client.query(host, Record.TYPE.A, Record.CLASS.IN, dnsServer).response;
                            if (query.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
                                Logs.error("DNS server " + dnsServer.getHostAddress() + " failed our DNS query for " + host + ": " + query.responseCode.name());
                            }

                            List<InetAddress> resolved = query.answerSection.stream()
                                    .map(record -> record.payloadData.toString())
                                    .map(s -> {
                                        try {
                                            return InetAddress.getByName(s);
                                        } catch (UnknownHostException e) {
                                            // impossible
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList());
                            if (!resolved.isEmpty()) {
                                return resolved;
                            } else {
                                Logs.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host + ": no results");
                            }
                        } catch (Exception e) {
                            Logs.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host, e);
                        }

                        // this dns server gave us an error so we move this dns server to the end of the
                        // list, effectively making it the last resort for future requests
                        fallbackDnsServers.remove(dnsServer);
                        fallbackDnsServers.add(dnsServer);
                    }

                    // this sleep is here to prevent OkHTTP from repeatedly trying to query DNS servers with no
                    // delay of it's own when internet connectivity is lost. that's extremely bad because it'll be
                    // spitting errors into the console and consuming 100% cpu
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}

                    UnknownHostException exception = new UnknownHostException("All DNS resolvers failed to resolve hostname " + host + ". Not good.");
                    exception.setStackTrace(new StackTraceElement[]{exception.getStackTrace()[0]});
                    throw exception;
                }
            };
        } catch (Exception e) {
            Logs.error("An error was encountered!", e);
        }
        return IOUtil.newHttpClientBuilder()
                .dns(dns)
                // more lenient timeouts (normally 10 seconds for these 3)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .hostnameVerifier(OkHostnameVerifier.INSTANCE)
                .build();
    }

    public static @NotNull ImmutableSet<GatewayIntent> getIntents() {
        return Sets.immutableEnumSet(EnumSet.of(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_WEBHOOKS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES
        ));
    }

    public static @NotNull List<CacheFlag> getDisabledCacheFlag() {
        return Arrays.asList(
                CacheFlag.ACTIVITY,
                CacheFlag.VOICE_STATE,
                CacheFlag.EMOJI,
                CacheFlag.STICKER,
                CacheFlag.CLIENT_STATUS,
                CacheFlag.ONLINE_STATUS,
                CacheFlag.SCHEDULED_EVENTS
        );
    }

    public static @NotNull EnumSet<Permission> getRequiredPermissions() {
        return EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MANAGE_CHANNEL,
                Permission.MANAGE_ROLES,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_SEND_IN_THREADS,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_HISTORY,
                Permission.NICKNAME_MANAGE
        );
    }

    public static boolean hasRequiredPermissions(@NotNull MessageChannelUnion union) {
        if (!union.getType().isGuild()) return union.canTalk();
        GuildChannel chn = union.asGuildMessageChannel();
        return chn.getGuild().getSelfMember().hasPermission(chn, getRequiredPermissions());
    }

    public static @Nullable TextChannel getConsoleChannel() {
        JDA jda = Main.getInstance().getJda();
        if (jda == null) return null;
        return jda.getTextChannelById(Main.getInstance().getConfig().getLong("discord.channel-ids.console"));
    }

    public static @Nullable TextChannel getChatMinecraftChannel() {
        JDA jda = Main.getInstance().getJda();
        if (jda == null) return null;
        return jda.getTextChannelById(Main.getInstance().getConfig().getLong("discord.channel-ids.chat-minecraft"));
    }

    /**
     * Makes the string not longer than maxLength.
     * @param string the string
     * @param maxLength a maximum length of string
     * @return an edited string
     */
    public static @NotNull String checkLength(@NotNull String string, @Range(from = 3, to = Integer.MAX_VALUE) int maxLength) {
        if (string.isBlank()) return "...";
        return string.length() > maxLength - 3 ? string.substring(0, maxLength - 3) + "..." : string;
    }

    public static @Nullable String getBotIcon() {
        JDA jda = Main.getInstance().getJda();
        if (jda == null) return null;
        return jda.getSelfUser().getEffectiveAvatarUrl();
    }

    public static InputStream convertAttachmentToInputStream(@NotNull Message.Attachment attachment) throws ExecutionException, InterruptedException {
        return attachment.getProxy().download().get();
    }

    private static final FixedSizeMap<Long, Long> privateMessages = new FixedSizeMap<>(100);
    public static void sendPrivateMessage(@NotNull User user, @NotNull String message) {
        user.openPrivateChannel().queue(
                privateChannel -> privateChannel.sendMessage(checkLength(message, Message.MAX_CONTENT_LENGTH)).queue(
                        success -> {},
                        failure -> {
                            if (failure instanceof ErrorResponseException e && e.getErrorCode() == 50007) {
                                long now = Utils.now();
                                if (now - privateMessages.getOrDefault(user.getIdLong(), 0L) >= 15 * 60 * 1000L) {
                                    Logs.warning("User " + DiscordUtils.getAsTag(user) + " doesn't allow private messages.");
                                    privateMessages.put(user.getIdLong(), now);
                                }
                            } else {
                                Logs.error("An error occurred while sending a private message.", failure);
                            }
                        })
        );
    }

    public static boolean sendWebhook(@NotNull String url, @Nullable String iconUrl, @Nullable String name, @NotNull String message) {
        try (WebhookClient client = WebhookClient.withUrl(url)) {
            WebhookMessageBuilder builder = new WebhookMessageBuilder();
            if (name != null) builder.setUsername(name);
            if (iconUrl != null) builder.setAvatarUrl(iconUrl);
            builder.setContent(checkLength(message, Message.MAX_CONTENT_LENGTH));
            client.send(builder.build());
            return true;
        } catch (Exception e) {
            Logs.error("An error occurred while sending the webhook", e);
        }
        return false;
    }

    public static @NotNull String getAvatar(@NotNull User user, @Nullable Member member) {
        return (member != null) ? member.getEffectiveAvatarUrl() : user.getEffectiveAvatarUrl();
    }

    public static @NotNull String getName(@NotNull User user, @Nullable Member member) {
        return (member != null) ? member.getEffectiveName() : user.getName();
    }

    public static @NotNull String escapeFormatting(@NotNull String string) {
        final String[] chars = new String[]{"*", ":", "|", "~", "_", ">", "@", "#", "`"};
        for (String c : chars) {
            string = string.replace(c, "\\" + c);
        }
        return string;
    }

    public static @Nullable Member retrieveMember(@NotNull Guild guild, @NotNull UserSnowflake user) {
        Member member = null;
        try {
            member = guild.retrieveMemberById(user.getIdLong()).complete();
        } catch (Exception ignored) {}
        return member;
    }

    public static boolean hasRole(@NotNull Member member, long role) {
        return member.getRoles().stream().map(ISnowflake::getIdLong).anyMatch(id -> id == role);
    }

    @SuppressWarnings("deprecation")
    public static @NotNull String getAsTag(@NotNull User user) {
        return user.getDiscriminator().equals("0000") ? user.getName() : user.getAsTag();
    }

    public static @NotNull String getAsTag(@NotNull Member member) {
        return getAsTag(member.getUser());
    }
}
