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
package net.draycia.carbon.common.event.events;

import net.draycia.carbon.api.event.CarbonEvent;
import net.draycia.carbon.api.users.CarbonPlayer;

public class CarbonEarlyChatEvent implements CarbonEvent {

    public final CarbonPlayer sender;
    public String message;

    public CarbonEarlyChatEvent(final CarbonPlayer sender, final String message) {
        this.sender = sender;
        this.message = message;
    }

    public CarbonPlayer sender() {
        return this.sender;
    }

    public String message() {
        return this.message;
    }

    public void message(final String message) {
        this.message = message;
    }

}
