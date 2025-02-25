/*
 * CarbonChat
 *
 * Copyright (c) 2023 Josua Parks (Vicarious)
 *                    Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.draycia.carbon.common.messaging;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonServer;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.draycia.carbon.common.CarbonChatInternal;
import net.draycia.carbon.common.command.commands.WhisperCommand;
import net.draycia.carbon.common.config.ConfigManager;
import net.draycia.carbon.common.config.MessagingSettings;
import net.draycia.carbon.common.messaging.packets.ChatMessagePacket;
import net.draycia.carbon.common.messaging.packets.DisbandPartyPacket;
import net.draycia.carbon.common.messaging.packets.InvalidatePartyInvitePacket;
import net.draycia.carbon.common.messaging.packets.LocalPlayerChangePacket;
import net.draycia.carbon.common.messaging.packets.LocalPlayersPacket;
import net.draycia.carbon.common.messaging.packets.PacketFactory;
import net.draycia.carbon.common.messaging.packets.PartyChangePacket;
import net.draycia.carbon.common.messaging.packets.PartyInvitePacket;
import net.draycia.carbon.common.messaging.packets.SaveCompletedPacket;
import net.draycia.carbon.common.messaging.packets.WhisperPacket;
import net.draycia.carbon.common.users.NetworkUsers;
import net.draycia.carbon.common.users.PartyInvites;
import net.draycia.carbon.common.users.UserManagerInternal;
import net.draycia.carbon.common.util.ConcurrentUtil;
import net.draycia.carbon.common.util.ExceptionLoggingScheduledThreadPoolExecutor;
import net.draycia.carbon.common.util.Exceptions;
import ninja.egg82.messenger.MessagingService;
import ninja.egg82.messenger.NATSMessagingService;
import ninja.egg82.messenger.PacketManager;
import ninja.egg82.messenger.RabbitMQMessagingService;
import ninja.egg82.messenger.RedisMessagingService;
import ninja.egg82.messenger.handler.AbstractServerMessagingHandler;
import ninja.egg82.messenger.handler.MessagingHandler;
import ninja.egg82.messenger.handler.MessagingHandlerImpl;
import ninja.egg82.messenger.packets.AbstractPacket;
import ninja.egg82.messenger.packets.MultiPacket;
import ninja.egg82.messenger.packets.server.InitializationPacket;
import ninja.egg82.messenger.packets.server.KeepAlivePacket;
import ninja.egg82.messenger.packets.server.PacketVersionPacket;
import ninja.egg82.messenger.packets.server.PacketVersionRequestPacket;
import ninja.egg82.messenger.packets.server.ShutdownPacket;
import ninja.egg82.messenger.services.PacketService;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@Singleton
@DefaultQualifier(NonNull.class)
public class MessagingManager {

    private static final byte protocolVersion = 0;

    private final Logger logger;
    private final UUID serverId;
    private final @MonotonicNonNull ScheduledExecutorService scheduledExecutor;
    private final @MonotonicNonNull MessagingService messagingService;
    private volatile @MonotonicNonNull PacketService packetService;

    @Inject
    public MessagingManager(
        final ConfigManager configManager,
        final CarbonChat carbonChat,
        final @ServerId UUID serverId,
        final CarbonServer server,
        final Logger logger,
        final UserManagerInternal<?> userManager,
        final NetworkUsers networkUsers,
        final WhisperCommand.WhisperHandler whisper,
        final PacketFactory packetFactory,
        final PartyInvites partyInvites
    ) {
        this.serverId = serverId;
        this.logger = logger;
        final boolean proxy = ((CarbonChatInternal) carbonChat).isProxy();
        if (proxy || !configManager.primaryConfig().messagingSettings().enabled()) {
            if (!proxy) {
                logger.info("Messaging services disabled in config. Cross-server will not work without this!");
            } else if (configManager.primaryConfig().messagingSettings().enabled()) {
                logger.warn("Messaging services enabled in config, but messaging is not supported on proxies. The messaging service is used for the configuration where Carbon is installed on all backends instead of the proxy.");
            }
            this.messagingService = null;
            this.packetService = null;
            this.scheduledExecutor = null;
            return;
        }

        PacketManager.register(MultiPacket.class, MultiPacket::new);
        PacketManager.register(KeepAlivePacket.class, KeepAlivePacket::new);
        PacketManager.register(InitializationPacket.class, InitializationPacket::new);
        PacketManager.register(PacketVersionPacket.class, PacketVersionPacket::new);
        PacketManager.register(PacketVersionRequestPacket.class, PacketVersionRequestPacket::new);
        PacketManager.register(ShutdownPacket.class, ShutdownPacket::new);
        //PacketManager.register(HeartbeatPacket.class, HeartbeatPacket::new);
        PacketManager.register(ChatMessagePacket.class, ChatMessagePacket::new);
        PacketManager.register(SaveCompletedPacket.class, SaveCompletedPacket::new);
        PacketManager.register(LocalPlayersPacket.class, LocalPlayersPacket::new);
        PacketManager.register(LocalPlayerChangePacket.class, LocalPlayerChangePacket::new);
        PacketManager.register(WhisperPacket.class, WhisperPacket::new);
        PacketManager.register(PartyChangePacket.class, PartyChangePacket::new);
        PacketManager.register(PartyInvitePacket.class, PartyInvitePacket::new);
        PacketManager.register(InvalidatePartyInvitePacket.class, InvalidatePartyInvitePacket::new);
        PacketManager.register(DisbandPartyPacket.class, DisbandPartyPacket::new);

        this.packetService = new PacketService(4, false, protocolVersion);
        this.scheduledExecutor = new ExceptionLoggingScheduledThreadPoolExecutor(4,
            ConcurrentUtil.carbonThreadFactory(logger, "MessagingManager"), logger);

        final MessagingHandlerImpl handlerImpl = new MessagingHandlerImpl(this.packetService);
        handlerImpl.addHandler(new CarbonServerHandler(server, serverId, this.packetService, handlerImpl, packetFactory));
        handlerImpl.addHandler(new CarbonChatPacketHandler(carbonChat, this, userManager, networkUsers, whisper, partyInvites));

        try {
            this.messagingService = this.initMessagingService(
                this.packetService,
                handlerImpl,
                new File("/"),
                configManager.primaryConfig().messagingSettings()
            );
        } catch (final IOException | TimeoutException | InterruptedException e) {
            throw Exceptions.rethrow(e);
        }

        this.packetService.addMessenger(this.messagingService);

        this.packetService.queuePacket(new InitializationPacket(serverId, protocolVersion));
        this.packetService.flushQueue();

        // Broadcast keepalive packets
        this.scheduledExecutor.scheduleAtFixedRate(() -> {
            this.packetService.queuePacket(new KeepAlivePacket(serverId));
            this.packetService.flushQueue();
        }, 5, 5, TimeUnit.SECONDS);

        this.scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                this.packetService.flushQueue();
            } catch (final IndexOutOfBoundsException ignored) {

            }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    public PacketService requirePacketService() {
        return Objects.requireNonNull(this.packetService, "packetService");
    }

    private void withPacketService(final Consumer<PacketService> consumer) {
        if (this.packetService != null) {
            consumer.accept(this.packetService);
        }
    }

    public void queuePacketAndFlush(final Supplier<? extends AbstractPacket> makePacket) {
        this.withPacketService(service -> {
            service.queuePacket(makePacket.get());
            service.flushQueue();
        });
    }

    public void queuePacket(final Supplier<? extends AbstractPacket> makePacket) {
        this.withPacketService(service -> service.queuePacket(makePacket.get()));
    }

    public void onShutdown() {
        if (this.scheduledExecutor != null) {
            ConcurrentUtil.shutdownExecutor(this.scheduledExecutor, TimeUnit.MILLISECONDS, 500);
        }
        if (this.packetService != null) {
            this.packetService.flushQueue();
            this.packetService.shutdown();
            this.packetService = null;
        }
        if (this.messagingService != null) {
            this.messagingService.close();
        }
    }

    private MessagingService initMessagingService(
        final PacketService packetService,
        final MessagingHandlerImpl handlerImpl,
        final File packetDir,
        final MessagingSettings messagingSettings
    ) throws IOException, TimeoutException, InterruptedException {
        final String name = "engine1";
        final String channelName = "carbon-data";

        return switch (messagingSettings.brokerType()) {
            case RABBITMQ -> {
                this.logger.info("Initializing RabbitMQ Messaging services...");

                final RabbitMQMessagingService.Builder builder = RabbitMQMessagingService.builder(packetService, name, channelName, this.serverId, handlerImpl, 0L, false, packetDir)
                    .url(messagingSettings.url(), messagingSettings.port(), messagingSettings.vhost())
                    .timeout(5000);

                if (messagingSettings.username() != null && !messagingSettings.username().isBlank()) {
                    builder.credentials(messagingSettings.username(), messagingSettings.password());
                }

                yield builder.build();
            }
            case NATS -> {
                this.logger.info("Initializing NATS Messaging services...");

                final NATSMessagingService.Builder builder = NATSMessagingService.builder(packetService, name, channelName, this.serverId, handlerImpl, 0L, false, packetDir)
                    .url(messagingSettings.url(), messagingSettings.port())
                    .life(5000);

                if (messagingSettings.credentialsFile() != null && !messagingSettings.credentialsFile().isBlank()) {
                    builder.credentials(messagingSettings.credentialsFile());
                }

                yield builder.build();
            }
            case REDIS -> {
                this.logger.info("Initializing Redis Messaging services...");

                final RedisMessagingService.Builder builder = RedisMessagingService.builder(packetService, name, channelName, this.serverId, handlerImpl, 0L, false, packetDir)
                    .url(messagingSettings.url(), messagingSettings.port());

                if (messagingSettings.password() != null && !messagingSettings.password().isBlank()) {
                    builder.credentials(messagingSettings.password());
                }

                yield builder.build();
            }
            case NONE ->
                throw new IllegalStateException("MessagingManager initialized with no messaging broker selected!");
        };
    }

    public enum BrokerType {
        NONE,
        RABBITMQ,
        NATS,
        REDIS,
    }

    private static final class CarbonServerHandler extends AbstractServerMessagingHandler {

        private final CarbonServer server;
        private final PacketFactory packetFactory;

        private CarbonServerHandler(
            final @NonNull CarbonServer server,
            final @NonNull UUID serverId,
            final @NonNull PacketService packetService,
            final @NonNull MessagingHandler messagingHandler,
            final @NonNull PacketFactory packetFactory
        ) {
            super(serverId, packetService, messagingHandler);
            this.server = server;
            this.packetFactory = packetFactory;
        }

        @Override
        protected void handleInitialization(final @NonNull InitializationPacket packet) {
            super.handleInitialization(packet);
            final List<? extends CarbonPlayer> players = this.server.players();
            final Map<UUID, String> map = new HashMap<>();
            for (final CarbonPlayer player : players) {
                map.put(player.uuid(), player.username());
            }
            this.packetService.queuePacket(this.packetFactory.localPlayersPacket(map));
        }

    }

}
