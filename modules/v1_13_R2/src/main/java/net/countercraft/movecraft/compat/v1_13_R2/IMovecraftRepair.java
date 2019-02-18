package net.countercraft.movecraft.compat.v1_13_R2;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.registry.state.EnumProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.countercraft.movecraft.MovecraftRepair;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.utils.FAWEUtils;
import net.countercraft.movecraft.utils.HashHitBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Slab;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Logger;

public class IMovecraftRepair extends MovecraftRepair {
    private HashMap<String, LinkedList<Vector>> locMissingBlocksMap = new HashMap<>();
    private HashMap<String, Long> numDiffBlocksMap = new HashMap<>();
    private HashMap<String, HashMap<Material, Double>> missingBlocksMap = new HashMap<>();
    @Override
    public boolean saveCraftRepairState(Craft craft, Sign sign, Plugin plugin, String s) {
        if (Settings.UseFAWE){
            return FAWEUtils.faweSaveCraftRepairState(craft,sign,plugin,s);
        }
        HashHitBox hitBox = craft.getHitBox();
        File saveDirectory = new File(plugin.getDataFolder(), "CraftRepairStates");
        World world = craft.getW();
        if (!saveDirectory.exists()){
            saveDirectory.mkdirs();
        }
        BlockVector3 minPos = BlockVector3.at(hitBox.getMinX(), hitBox.getMinY(), hitBox.getMinZ());
        BlockVector3 maxPos = BlockVector3.at(hitBox.getMaxX(), hitBox.getMaxY(), hitBox.getMaxZ());
        CuboidRegion region = new CuboidRegion(minPos, maxPos);
        com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        Extent source = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1);
        Extent destination = clipboard;
        ForwardExtentCopy copy = new ForwardExtentCopy(source, region, clipboard.getOrigin(), destination, minPos);
        ExistingBlockMask mask = new ExistingBlockMask(source);
        copy.setSourceMask(mask);
        try {
            Operations.complete(copy);
        } catch (WorldEditException e) {
            e.printStackTrace();
            return false;
        }
        File schematicFile = new File(saveDirectory, s + ".schematic");
        try {
            OutputStream output = new FileOutputStream(schematicFile);
            ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(output);
            writer.write(clipboard);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public boolean saveRegionRepairState(Plugin plugin, World world, org.bukkit.util.Vector vector, org.bukkit.util.Vector vector1, String s) {
        return false;
    }

    @Override
    public boolean repairRegion(World world, String s) {
        return false;
    }

    @Override
    public Clipboard loadCraftRepairStateClipboard(Plugin plugin, Sign sign, String s, World world) {
        File dataDirectory = new File(plugin.getDataFolder(), "CraftRepairStates");
        File file = new File(dataDirectory, s + ".schematic"); // The schematic file
        com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
        Clipboard clipboard = null;
        ClipboardFormat format = ClipboardFormats.findByFile(file);

        try {
            InputStream input = new FileInputStream(file);
            ClipboardReader reader = format.getReader(input);
            clipboard = reader.read();
            reader.close();


        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
        if (clipboard != null){
            long numDiffBlocks = 0;
            int index = 0;
            HashMap<Material, Double> missingBlocks = new HashMap<>();
            LinkedList<Vector> locMissingBlocks = new LinkedList<>();
            org.bukkit.util.Vector distMP = getDistanceFromSignToLowestPoint(clipboard,s);
            BlockVector3 minPos = BlockVector3.at(sign.getLocation().getBlockX() - distMP.getBlockX(),sign.getLocation().getBlockY() - distMP.getBlockY(),sign.getLocation().getBlockZ() - distMP.getBlockZ());
            BlockVector3 distance = BlockVector3.at(minPos.getBlockX() - clipboard.getMinimumPoint().getBlockX(), minPos.getBlockY() - clipboard.getMinimumPoint().getBlockY(),minPos.getBlockZ() - clipboard.getMinimumPoint().getBlockZ());
            for (int x = clipboard.getMinimumPoint().getBlockX(); x <= clipboard.getMaximumPoint().getBlockX(); x++) {
                for (int y = clipboard.getMinimumPoint().getBlockY(); y <= clipboard.getMaximumPoint().getBlockY(); y++) {
                    for (int z = clipboard.getMinimumPoint().getBlockZ(); z <= clipboard.getMaximumPoint().getBlockZ(); z++) {
                        index++;
                        BlockVector3 position = BlockVector3.at(x,y,z);
                        BaseBlock block = clipboard.getFullBlock(position);
                        Material type = BukkitAdapter.adapt(block.getBlockType());
                        Location loc = new Location(sign.getWorld(),x + distance.getBlockX(),y + distance.getBlockY(),z + distance.getBlockZ());
                        Block bukkitBlock = sign.getWorld().getBlockAt(loc);
                        boolean isImportant = true;

                        if (type.equals(Material.AIR) || type.equals(Material.CAVE_AIR) || type.equals(Material.VOID_AIR)){
                            isImportant = false;
                        }
                        boolean blockMissing = isImportant && type != bukkitBlock.getType();
                        if (type.name().endsWith("_SLAB")){
                            plugin.getLogger().info(type.name());
                            for (Property property : block.getStates().keySet()){
                                if (!(property instanceof EnumProperty)){
                                    continue;
                                }
                                @SuppressWarnings("unchecked")
                                String prop = (String) block.getState(property);
                                if (bukkitBlock.getBlockData() instanceof Slab){
                                    Slab slab = (Slab) bukkitBlock.getBlockData();
                                    blockMissing = slab.getType() != Slab.Type.valueOf(prop.toUpperCase());
                                }
                            }
                        }

                        if (blockMissing){
                            Material typeToConsume = type;
                            double qtyToConsume = 1.0;
                            if (type.equals(Material.WATER)||type.equals(Material.LAVA)){
                                qtyToConsume = 0;
                            }
                            if (type.name().endsWith("_SLAB")){
                                plugin.getLogger().info(type.name());
                                for (Property property : block.getStates().keySet()){
                                    if (!(property instanceof EnumProperty)){
                                        continue;
                                    }
                                    @SuppressWarnings("unchecked")
                                    String prop = (String) block.getState(property);
                                    if (prop.equals("double")){
                                        qtyToConsume = 2.0;
                                    }
                                }
                            }
                            if (type.equals(Material.WALL_SIGN)){
                                typeToConsume = Material.SIGN;
                            }
                            if (type.equals(Material.REDSTONE_WALL_TORCH)){
                                typeToConsume = Material.REDSTONE_TORCH;
                            }
                            if (type.equals(Material.WALL_TORCH)){
                                typeToConsume = Material.TORCH;
                            }
                            if (type.name().endsWith("_DOOR") || type.name().endsWith("_BED")){
                                qtyToConsume = 0.5;
                            }
                            if (type.equals(Material.DISPENSER)){
                                int numTNT = 0;
                                int numFirecharge = 0;
                                int numWaterBucket = 0;
                                ListTag listTag = block.getNbtData().getListTag("Items");
                                if (listTag != null) {
                                    for (Tag entry : listTag.getValue()) {
                                        //To avoid ClassCastExceptions, continue if tag is not a CompoundTag
                                        if (!(entry instanceof CompoundTag)) {
                                            continue;
                                        }
                                        CompoundTag cTag = (CompoundTag) entry;
                                        if (cTag.getString("id").equals("minecraft:tnt")) {
                                            numTNT += cTag.getByte("Count");
                                        }
                                        if (cTag.getString("id").equals("minecraft:fire_charge")) {
                                            numFirecharge += cTag.getByte("Count");
                                        }
                                        if (cTag.getString("id").equals("minecraft:water_bucket")) {
                                            numWaterBucket += cTag.getByte("Count");
                                        }
                                    }
                                }
                                if (numTNT > 0){
                                    if (missingBlocks.containsKey(Material.TNT)){
                                        double count = missingBlocks.get(Material.TNT);
                                        count += numTNT;
                                        missingBlocks.put(Material.TNT,count);
                                    } else {
                                        missingBlocks.put(Material.TNT, (double) numTNT);
                                    }
                                }
                                if (numFirecharge > 0){
                                    if (missingBlocks.containsKey(Material.FIRE_CHARGE)){
                                        double count = missingBlocks.get(Material.FIRE_CHARGE);
                                        count += numFirecharge;
                                        missingBlocks.put(Material.FIRE_CHARGE,count);
                                    } else {
                                        missingBlocks.put(Material.FIRE_CHARGE, (double) numFirecharge);
                                    }
                                }
                                if (numWaterBucket > 0){
                                    if (missingBlocks.containsKey(Material.WATER_BUCKET)){
                                        double count = missingBlocks.get(Material.WATER_BUCKET);
                                        count += numWaterBucket;
                                        missingBlocks.put(Material.WATER_BUCKET,count);
                                    } else {
                                        missingBlocks.put(Material.WATER_BUCKET, (double) numWaterBucket);
                                    }
                                }
                            }
                            locMissingBlocks.push(new Vector(x,y,z));
                            numDiffBlocks++;
                            if (missingBlocks.containsKey(typeToConsume)){
                                double count = missingBlocks.get(typeToConsume);
                                count += qtyToConsume;
                                missingBlocks.put(typeToConsume,count);
                            } else {
                                missingBlocks.put(typeToConsume,qtyToConsume);
                            }
                        }

                    }
                }
            }
            locMissingBlocksMap.put(s, locMissingBlocks);
            missingBlocksMap.put(s, missingBlocks);
            numDiffBlocksMap.put(s, numDiffBlocks);
        }
        return clipboard;
    }

    @Override
    public Clipboard loadRegionRepairStateClipboard(Plugin plugin, String s, World world) {
        return null;
    }

    @Override
    public HashMap<Material, Double> getMissingBlocks(String s) {
        return missingBlocksMap.get(s);
    }

    @Override
    public LinkedList<Vector> getMissingBlockLocations(String s) {

        return locMissingBlocksMap.get(s);
    }

    @Override
    public long getNumDiffBlocks(String s) {
        return numDiffBlocksMap.get(s);
    }

    @Override
    public Vector getDistanceFromSignToLowestPoint(Clipboard clipboard, String s) {
        org.bukkit.util.Vector returnDistance = null;
        for (int x = clipboard.getMinimumPoint().getBlockX(); x <= clipboard.getMaximumPoint().getBlockX(); x++) {
            for (int y = clipboard.getMinimumPoint().getBlockY(); y <= clipboard.getMaximumPoint().getBlockY(); y++) {
                for (int z = clipboard.getMinimumPoint().getBlockZ(); z <= clipboard.getMaximumPoint().getBlockZ(); z++) {
                    BlockVector3 pos = BlockVector3.at(x,y,z);
                    BaseBlock block = clipboard.getFullBlock(pos);
                    if (block.getBlockType().getId().equals("minecraft:sign")||block.getBlockType().getId().equals("minecraft:wall_sign")){
                        Logger log = Bukkit.getLogger();
                        String firstLine = block.getNbtData().getString("Text1");
                        firstLine = firstLine.substring(2);
                        if (firstLine.startsWith("extra")){
                            firstLine = firstLine.substring(17);
                            firstLine = firstLine.replace("\"}],\"text\":\"\"}","");
                        }
                        String secondLine = block.getNbtData().getString("Text2");
                        secondLine = secondLine.substring(2);
                        if (secondLine.startsWith("extra")){
                            secondLine = secondLine.substring(17);
                            secondLine = secondLine.replace("\"}],\"text\":\"\"}","");
                        }
                        if (firstLine.equalsIgnoreCase("Repair:") && s.endsWith(secondLine))
                            returnDistance = new Vector(x - clipboard.getMinimumPoint().getBlockX(),y - clipboard.getMinimumPoint().getBlockY(),z - clipboard.getMinimumPoint().getBlockZ());
                    }

                }
            }
        }
        return returnDistance;
    }


    @Override
    public org.bukkit.util.Vector getDistanceFromClipboardToWorldOffset(org.bukkit.util.Vector offset, Clipboard clipboard) {
        return new org.bukkit.util.Vector(offset.getBlockX() - clipboard.getMinimumPoint().getBlockX(), offset.getBlockY() - clipboard.getMinimumPoint().getBlockY(), offset.getBlockZ() - clipboard.getMinimumPoint().getBlockZ());
    }

    @Override
    public void setFawePlugin(Plugin fawePlugin) {

    }
}