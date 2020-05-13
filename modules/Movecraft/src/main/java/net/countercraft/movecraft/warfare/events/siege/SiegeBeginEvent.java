package net.countercraft.movecraft.warfare.events.siege;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.warfare.siege.Siege;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player starts a siege. This also include the preparation part
 */
public class SiegeBeginEvent extends SiegeEvent implements Cancellable {
    private boolean cancelled = false;
    private static final HandlerList HANDLERS = new HandlerList();
    public SiegeBeginEvent(@NotNull Craft craft, Siege siege) {
        super(craft, siege);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
