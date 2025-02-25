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
package net.draycia.carbon.common.util;

import com.google.inject.Inject;
import java.util.function.IntFunction;
import net.draycia.carbon.common.messages.CarbonMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;

@DefaultQualifier(NonNull.class)
public final class PaginationHelper {

    private final CarbonMessages messages;

    @Inject
    private PaginationHelper(final CarbonMessages messages) {
        this.messages = messages;
    }

    public Pagination.BiIntFunction<ComponentLike> footerRenderer(final IntFunction<String> commandFunction) {
        return (currentPage, pages) -> {
            if (pages == 1) {
                return empty(); // we don't need to see 'Page 1/1'
            }
            final TextComponent.Builder buttons = text();
            if (currentPage > 1) {
                buttons.append(this.previousPageButton(currentPage, commandFunction));
            }
            if (currentPage > 1 && currentPage < pages) {
                buttons.append(space());
            }
            if (currentPage < pages) {
                buttons.append(this.nextPageButton(currentPage, commandFunction));
            }
            return this.messages.paginationFooter(currentPage, pages, buttons.build());
        };
    }

    private Component previousPageButton(final int currentPage, final IntFunction<String> commandFunction) {
        return text()
            .content("←")
            .clickEvent(runCommand(commandFunction.apply(currentPage - 1)))
            .hoverEvent(this.messages.paginationClickForPreviousPage())
            .build();
    }

    private Component nextPageButton(final int currentPage, final IntFunction<String> commandFunction) {
        return text()
            .content("→")
            .clickEvent(runCommand(commandFunction.apply(currentPage + 1)))
            .hoverEvent(this.messages.paginationClickForNextPage())
            .build();
    }

}
