package net.countercraft.movecraft.warfare.events.assault;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftEvent;
import net.countercraft.movecraft.warfare.assault.Assault;
import org.jetbrains.annotations.NotNull;

public abstract class AssaultEvent extends CraftEvent {
    private final Assault assault;
    public AssaultEvent(@NotNull Craft craft, Assault assault) {
        super(craft);
        this.assault = assault;
    }

    public Assault getAssault() {
        return assault;
    }
}
