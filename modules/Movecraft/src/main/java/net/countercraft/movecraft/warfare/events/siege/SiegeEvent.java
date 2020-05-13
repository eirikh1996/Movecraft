package net.countercraft.movecraft.warfare.events.siege;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftEvent;
import net.countercraft.movecraft.warfare.siege.Siege;
import org.jetbrains.annotations.NotNull;

/**
 * Base event for siege events
 */
public abstract class SiegeEvent extends CraftEvent {
    private final Siege siege;

    public SiegeEvent(@NotNull Craft craft, Siege siege) {
        super(craft);
        this.siege = siege;
    }

    public Siege getSiege() {
        return siege;
    }
}
