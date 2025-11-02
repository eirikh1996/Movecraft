package net.countercraft.movecraft.processing.tasks.translation;

import com.google.common.collect.Sets;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.WorldHandler;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftCollisionEvent;
import net.countercraft.movecraft.events.CraftPreTranslateEvent;
import net.countercraft.movecraft.events.CraftTranslateEvent;
import net.countercraft.movecraft.events.FuelBurnEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.CachedMovecraftWorld;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.MonadicPredicate;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.processing.functions.TetradicPredicate;
import net.countercraft.movecraft.processing.tasks.translation.effects.TeleportationEffect;
import net.countercraft.movecraft.processing.tasks.translation.validators.HoverValidator;
import net.countercraft.movecraft.processing.tasks.translation.validators.MaxHeightValidator;
import net.countercraft.movecraft.processing.tasks.translation.validators.MinHeightValidator;
import net.countercraft.movecraft.processing.tasks.translation.validators.WorldBorderValidator;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.Tags;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.countercraft.movecraft.util.hitboxes.MutableHitBox;
import net.countercraft.movecraft.util.hitboxes.SetHitBox;
import net.countercraft.movecraft.util.hitboxes.SolidHitBox;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Event;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class TranslationTask implements Supplier<Effect> {

    private static final List<MonadicPredicate<Craft>> preTranslationValidators = new ArrayList<>();
    static {
        preTranslationValidators.add((craft -> craft.getHitBox().isEmpty() ? Result.failWithMessage("Empty hitbox") : Result.succeed()));
        preTranslationValidators.add((craft -> craft.getDisabled() && !(craft instanceof SinkingCraft)
                ? Result.failWithMessage(
                        I18nSupport.getInternationalisedString("Translation - Failed Craft Is Disabled"))
                : Result.succeed()));
    }
    private static final List<TetradicPredicate<MovecraftLocation, MovecraftWorld, HitBox, Craft>> translationValidators = new ArrayList<>();
    static {
        translationValidators.add(new MinHeightValidator());
        translationValidators.add(new MaxHeightValidator());
        translationValidators.add(new HoverValidator());
        translationValidators.add(new WorldBorderValidator());
    }

    private MovecraftRotation rotation;
    private final Craft craft;
    private MovecraftWorld destinationWorld;
    private MovecraftLocation translation;

    public TranslationTask(@NotNull Craft craft, @NotNull MovecraftLocation translation, @NotNull MovecraftWorld destinationWorld, @NotNull MovecraftRotation rotation) {
        this.rotation = rotation;
        this.craft = craft;
        this.translation = translation;
        this.destinationWorld = destinationWorld;
    }

    @Override
    public Effect get() {
        if (craft.getHitBox().isEmpty()) {
            return () -> {};
        }
        var preTranslationResult = preTranslationValidators.stream().reduce(MonadicPredicate::and).orElseThrow().validate(craft);
        if(!preTranslationResult.isSucess()){
            return () -> craft.getAudience().sendMessage(Component.text(preTranslationResult.getMessage()));
        }
        var preTranslateEvent = WorldManager.INSTANCE.executeMain(()->{
            var event = new CraftPreTranslateEvent(craft, translation.getX(), translation.getY(), translation.getZ(), craft.getWorld());
            Bukkit.getServer().getPluginManager().callEvent(event);
            return event;
        });
        if (preTranslateEvent.isCancelled()) {
            return ()-> craft.getAudience().sendMessage(Component.text(preTranslateEvent.getFailMessage()));
        }
        translation = new MovecraftLocation(preTranslateEvent.getDx(), preTranslateEvent.getDy(), preTranslateEvent.getDz());
        destinationWorld = CachedMovecraftWorld.of(preTranslateEvent.getWorld());
        //TODO: Portal movement
        //TODO: Gravity
        var destinationLocations = new SetHitBox();
        var collisions = new SetHitBox();
        var phaseLocations = new SetHitBox();
        var harvestLocations = new SetHitBox();
        var fuelSources = new ArrayList<FurnaceInventory>();
        var passthroughBlocks = new HashSet<>(craft.getType().getMaterialSetProperty(CraftType.PASSTHROUGH_BLOCKS));
        if (craft instanceof SinkingCraft) {
            passthroughBlocks.addAll(Tags.FLUID);
            passthroughBlocks.addAll(Tag.LEAVES.getValues());
            passthroughBlocks.addAll(Tags.SINKING_PASSTHROUGH);

        }
        for(var originLocation : craft.getHitBox()){
            var originMaterial = craft.getMovecraftWorld().getMaterial(originLocation);
            // Remove air from hitboxes
            if(originMaterial.isAir())
                continue;
            if(Tags.FURNACES.contains(originMaterial)) {
                var state = craft.getMovecraftWorld().getState(originLocation);
                if(state instanceof FurnaceInventory)
                    fuelSources.add((FurnaceInventory) state);
            }

            var destination = originLocation.add(translation);

            destinationLocations.add(destination);
            // previous locations cannot collide
            if(craft.getMovecraftWorld().equals(destinationWorld) && craft.getHitBox().contains(destination)){
                continue;
            }
            var destinationMaterial = destinationWorld.getMaterial(destination);
            if (destinationMaterial.isAir()) { //Do not collide with air
                continue;
            }
            if(craft.getType().getMaterialSetProperty(CraftType.PASSTHROUGH_BLOCKS).contains(destinationMaterial)){
                phaseLocations.add(destination);
                continue;
            }
            if(craft.getType().getMaterialSetProperty(CraftType.HARVEST_BLOCKS).contains(destinationMaterial) &&
                    craft.getType().getMaterialSetProperty(CraftType.HARVESTER_BLADE_BLOCKS).contains(originMaterial)){
                harvestLocations.add(destination);
                continue;
            }
            collisions.add(destination);
        }
        double fuelBurnRate = (double) craft.getType().getPerWorldProperty(CraftType.PER_WORLD_FUEL_BURN_RATE, preTranslateEvent.getWorld());
        Effect fuelBurnEffect;
        if (craft.getBurningFuel() >= fuelBurnRate) {
            //call event
            final FuelBurnEvent event = new FuelBurnEvent(craft, craft.getBurningFuel(), fuelBurnRate);
            submitEvent(event);
            fuelBurnEffect = () -> craft.setBurningFuel(event.getBurningFuel() - event.getFuelBurnRate());
        } else {
            var fuelSource = findFuelHolders(craft.getType(), fuelSources);
            if(fuelSource == null){
                return () -> craft.getAudience().sendMessage(I18nSupport.getInternationalisedComponent("Translation - Failed Craft out of fuel"));
            }
            callFuelEvent(craft, findFuelStack(craft.getType(), fuelSource));
            //TODO: Take Fuel
            fuelBurnEffect = () -> Bukkit.getLogger().info("This is where we'd take ur fuel, if we had some");
        }
        var translationResult = translationValidators.stream().reduce(TetradicPredicate::and).orElseThrow().validate(translation, destinationWorld, destinationLocations, craft);
        if(!translationResult.isSucess()){
            return () -> craft.getAudience().sendMessage(Component.text(translationResult.getMessage()));
        }



        // Direct float comparison due to check for statically initialized value
        callCollisionEvent(craft, collisions, preTranslateEvent.getWorld());
        final float collisionExplosion = craft.getType().getFloatProperty(CraftType.COLLISION_EXPLOSION);
        if(collisionExplosion <= 0F && !collisions.isEmpty()){
            if (craft instanceof SinkingCraft) {
                //TODO: collision highlights
                for (MovecraftLocation collision : collisions) {
                    Material type = destinationWorld.getMaterial(collision);
                    if (type.isAir() || !passthroughBlocks.contains(type)) {
                        continue;
                    }
                    destinationLocations.remove(collision);
                    craft.getCollapsedHitBox().add(collision);
                }

            } else {
                MovecraftLocation firstCollision = Collections.min(collisions.asSet(), MovecraftLocation::compareTo);
                Object obj = craft.getType().getObjectProperty(CraftType.COLLISION_SOUND);
                if (!(obj instanceof Sound collisionSound)) {
                    throw new IllegalStateException("COLLISION_SOUND must be of type Sound");
                }
                return () -> {
                    craft.getAudience().playSound(collisionSound);
                    craft.getAudience().sendMessage(Component.text(String.format(I18nSupport.getInternationalisedString("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                            firstCollision.getX(),
                            firstCollision.getY(),
                            firstCollision.getZ(),
                            destinationWorld.getMaterial(firstCollision))));
                };
            }
        }
        Effect fluidBoxEffect = fluidBox(craft, translation);
        var  translateEvent = callTranslateEvent(craft, destinationLocations, preTranslateEvent.getWorld());


        final MovecraftWorld originWorld = craft.getMovecraftWorld();
        final MutableHitBox totalOriginHitBox = new SetHitBox(craft.getHitBox());
        final MutableHitBox totalDestinationHitBox = new SetHitBox(destinationLocations);
        if (!passthroughBlocks.isEmpty()) {
            final var invertedHitBox = Sets.difference(craft.getHitBox().boundingHitBox().asSet(), craft.getHitBox().asSet());
            Set<Location> overlap = new HashSet<>(craft.getPhaseBlocks().keySet());
            overlap.removeIf((location -> !craft.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(location))));
            final int minX = craft.getHitBox().getMinX();
            final int maxX = craft.getHitBox().getMaxX();
            final int minY = craft.getHitBox().getMinY();
            final int maxY = overlap.isEmpty() ? craft.getHitBox().getMaxY() : Collections.max(overlap,
                    Comparator.comparingInt(Location::getBlockY)).getBlockY();
            final int minZ = craft.getHitBox().getMinZ();
            final int maxZ = craft.getHitBox().getMaxZ();
            final HitBox[] surfaces = {
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, maxY, minZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(maxX, maxY, minZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, minY, maxZ))
            };
            final SetHitBox validExterior = new SetHitBox();
            for (HitBox surface : surfaces) {
                validExterior.addAll(Sets.difference(surface.asSet(), craft.getHitBox().asSet()));
            }
            //Check to see which locations in the from set are actually outside of the craft
            final Set<MovecraftLocation> confirmed = craft instanceof SinkingCraft
                    ? invertedHitBox.copyInto(new LinkedHashSet<>())
                    : verifyExterior(invertedHitBox, validExterior);
            final Set<MovecraftLocation> failed = Sets.difference(invertedHitBox, confirmed);
            failed.forEach((moveLoc) -> {
                totalOriginHitBox.add(moveLoc);
                totalDestinationHitBox.add(moveLoc.add(translation));
            });

        }
        //TODO: Sinking?
        //TODO: Collision explosion
        //TODO: phase blocks
        Effect phaseEffect = phaseBlocks(craft, destinationWorld, totalOriginHitBox, totalDestinationHitBox, passthroughBlocks);
        Effect movementEffect = moveCraft(craft, translation, destinationLocations, destinationWorld);
        Effect unphaseEffect = unphaseBlocks(craft, Objects.requireNonNull(Bukkit.getWorld(originWorld.getWorldUUID())), totalOriginHitBox, totalDestinationHitBox, passthroughBlocks);
        //TODO: un-phase blocks
        Effect teleportEffect = new TeleportationEffect(craft, translation, translateEvent.getWorld());
        return fuelBurnEffect
                .andThen(fluidBoxEffect)
                .andThen(phaseEffect)
                .andThen(movementEffect)
                .andThen(unphaseEffect)
                .andThen(teleportEffect);
    }

    @Contract("_ -> param1")
    private static <T extends Event> T submitEvent(@NotNull T event ){
        WorldManager.INSTANCE.executeMain(() -> Bukkit.getServer().getPluginManager().callEvent(event));
        return event;
    }

    private static @NotNull Effect moveCraft(Craft craft, MovecraftLocation translation, HitBox destinationLocations, MovecraftWorld destinationWorld){
        final WorldHandler handler = Movecraft.getInstance().getWorldHandler();
        final World bukkitWorld = Bukkit.getWorld(destinationWorld.getWorldUUID());
        if (bukkitWorld == null) {
            throw new UnsupportedOperationException("Destination world " + destinationWorld.getName() + " is null");
        }
        craft.setHitBox(destinationLocations);
        return () -> {
            handler.translateCraft(craft, translation, bukkitWorld);
            craft.setWorld(bukkitWorld);
        };
    }

    private static @NotNull Effect fluidBox(Craft craft, MovecraftLocation translation){
        var newFluids = new SetHitBox();
        for(var location : craft.getFluidLocations()){
            newFluids.add(location.add(translation));
        }
        return () -> craft.setFluidLocations(newFluids);
    }

    private static @NotNull CraftCollisionEvent callCollisionEvent(@NotNull Craft craft, @NotNull HitBox collided, @NotNull World destinationWorld){
        return submitEvent(new CraftCollisionEvent(craft, collided, destinationWorld));
    }

    private static @NotNull CraftTranslateEvent callTranslateEvent(@NotNull Craft craft, @NotNull HitBox destinationHitBox, @NotNull World destinationWorld){
        return submitEvent(new CraftTranslateEvent(craft, craft.getHitBox(), destinationHitBox, destinationWorld));
    }

    private static @Nullable FurnaceInventory findFuelHolders(CraftType type, List<FurnaceInventory> inventories){
        for(var inventory : inventories){
            var stack = findFuelStack(type, inventory);
            if(stack != null){
                return inventory;
            }
        }
        return null;
    }

    private static @Nullable ItemStack findFuelStack(@NotNull CraftType type, @NotNull FurnaceInventory inventory){
        var v = type.getObjectProperty(CraftType.FUEL_TYPES);
        if(!(v instanceof Map<?, ?>))
            throw new IllegalStateException("FUEL_TYPES must be of type Map");
        var fuelTypes = (Map<?, ?>) v;
        for(var e : fuelTypes.entrySet()) {
            if(!(e.getKey() instanceof Material))
                throw new IllegalStateException("Keys in FUEL_TYPES must be of type Material");
            if(!(e.getValue() instanceof Double))
                throw new IllegalStateException("Values in FUEL_TYPES must be of type Double");
        }

        for(var item : inventory) {
            if(item == null || !fuelTypes.containsKey(item.getType())){
                continue;
            }
            return item;
        }
        return null;
    }

    private static @NotNull FuelBurnEvent callFuelEvent(@NotNull Craft craft, @NotNull ItemStack burningFuel) {
        var v = craft.getType().getObjectProperty(CraftType.FUEL_TYPES);
        if(!(v instanceof Map<?, ?>))
            throw new IllegalStateException("FUEL_TYPES must be of type Map");
        var fuelTypes = (Map<?, ?>) v;
        for(var e : fuelTypes.entrySet()) {
            if(!(e.getKey() instanceof Material))
                throw new IllegalStateException("Keys in FUEL_TYPES must be of type Material");
            if(!(e.getValue() instanceof Double))
                throw new IllegalStateException("Values in FUEL_TYPES must be of type Double");
        }

        double fuelBurnRate = (double) craft.getType().getPerWorldProperty(CraftType.PER_WORLD_FUEL_BURN_RATE, craft.getWorld());
        return submitEvent(new FuelBurnEvent(craft, (double) fuelTypes.get(burningFuel.getType()), fuelBurnRate));
    }

    private static @NotNull Effect phaseBlocks(@NotNull Craft craft, @NotNull MovecraftWorld destinationWorld, @NotNull HitBox originLocations, @NotNull HitBox destinationLocations, @NotNull Set<Material> passthroughBlocks) {
        if (passthroughBlocks.isEmpty())
            return () -> { };
        return () -> {
            for (MovecraftLocation moveLoc : destinationLocations) {
                if (craft.getMovecraftWorld() == destinationWorld && originLocations.contains(moveLoc))
                    continue;
                Location bukkitLoc = moveLoc.toBukkit(Bukkit.getWorld(destinationWorld.getWorldUUID()));
                final BlockData data = bukkitLoc.getBlock().getBlockData();
                if (!passthroughBlocks.contains(data.getMaterial())) {
                    continue;
                }
                if (craft.getPhaseBlocks().containsKey(bukkitLoc)) {
                    continue;
                }
                craft.getPhaseBlocks().put(bukkitLoc, data);
            }
        };
    }

    private static @NotNull Effect unphaseBlocks(@NotNull Craft craft, @NotNull World oldWorld,  @NotNull HitBox originHitBox, @NotNull HitBox destinationHitBox, @NotNull Set<Material> passthroughBlocks) {
        if (passthroughBlocks.isEmpty())
            return () -> { };
        WorldHandler handler = Movecraft.getInstance().getWorldHandler();
        return () -> {
            for (MovecraftLocation origLoc : originHitBox) {
                final Location bukkit = origLoc.toBukkit(oldWorld);
                if (!destinationHitBox.contains(origLoc) && craft.getPhaseBlocks().containsKey(bukkit)) {
                    BlockData data = craft.getPhaseBlocks().remove(bukkit);
                    handler.setBlockFast(bukkit, data);
                }
            }
        };
    }

    @NotNull
    private static Set<MovecraftLocation> verifyExterior(Set<MovecraftLocation> invertedHitBox, SetHitBox validExterior) {
        var shifts = new MovecraftLocation[]{new MovecraftLocation(0,-1,0),
                new MovecraftLocation(1,0,0),
                new MovecraftLocation(-1,0,0),
                new MovecraftLocation(0,0,1),
                new MovecraftLocation(0,0,-1)};
        Set<MovecraftLocation> visited = new LinkedHashSet<>(validExterior.asSet());
        Queue<MovecraftLocation> queue = new ArrayDeque<>();
        for(var node : validExterior){
            //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
            for(var shift : shifts){
                var shifted = node.add(shift);
                if(invertedHitBox.contains(shifted) && visited.add(shifted)){
                    queue.add(shifted);
                }
            }
        }
        while (!queue.isEmpty()) {
            var node = queue.poll();
            //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
            for(var shift : shifts){
                var shifted = node.add(shift);
                if(invertedHitBox.contains(shifted) && visited.add(shifted)){
                    queue.add(shifted);
                }
            }
        }
        return visited;
    }
}
