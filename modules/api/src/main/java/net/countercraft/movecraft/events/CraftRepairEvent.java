package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a craft is repaired
 */
public class CraftRepairEvent extends CraftEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RepairStage stage;
    private boolean cancelled;
    public CraftRepairEvent(@NotNull Craft craft, RepairStage stage) {
        super(craft);
        this.stage = stage;
    }

    /**
     * The stages for each repair
     */
    public enum RepairStage{
        SAVE_STATE, GET_SUPPLY_INFO, DO_THE_REPAIR
    }
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }



    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList(){
        return HANDLERS;
    }
}
