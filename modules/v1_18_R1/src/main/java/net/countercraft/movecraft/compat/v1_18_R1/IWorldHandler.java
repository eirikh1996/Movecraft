package net.countercraft.movecraft.compat.v1_18_R1;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.Rotation;
import net.countercraft.movecraft.WorldHandler;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.utils.CollectionUtils;
import net.countercraft.movecraft.utils.MathUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.ticks.ScheduledTick;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_18_R1.util.CraftMagicNumbers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class IWorldHandler extends WorldHandler {
    private static final net.minecraft.world.level.block.Rotation[] ROTATION;
    @Nullable private static final Unsafe unsafe;
    static {
        Unsafe deferred;
        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            deferred = (Unsafe) theUnsafeField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            deferred = null;
            e.printStackTrace();
        }
        unsafe = deferred;

        ROTATION = new net.minecraft.world.level.block.Rotation[3];
        ROTATION[Rotation.NONE.ordinal()] = net.minecraft.world.level.block.Rotation.NONE;
        ROTATION[Rotation.CLOCKWISE.ordinal()] = net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
        ROTATION[Rotation.ANTICLOCKWISE.ordinal()] = net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;

    }
    private final NextTickProvider tickProvider = new NextTickProvider();
    private MethodHandle internalTeleportMH;

    public IWorldHandler() {
    }

    @Override
    public void rotateCraft(@NotNull Craft craft, @NotNull MovecraftLocation originPoint, @NotNull Rotation rotation) {
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        HashMap<BlockPos, BlockPos> rotatedPositions = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        for(MovecraftLocation newLocation : craft.getHitBox()){
            rotatedPositions.put(locationToPosition(MathUtils.rotateVec(counterRotation, newLocation.subtract(originPoint)).add(originPoint)),locationToPosition(newLocation));
        }
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        ServerLevel nativeWorld = ((CraftWorld) craft.getWorld()).getHandle();
        List<TileHolder> tiles = new ArrayList<>();
        //get the tiles
        for(BlockPos position : rotatedPositions.keySet()){
            //BlockEntity tile = nativeWorld.removeBlockEntity(position);
            BlockEntity tile = removeBlockEntity(nativeWorld,position);
            if(tile == null)
                continue;
            //tile.setBlockState(tile.getBlockState().rotate(ROTATION[rotation.ordinal()]));
            //get the nextTick to move with the tile
            tiles.add(new TileHolder(tile, tickProvider.getNextTick(nativeWorld, position), position));
        }

        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and rotate them
        HashMap<BlockPos, BlockState> blockData = new HashMap<>();
        for(BlockPos position : rotatedPositions.keySet()){

            blockData.put(position,nativeWorld.getBlockState(position).rotate(ROTATION[rotation.ordinal()]));
        }
        HashMap<BlockPos, BlockState> redstoneComponents = new HashMap<>();
        //create the new block
        for(Map.Entry<BlockPos,BlockState> entry : blockData.entrySet()) {
            final BlockPos newPosition = rotatedPositions.get(entry.getKey());
            final BlockState iBlockData = entry.getValue();
            if (nativeWorld.getBlockEntity(newPosition) != null) {
                removeBlockEntity(nativeWorld, newPosition);
            }
            if (isRedstoneComponent(iBlockData))
                redstoneComponents.put(newPosition, iBlockData);

            setBlockFast(nativeWorld, newPosition, iBlockData);
        }


        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        //TODO: go by chunks
        for(TileHolder tileHolder : tiles){
            moveBlockEntity(nativeWorld, rotatedPositions.get(tileHolder.getTilePosition()),tileHolder.getTile());
            if(tileHolder.getNextTick()==null)
                continue;
            final long currentTime = nativeWorld.getGameTime();
            nativeWorld.getBlockTicks().schedule(
                    new ScheduledTick<>(
                            (Block) tileHolder.getNextTick().type(),
                            rotatedPositions.get(tileHolder.getNextTick().pos()),
                    tileHolder.getNextTick().triggerTick() - currentTime,
                    tileHolder.getNextTick().priority(),
                    tileHolder.getNextTick().subTickOrder())
            );
        }

        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        //TODO: add support for pass-through
        Collection<BlockPos> deletePositions =  CollectionUtils.filter(rotatedPositions.keySet(),rotatedPositions.values());
        for(BlockPos position : deletePositions){
            setBlockFast(nativeWorld, position, Blocks.AIR.defaultBlockState());
        }

        //*******************************************
        //*   Step six: Process fire spread         *
        //*******************************************
        for (BlockPos position : rotatedPositions.values()) {
            BlockState type = nativeWorld.getBlockState(position);
            if (!(type.getBlock() instanceof FireBlock)) {
                continue;
            }
            FireBlock fire = (FireBlock) type.getBlock();
            fire.tick(type, nativeWorld, position, nativeWorld.random);
        }

        for (Map.Entry<BlockPos, BlockState> entry : redstoneComponents.entrySet()) {
            final BlockPos position = entry.getKey();
            final BlockState data = entry.getValue();
            LevelChunk chunk = nativeWorld.getChunkAt(position);
            LevelChunkSection chunkSection = chunk.getSections()[position.getY()>>4];
            if (chunkSection == null) {
                // Put a GLASS block to initialize the section. It will be replaced next with the real block.
                chunk.setBlockState(position, Blocks.GLASS.defaultBlockState(), false);
                chunkSection = chunk.getSections()[chunk.getSectionIndex(position.getY())];

            }
            if (chunkSection.getBlockState(position.getX()&15, position.getY()&15, position.getZ()&15).equals(data)) {
                return;
            }
            chunkSection.setBlockState(position.getX()&15, position.getY()&15, position.getZ()&15, data);
            nativeWorld.sendBlockUpdated(position, data, data, 3);
            nativeWorld.getLightEngine().checkBlock(position); // boolean corresponds to if chunk section empty
            chunk.setUnsaved(true);
        }
    }

    @Override
    public void translateCraft(@NotNull Craft craft, @NotNull MovecraftLocation displacement, @NotNull org.bukkit.World world) {
        //TODO: Add supourt for rotations
        //A craftTranslateCommand should only occur if the craft is moving to a valid position
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        BlockPos translateVector = locationToPosition(displacement);
        List<BlockPos> positions = new ArrayList<>();
        for(MovecraftLocation movecraftLocation : craft.getHitBox()) {
            positions.add(moveBlockPos(locationToPosition(movecraftLocation), translateVector, true));
        }
        ServerLevel oldNativeWorld = ((CraftWorld) craft.getWorld()).getHandle();
        ServerLevel nativeWorld = ((CraftWorld) world).getHandle();
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        List<TileHolder> tiles = getTiles(positions, oldNativeWorld);
        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and translate the positions
        List<BlockState> blockData = new ArrayList<>();
        List<BlockPos> newPositions = new ArrayList<>();
        for(BlockPos position : positions){
            blockData.add(oldNativeWorld.getBlockState(position));
            newPositions.add(moveBlockPos(position, translateVector, false));
        }
        //create the new block
        setBlocks(newPositions, blockData, nativeWorld);
        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        //TODO: go by chunks
        processTiles(tiles, nativeWorld, translateVector);
        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        Collection<BlockPos> deletePositions = oldNativeWorld == nativeWorld ? CollectionUtils.filter(positions,newPositions) : positions;
        setAir(deletePositions, oldNativeWorld);
        //*******************************************
        //*   Step six: Process fire spread         *
        //*******************************************
        processFireSpread(newPositions, nativeWorld);
        //*******************************************
        //*   Step six: Process redstone        *
        //*******************************************
        processRedstone(newPositions, nativeWorld);
    }


    private void setBlocks(List<BlockPos> newPositions, List<BlockState> blockData, ServerLevel nativeWorld){
        Map<BlockPos, BlockState> redstoneComponents = new HashMap<>();
        for(int i = 0; i<newPositions.size(); i++) {
            setBlockFast(nativeWorld, newPositions.get(i), blockData.get(i));
        }
    }

    private List<TileHolder> getTiles(Iterable<BlockPos> positions, ServerLevel nativeWorld){
        List<TileHolder> tiles = new ArrayList<>();
        //get the tiles
        for(BlockPos position : positions){
            if(nativeWorld.getBlockState(position) == Blocks.AIR.defaultBlockState())
                continue;
            //BlockEntity tile = nativeWorld.removeBlockEntity(position);
            BlockEntity tile = removeBlockEntity(nativeWorld,position);
            if(tile == null)
                continue;
            //get the nextTick to move with the tile

            //nativeWorld.capturedTileEntities.remove(position);
            //nativeWorld.getChunkAtWorldCoords(position).getTileEntities().remove(position);
            tiles.add(new TileHolder(tile, tickProvider.getNextTick(nativeWorld, position), position));

        }
        return tiles;
    }

    private BlockPos moveBlockPos(BlockPos original, BlockPos translateVector, boolean opposite) {
        if (opposite)
            return new BlockPos(original.getX() - translateVector.getX(), original.getY() - translateVector.getY(), original.getZ() - translateVector.getZ());

        return new BlockPos(original.getX() + translateVector.getX(), original.getY() + translateVector.getY(), original.getZ() + translateVector.getZ());
    }


    private void processTiles(Iterable<TileHolder> tiles, ServerLevel world, BlockPos translateVector){
        for(TileHolder tileHolder : tiles){
            BlockPos newPos =  moveBlockPos(tileHolder.getTilePosition(), translateVector, false);
            moveBlockEntity(world, newPos,tileHolder.getTile());
            if(tileHolder.getNextTick()==null)
                continue;
            final long currentTime = world.getGameTime();
            world.getBlockTicks().schedule(
                    new ScheduledTick<>(
                            (Block) tileHolder.getNextTick().type(),
                            newPos,
                            tileHolder.getNextTick().triggerTick() - currentTime,
                            tileHolder.getNextTick().priority(),
                            tileHolder.getNextTick().subTickOrder()));
        }
    }

    private void processFireSpread(Iterable<BlockPos> positions, ServerLevel world){
        for (BlockPos position : positions) {
            BlockState type = world.getBlockState(position);
            if (!(type.getBlock() instanceof FireBlock)) {
                continue;
            }
            FireBlock fire = (FireBlock) type.getBlock();
            fire.tick(type, world, position, world.random);
        }
    }

    private void processRedstone(List<BlockPos> redstone, ServerLevel world) {
        Map<BlockPos, BlockState> redstoneComponents = new HashMap<>();
        for (int i = 0 ; i < redstone.size(); i++) {
            BlockPos pos = redstone.get(i);
            BlockState data = world.getBlockState(pos);
            if (isRedstoneComponent(data))
                redstoneComponents.put(pos, data);
        }
        for (Map.Entry<BlockPos, BlockState> entry : redstoneComponents.entrySet()) {
            final BlockPos position = entry.getKey();
            final BlockState data = entry.getValue();
            LevelChunk chunk = world.getChunkAt(position);
            LevelChunkSection chunkSection = chunk.getSections()[position.getY()>>4];
            if (chunkSection == null) {
                // Put a GLASS block to initialize the section. It will be replaced next with the real block.
                chunk.setBlockState(position, Blocks.GLASS.defaultBlockState(), false);
                chunkSection = chunk.getSections()[chunk.getSectionIndex(position.getY())];

            }
            if (chunkSection.getBlockState(position.getX()&15, position.getY()&15, position.getZ()&15).equals(data)) {
                return;
            }
            chunkSection.setBlockState(position.getX()&15, position.getY()&15, position.getZ()&15, data);
            world.sendBlockUpdated(position, data, data, 3);
            world.getLightEngine().checkBlock(position); // boolean corresponds to if chunk section empty
            chunk.setUnsaved(true);
        }
    }

    private void setAir(Iterable<BlockPos> positions, ServerLevel world){
        for(BlockPos position : positions){
            setBlockFast(world, position, Blocks.AIR.defaultBlockState());
        }
    }

    @Nullable
    private BlockEntity removeBlockEntity(@NotNull ServerLevel world, @NotNull BlockPos position){
        return world.getChunkAt(position).blockEntities.remove(position);
    }

    @NotNull
    private BlockPos locationToPosition(@NotNull MovecraftLocation loc) {
        return new BlockPos(loc.getX(), loc.getY(), loc.getZ());
    }

    private void setBlockFast(@NotNull ServerLevel world, @NotNull BlockPos position,@NotNull BlockState data) {
        LevelChunk chunk = world.getChunkAt(position);
        LevelChunkSection chunkSection = chunk.getSections()[chunk.getSectionIndex(position.getY())];

        if (chunkSection == null) {
            // Put a GLASS block to initialize the section. It will be replaced next with the real block.
            chunk.setBlockState(position, Blocks.GLASS.defaultBlockState(), false);
            chunkSection = chunk.getSections()[chunk.getSectionIndex(position.getY())];

        }
        if (chunkSection.getBlockState(position.getX()&15, position.getY()&15, position.getZ()&15).equals(data) && data != Blocks.GLASS.defaultBlockState()) {
            return;
        }
        chunkSection.setBlockState(position.getX()&15, position.getY()&15, position.getZ()&15, data);
        world.sendBlockUpdated(position, data, data, 3);
        world.getLightEngine().checkBlock(position); // boolean corresponds to if chunk section empty
        chunk.setUnsaved(true);
    }

    @Override
    public void setBlockFast(@NotNull Location location, @NotNull Material material, Object data){
        setBlockFast(location, Rotation.NONE, material, data);
    }

    @Override
    public void setBlockFast(@NotNull Location location, @NotNull Rotation rotation, @NotNull Material material, Object data) {
        BlockState blockData;
        if (!(data instanceof BlockData)) {
            blockData = CraftMagicNumbers.getBlock(material).defaultBlockState();
        } else {
            blockData = ((CraftBlockData) data).getState();
        }

        blockData = blockData.rotate(ROTATION[rotation.ordinal()]);
        ServerLevel world = ((CraftWorld)(location.getWorld())).getHandle();
        BlockPos blockPosition = locationToPosition(bukkit2MovecraftLoc(location));
        if (world.getBlockEntity(blockPosition) != null) {
            removeBlockEntity(world, blockPosition);
        }
        setBlockFast(world,blockPosition,blockData);
    }

    @Override
    public void disableShadow(@NotNull Material type) {
        Method method;
        //try {
        Block tempBlock = CraftMagicNumbers.getBlock(type);
        //method = Block.class.getDeclaredMethod("d", int.class);
        //method.setAccessible(true);
        //method.invoke(tempBlock, 0);
        //} catch (NoSuchMethodException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | SecurityException e1) {
        // TODO Auto-generated catch block
        //    e1.printStackTrace();
        //}
    }

    private static MovecraftLocation bukkit2MovecraftLoc(Location l) {
        return new MovecraftLocation(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private void moveBlockEntity(@NotNull ServerLevel nativeWorld, @NotNull BlockPos newPosition, @NotNull BlockEntity tile){
        LevelChunk chunk = nativeWorld.getChunkAt(newPosition);
        try {
            var positionField = BlockEntity.class.getDeclaredField("o");
            setField(positionField, tile, newPosition);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        tile.setLevel(nativeWorld);
        tile.clearRemoved();
        if (nativeWorld.captureBlockStates)
            nativeWorld.capturedTileEntities.put(newPosition, tile);
        chunk.setBlockEntity(tile);
        chunk.blockEntities.put(newPosition, tile);
    }

    private class TileHolder{
        @NotNull private final BlockEntity tile;
        @Nullable
        private final ScheduledTick<?> nextTick;
        @NotNull private final BlockPos tilePosition;

        public TileHolder(@NotNull BlockEntity tile, @Nullable ScheduledTick<?> nextTick, @NotNull BlockPos tilePosition){
            this.tile = tile;
            this.nextTick = nextTick;
            this.tilePosition = tilePosition;
        }


        @NotNull
        public BlockEntity getTile() {
            return tile;
        }

        @Nullable
        public ScheduledTick<?> getNextTick() {
            return nextTick;
        }

        @NotNull
        public BlockPos getTilePosition() {
            return tilePosition;
        }
    }

    private boolean isRedstoneComponent(BlockState blockData) {
        final Block block = blockData.getBlock();
        return block instanceof RedStoneWireBlock ||
                block instanceof ComparatorBlock ||
                block instanceof ButtonBlock ||
                block instanceof LeverBlock;

    }

    private void setField(Field field, Object holder, Object value) {
        if (unsafe == null)
            return;
        unsafe.putObject(holder, unsafe.objectFieldOffset(field), value);
    }


}

