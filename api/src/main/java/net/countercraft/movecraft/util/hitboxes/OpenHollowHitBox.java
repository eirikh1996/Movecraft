package net.countercraft.movecraft.util.hitboxes;

import net.countercraft.movecraft.MovecraftLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

public final class OpenHollowHitBox implements HitBox {
    final private int minX, minY, minZ, maxX, maxY, maxZ;

    public OpenHollowHitBox(MovecraftLocation startBound, MovecraftLocation endBound) {
        if(startBound.getX() < endBound.getX()){
            minX = startBound.getX();
            maxX = endBound.getX();
        } else {
            maxX = startBound.getX();
            minX = endBound.getX();
        }
        if(startBound.getY() < endBound.getY()){
            minY = startBound.getY();
            maxY = endBound.getY();
        } else {
            maxY = startBound.getY();
            minY = endBound.getY();
        }
        if(startBound.getZ() < endBound.getZ()){
            minZ = startBound.getZ();
            maxZ = endBound.getZ();
        } else {
            maxZ = startBound.getZ();
            minZ = endBound.getZ();
        }
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMinZ() {
        return minZ;
    }

    @Override
    public int getMaxX() {
        return maxX;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public int getMaxZ() {
        return maxZ;
    }

    @Override
    public int size() {
        int size;
        if (this.getXLength() <= 2 || this.getYLength() <= 2 || this.getZLength() <= 2) {//No hollow space exists if at least one of the sides are two blocks in length
            size = (this.getXLength()) * (this.getYLength()) * (this.getZLength());
        } else {
            size = ((this.getXLength()) * (this.getYLength()) * (this.getZLength())) - ((this.getXLength() - 2) * (this.getYLength() - 1) * (this.getZLength() - 2));
        }
        return size;
    }

    @Override
    public int getXLength() {
        return Math.abs(maxX - minX) + 1;
    }

    @Override
    public int getYLength() {
        return Math.abs(maxY - minY) + 1;
    }

    @Override
    public int getZLength() {
        return Math.abs(maxZ - minZ) + 1;
    }

    @Override
    public @NotNull Iterator<MovecraftLocation> iterator() {
        return new Iterator<>() {
            private int lastX = minX;
            private int lastY = minY;
            private int lastZ = minZ;

            @Override
            public boolean hasNext() {
                return lastY <= maxY;
            }

            @Override
            public MovecraftLocation next() {
                MovecraftLocation output = new MovecraftLocation(lastX, lastY, lastZ);
                if (lastY == minY) { //Bottom and top surface of the hollow hitbox
                    lastX++;
                    if (lastX > maxX) {
                        lastX = minX;
                        lastZ++;
                    }
                    if (lastZ > maxZ) {
                        lastZ = minZ;
                        lastY++;
                    }
                } else if (lastZ == minZ || lastZ == maxZ) { //Sides along z axis
                    lastX++;
                    if (lastX > maxX) {
                        lastX = minX;
                        lastZ++;
                    }
                    if (lastZ > maxZ) {
                        lastZ = minZ;
                        lastY++;
                    }
                } else if (lastX == minX || lastX == maxX) {
                    if (lastX == minX) {
                        lastX = maxX;
                    } else {
                        lastX = minX;
                        lastZ++;
                    }
                }

                return output;
            }
        };
    }

    @Override
    public boolean contains(@NotNull MovecraftLocation location) {
        return ((location.getX() == minX || location.getX() == maxX) && (location.getY() >= minY && location.getY() <= maxY) && (location.getZ() >= minZ && location.getZ() <= maxZ)) ||
                ((location.getX() >= minX && location.getX() <= maxX) && (location.getY() == minY || location.getY() == maxY) && (location.getZ() >= minZ && location.getZ() <= maxZ)) ||
                ((location.getX() == minX && location.getX() == maxX) && (location.getY() >= minY && location.getY() <= maxY) && (location.getZ() == minZ || location.getZ() == maxZ));
    }

    @Override
    public boolean containsAll(Collection<? extends MovecraftLocation> collection) {
        for (MovecraftLocation ml : collection) {
            if (this.contains(ml)) {
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    public @NotNull HitBox difference(HitBox other) {
        return new SetHitBox(this).difference(other);
    }

    @Override
    public @NotNull HitBox intersection(HitBox other) {
        return new SetHitBox(this).intersection(other);
    }

    @Override
    public @NotNull HitBox union(HitBox other) {
        return new SetHitBox(this).union(other);
    }

    @Override
    public @NotNull HitBox symmetricDifference(HitBox other) {
        return new SetHitBox(this).symmetricDifference(other);
    }

    @Override
    public int getMinYAt(int x, int z) {
        return minY;
    }
}
