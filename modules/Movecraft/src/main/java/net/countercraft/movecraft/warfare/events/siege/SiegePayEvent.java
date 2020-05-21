package net.countercraft.movecraft.warfare.events.siege;

import net.countercraft.movecraft.warfare.siege.Siege;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

public class SiegePayEvent extends Event implements Cancellable {
    private final static HandlerList HANDLERS = new HandlerList();
    private final Siege siege;
    private final Set<OfflinePlayer> recipients;
    private int payment;
    private boolean cancelled = false;

    public SiegePayEvent(Siege siege, Set<OfflinePlayer> recipients, int payment) {
        this.siege = siege;
        this.recipients = recipients;
        this.payment = payment;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Siege getSiege() {
        return siege;
    }

    public Set<OfflinePlayer> getRecipients() {
        return recipients;
    }

    public int getPayment() {
        return payment;
    }

    public void setPayment(int payment) {
        this.payment = payment;
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
