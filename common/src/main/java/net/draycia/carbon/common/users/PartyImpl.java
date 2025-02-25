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
package net.draycia.carbon.common.users;

import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.draycia.carbon.api.CarbonServer;
import net.draycia.carbon.api.event.CarbonEventHandler;
import net.draycia.carbon.api.event.events.PartyJoinEvent;
import net.draycia.carbon.api.event.events.PartyLeaveEvent;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.draycia.carbon.api.users.Party;
import net.draycia.carbon.common.messages.CarbonMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class PartyImpl implements Party {

    private final Component name;
    private final UUID id;
    private final Set<UUID> members;
    private transient final @Nullable String serializedName;
    private transient volatile @MonotonicNonNull Map<UUID, ChangeType> changes;
    private transient @MonotonicNonNull @Inject UserManagerInternal<?> userManager;
    private transient @MonotonicNonNull @Inject CarbonServer server;
    private transient @MonotonicNonNull @Inject Logger logger;
    private transient @MonotonicNonNull @Inject CarbonEventHandler events;
    private transient @MonotonicNonNull @Inject CarbonMessages messages;
    private transient volatile boolean disbanded = false;

    private PartyImpl(
        final Component name,
        final UUID id
    ) {
        this.serializedName = GsonComponentSerializer.gson().serialize(name);
        if (this.serializedName.toCharArray().length > 8192) {
            throw new IllegalArgumentException("Serialized party name is too long: '%s', %s > 8192".formatted(name, this.serializedName.toCharArray().length));
        }
        this.name = name;
        this.id = id;
        this.members = ConcurrentHashMap.newKeySet();
        this.changes = new ConcurrentHashMap<>();
    }

    public static PartyImpl create(final Component name) {
        return create(name, UUID.randomUUID());
    }

    public static PartyImpl create(final Component name, final UUID id) {
        return new PartyImpl(name, id);
    }

    private Map<UUID, ChangeType> changes() {
        if (this.changes == null) {
            synchronized (this) {
                if (this.changes == null) {
                    this.changes = new ConcurrentHashMap<>();
                }
            }
        }
        return this.changes;
    }

    @Override
    public void addMember(final UUID id) {
        if (this.disbanded) {
            throw new IllegalStateException("This party was disbanded.");
        }
        this.changes().put(id, ChangeType.ADD);
        this.addMemberRaw(id);
        final BiConsumer<Void, @Nullable Throwable> exceptionHandler = ($, thr) -> {
            if (thr != null) {
                this.logger.warn("Exception adding member {} to group {}", id, this.id(), thr);
            }
        };
        this.userManager.saveParty(this).whenComplete(exceptionHandler);
        this.userManager.user(id).thenCompose(user -> {
            final WrappedCarbonPlayer wrapped = (WrappedCarbonPlayer) user;
            final @Nullable UUID oldPartyId = wrapped.partyId();
            wrapped.party(this);
            if (oldPartyId != null) {
                return this.userManager.party(oldPartyId).thenAccept(old -> {
                    if (old != null) {
                        old.removeMember(user.uuid());
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        }).whenComplete(exceptionHandler);
    }

    @Override
    public void removeMember(final UUID id) {
        if (this.disbanded) {
            throw new IllegalStateException("This party was disbanded.");
        }
        this.changes().put(id, ChangeType.REMOVE);
        this.removeMemberRaw(id);
        final BiConsumer<Void, @Nullable Throwable> exceptionHandler = ($, thr) -> {
            if (thr != null) {
                this.logger.warn("Exception removing member {} from group {}", id, this.id(), thr);
            }
        };
        this.userManager.saveParty(this).whenComplete(exceptionHandler);
        this.userManager.user(id).thenAccept(user -> {
            final WrappedCarbonPlayer wrapped = (WrappedCarbonPlayer) user;
            if (Objects.equals(wrapped.partyId(), this.id)) {
                wrapped.party(null);
            }
        }).whenComplete(exceptionHandler);
    }

    @Override
    public Set<UUID> members() {
        if (this.disbanded) {
            throw new IllegalStateException("This party was disbanded.");
        }
        return Set.copyOf(this.members);
    }

    @Override
    public void disband() {
        if (this.disbanded) {
            throw new IllegalStateException("This party is already disbanded.");
        }
        this.disbandRaw();
        this.userManager.disbandParty(this.id);
    }

    public void disbandRaw() {
        this.disbanded = true;
        this.server.players().stream().filter(p -> this.members.contains(p.uuid())).forEach(p -> ((WrappedCarbonPlayer) p).party(null));
        for (final UUID member : this.members) {
            this.emitLeaveEvent(member);
        }
    }

    public Set<UUID> rawMembers() {
        return this.members;
    }

    public void addMemberRaw(final UUID id) {
        this.members.add(id);

        this.events.emit(new PartyJoinEvent() {

            @Override
            public UUID playerId() {
                return id;
            }

            @Override
            public Party party() {
                return PartyImpl.this;
            }
        });

        this.notifyJoin(id);
    }

    public void removeMemberRaw(final UUID id) {
        this.members.remove(id);

        this.emitLeaveEvent(id);

        this.notifyLeave(id);
    }

    private void emitLeaveEvent(final UUID id) {
        this.events.emit(new PartyLeaveEvent() {

            @Override
            public UUID playerId() {
                return id;
            }

            @Override
            public Party party() {
                return PartyImpl.this;
            }
        });
    }

    public Map<UUID, ChangeType> pollChanges() {
        final Map<UUID, ChangeType> ret = Map.copyOf(this.changes());
        ret.forEach((id, t) -> this.changes().remove(id));
        return ret;
    }

    @Override
    public Component name() {
        return this.name;
    }

    public String serializedName() {
        return Objects.requireNonNullElseGet(this.serializedName, () -> GsonComponentSerializer.gson().serialize(this.name));
    }

    @Override
    public UUID id() {
        return this.id;
    }

    private void notifyJoin(final UUID joined) {
        this.notifyMembersChanged(joined, (p, party, member) -> {
            this.messages.playerJoinedParty(member, party.name(), p.displayName());
        });
    }

    private void notifyLeave(final UUID left) {
        this.notifyMembersChanged(left, (p, party, member) -> {
            this.messages.playerLeftParty(member, party.name(), p.displayName());
        });
    }

    private void notifyMembersChanged(final UUID changed, final ChangeNotifier notify) {
        final Supplier<CompletableFuture<? extends CarbonPlayer>> changedPlayer = Suppliers.memoize(() -> this.userManager.user(changed));
        for (final CarbonPlayer player : this.server.players()) {
            if (player.uuid().equals(changed)) {
                continue;
            }
            if (this.members.contains(player.uuid())) {
                changedPlayer.get().thenAccept(p -> {
                    notify.notify(p, this, player);
                }).whenComplete(($, thr) -> {
                    if (thr != null) {
                        this.logger.warn("Exception notifying members of party change", thr);
                    }
                });
            }
        }
    }

    @FunctionalInterface
    private interface ChangeNotifier {

        void notify(CarbonPlayer changed, Party party, CarbonPlayer member);

    }

    @Override
    public String toString() {
        return "PartyImpl[" +
            "name=" + this.name + ", " +
            "id=" + this.id + ", " +
            "members=" + this.members + ", " +
            "changes=" + this.changes + ']';
    }

    public enum ChangeType {
        ADD, REMOVE
    }

}
