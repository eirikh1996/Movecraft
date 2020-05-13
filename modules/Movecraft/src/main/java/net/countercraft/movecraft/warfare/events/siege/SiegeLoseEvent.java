package net.countercraft.movecraft.warfare.events.siege;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.warfare.siege.Siege;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a siege is lost
 */
public class SiegeLoseEvent extends SiegeEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public SiegeLoseEvent(@NotNull Craft craft, Siege siege) {
        super(craft, siege);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
