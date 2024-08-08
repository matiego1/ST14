package me.matiego.st14;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

//Based on https://github.com/e-im/FreedomChat/ (abdb14a)
@ChannelHandler.Sharable
public class ChatPacketHandler extends MessageToByteEncoder<Packet<?>> {
    private static final int STATUS_RESPONSE_PACKET_ID = 0x00;
    private final StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> s2cPlayPacketCodec;

    public ChatPacketHandler() {
        final RegistryAccess registryAccess = MinecraftServer.getServer().registryAccess();
        final Function<ByteBuf, RegistryFriendlyByteBuf> bufRegistryAccess = RegistryFriendlyByteBuf.decorator(registryAccess);
        this.s2cPlayPacketCodec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(bufRegistryAccess).codec();
    }

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof ClientboundPlayerChatPacket
                || msg instanceof ClientboundStatusResponsePacket
                || msg instanceof ClientboundLoginPacket;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final Packet msg, final ByteBuf out) {
        final FriendlyByteBuf fbb = new FriendlyByteBuf(out);

        if (msg instanceof final ClientboundPlayerChatPacket packet) {
            encode(ctx, packet, fbb);
        } else if (msg instanceof final ClientboundStatusResponsePacket packet) {
            encode(ctx, packet, fbb);
        } else if (msg instanceof final ClientboundLoginPacket packet) {
            encode(ctx, packet, fbb);
        }
    }

    private void encode(@SuppressWarnings("unused") final ChannelHandlerContext ctx, final ClientboundPlayerChatPacket msg, final FriendlyByteBuf buf) {
        final Component content = Objects.requireNonNullElseGet(msg.unsignedContent(), () -> Component.literal(msg.body().content()));

        final ChatType.Bound chatType = msg.chatType();
        final Component decoratedContent = chatType.decorate(content);

        final ClientboundSystemChatPacket system = new ClientboundSystemChatPacket(decoratedContent, false);

        s2cPlayPacketCodec.encode(buf, system);
    }

    private void encode(@SuppressWarnings("unused") final ChannelHandlerContext ctx, final ClientboundLoginPacket msg, final FriendlyByteBuf buf) {
        final ClientboundLoginPacket rewritten = new ClientboundLoginPacket(
                msg.playerId(),
                msg.hardcore(),
                msg.levels(),
                msg.maxPlayers(),
                msg.chunkRadius(),
                msg.simulationDistance(),
                msg.reducedDebugInfo(),
                msg.showDeathScreen(),
                msg.doLimitedCrafting(),
                msg.commonPlayerSpawnInfo(),
                true // Enforced secure chat
        );
        s2cPlayPacketCodec.encode(buf, rewritten);
    }

    private void encode(@SuppressWarnings("unused") final ChannelHandlerContext ctx, final ClientboundStatusResponsePacket msg, final FriendlyByteBuf buf) {
        final ServerStatus status = msg.status();

        final CustomServerMetadata customStatus = new CustomServerMetadata(
                status.description(),
                status.players(),
                status.version(),
                status.favicon(),
                status.enforcesSecureChat(),
                true
        );

        buf.writeVarInt(STATUS_RESPONSE_PACKET_ID);
        buf.writeJsonWithCodec(CustomServerMetadata.CODEC, customStatus);
    }

    private record CustomServerMetadata(Component description, Optional<ServerStatus.Players> players, Optional<ServerStatus.Version> version, Optional<ServerStatus.Favicon> favicon, boolean enforcesSecureChat, boolean preventsChatReports) {
        public static final Codec<CustomServerMetadata> CODEC = RecordCodecBuilder
                .create((instance) -> instance.group(
                                ComponentSerialization.CODEC.lenientOptionalFieldOf("description", CommonComponents.EMPTY)
                                        .forGetter(CustomServerMetadata::description),
                                ServerStatus.Players.CODEC.lenientOptionalFieldOf("players")
                                        .forGetter(CustomServerMetadata::players),
                                ServerStatus.Version.CODEC.lenientOptionalFieldOf("version")
                                        .forGetter(CustomServerMetadata::version),
                                ServerStatus.Favicon.CODEC.lenientOptionalFieldOf("favicon")
                                        .forGetter(CustomServerMetadata::favicon),
                                Codec.BOOL.lenientOptionalFieldOf("enforcesSecureChat", false)
                                        .forGetter(CustomServerMetadata::enforcesSecureChat),
                                Codec.BOOL.lenientOptionalFieldOf("preventsChatReports", false)
                                        .forGetter(CustomServerMetadata::preventsChatReports))
                        .apply(instance, CustomServerMetadata::new));

        public Component description() {
            return this.description;
        }
        public Optional<ServerStatus.Players> players() {
            return this.players;
        }
        public Optional<ServerStatus.Version> version() {
            return this.version;
        }
        public Optional<ServerStatus.Favicon> favicon() {
            return this.favicon;
        }
        public boolean enforcesSecureChat() {
            return this.enforcesSecureChat;
        }
        public boolean preventsChatReports() {
            return this.preventsChatReports;
        }
    }
}
