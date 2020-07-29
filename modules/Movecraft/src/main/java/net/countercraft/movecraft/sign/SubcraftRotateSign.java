package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.Rotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.craft.ICraft;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class SubcraftRotateSign implements Listener {
    private static final String HEADER = "Subcraft Rotate";
    private final Set<MovecraftLocation> rotatingCrafts = new HashSet<>();
    private final Map<UUID, Map<MovecraftLocation, CraftHolder>> subcraftMap = new HashMap<>();
    private final Map<UUID, Long> lastInteractTimes = new HashMap<>();
    @EventHandler
    public final void onSignClick(PlayerInteractEvent event) {
        Rotation rotation;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            rotation = Rotation.CLOCKWISE;
        }else if(event.getAction() == Action.LEFT_CLICK_BLOCK){
            rotation = Rotation.ANTICLOCKWISE;
        }else{
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER)) {
            return;
        }
        final Location loc = event.getClickedBlock().getLocation();

        // rotate subcraft
        String craftTypeStr = ChatColor.stripColor(sign.getLine(1));
        CraftType type = CraftManager.getInstance().getCraftTypeFromString(craftTypeStr);
        if (type == null) {
            return;
        }
        if (ChatColor.stripColor(sign.getLine(2)).equals("")
                && ChatColor.stripColor(sign.getLine(3)).equals("")) {
            sign.setLine(2, "_\\ /_");
            sign.setLine(3, "/ \\");
            sign.update(false, false);
        }

        if (!event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".pilot") || !event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".rotate")) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }
        final MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if(rotatingCrafts.contains(startPoint)){
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Rotation - Already Rotating"));
            event.setCancelled(true);
            return;
        }

        final Craft subCraft = new ICraft(type, loc.getWorld());
        final UUID playerID = event.getPlayer().getUniqueId();
        final Map<MovecraftLocation, CraftHolder> locationCraftHolderMap = subcraftMap.getOrDefault(playerID, new HashMap<>());
        locationCraftHolderMap.put(startPoint, new CraftHolder(subCraft, rotation));
        subcraftMap.put(playerID, locationCraftHolderMap);
        final long lastInteractTime = lastInteractTimes.getOrDefault(playerID, 0L);
        event.setCancelled(true);
        if (System.currentTimeMillis() - lastInteractTime <= 50) {
            return;
        }
        lastInteractTimes.put(playerID, System.currentTimeMillis());
        final Craft craft = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
        if(craft!=null) {
            if (!craft.isNotProcessing()) {
                event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Detection - Parent Craft is busy"));
                subcraftMap.remove(playerID);
                return;
            }
            craft.setProcessing(true); // prevent the parent craft from moving or updating until the subcraft is done
            new BukkitRunnable() {
                @Override
                public void run() {
                    craft.setProcessing(false);
                }
            }.runTaskLaterAsynchronously(Movecraft.getInstance(), (10));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                Map<MovecraftLocation, CraftHolder> locMap = subcraftMap.get(playerID);
                for (MovecraftLocation loc : locMap.keySet()) {
                    final CraftHolder craftHolder = locMap.get(loc);
                    craftHolder.getCraft().detect(null, event.getPlayer(), loc);
                    rotatingCrafts.add(loc);
                    Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(subCraft, CraftPilotEvent.Reason.SUB_CRAFT));
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (MovecraftLocation loc : locMap.keySet()) {
                            if(!rotatingCrafts.contains(loc)){
                                continue;
                            }
                            final CraftHolder craftHolder = locMap.get(loc);
                            craftHolder.getCraft().rotate(craftHolder.getRotation(), loc, true);
                        }
                    }
                }.runTaskLater(Movecraft.getInstance(), 3);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (MovecraftLocation loc : locMap.keySet()) {
                            if(!rotatingCrafts.contains(loc)){
                                continue;
                            }
                            rotatingCrafts.remove(loc);
                            final CraftHolder craftHolder = locMap.get(loc);
                            CraftManager.getInstance().removeCraft(craftHolder.getCraft());
                        }
                        subcraftMap.remove(playerID);
                    }
                }.runTaskLater(Movecraft.getInstance(), 6);
            }
        }.runTaskLaterAsynchronously(Movecraft.getInstance(), 2);

    }

    @EventHandler
    public void onCraftRelease(CraftReleaseEvent event){
        rotatingCrafts.removeAll(event.getCraft().getHitBox().asSet());
    }

    private static class CraftHolder {
        private final Craft craft;
        private final Rotation rotation;

        private CraftHolder(Craft craft, Rotation rotation) {
            this.craft = craft;
            this.rotation = rotation;
        }

        public Craft getCraft() {
            return craft;
        }

        public Rotation getRotation() {
            return rotation;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CraftHolder))
                return false;
            CraftHolder that = (CraftHolder) o;
            return getCraft() == that.getCraft() &&
                    getRotation() == that.getRotation();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getCraft(), getRotation());
        }
    }

}
