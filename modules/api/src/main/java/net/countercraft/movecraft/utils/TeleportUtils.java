package net.countercraft.movecraft.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * Code taken with permission from MicleBrick
 * https://www.spigotmc.org/threads/teleport-player-smoothly.317416/
 */
public class TeleportUtils {
    private static Set<Object> teleportFlags;

    private static Constructor packetConstructor;
    private static Constructor vehiclePacketConstructor;
    private static Constructor vec3D;

    private static Method position;
    private static Method closeInventory;
    private static Method sendMethod;
    private static Method getBukkitEntity;
    private static Method spawnIn;

    private static Field connectionField;
    private static Field justTeleportedField;
    private static Field teleportPosField;
    private static Field lastPosXField;
    private static Field lastPosYField;
    private static Field lastPosZField;
    private static Field teleportAwaitField;
    private static Field AField;
    private static Field eField;
    private static Field yaw;
    private static Field pitch;
    private static Field activeContainer;
    private static Field defaultContainer;

    private static Field t;

    static {
        Class<?> packet = v1_17() ? getNmnClass("protocol.Packet") : getNmsClass("Packet");
        Class<?> entity = v1_17() ? getNmwClass("entity.Entity") : getNmsClass("Entity");
        Class<?> entityPlayer = getNmsClass((v1_17() ? "level." : "") + "EntityPlayer");
        Class<?> entityHuman = v1_17() ? getNmwClass("entity.player.EntityHuman") : getNmsClass("EntityHuman");
        Class<?> connectionClass = getNmsClass((v1_17() ? "network." : "") + "PlayerConnection");
        Class<?> packetClass = v1_17() ? getNmnClass("protocol.game.PacketPlayOutPosition") : getNmsClass("PacketPlayOutPosition");
        Class<?> vehiclePacket =  v1_17() ? getNmnClass("protocol.game.PacketPlayOutVehicleMove") : getNmsClass("PacketPlayOutVehicleMove");
        Class<?> vecClass = v1_17() ? getNmwClass("phys.Vec3D") : getNmsClass("Vec3D");
        Class<?> worldClass = v1_17() ? getNmwClass("level.World") : getNmsClass("World");

        try {
            sendMethod = connectionClass.getMethod(v1_18() ? "a" : "sendPacket", packet);

            position = entity.getDeclaredMethod(v1_18() ? "a" : "setLocation", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE);
            closeInventory = entityPlayer.getDeclaredMethod(v1_18() ? "q" : "closeInventory");
            getBukkitEntity = entity.getDeclaredMethod("getBukkitEntity");
            if (!v1_17())
                spawnIn = entity.getDeclaredMethod("spawnIn", worldClass);
            else
                t = entity.getDeclaredField("t");
            yaw = getField(entity, v1_17() ? (v1_18() ? "aA" : "ay") : "yaw");
            pitch = getField(entity,  v1_17() ? (v1_18() ? "aB" : "az") : "pitch");
            connectionField = getField(entityPlayer, v1_17() ? "b" : "playerConnection");
            activeContainer = getField(entityHuman, v1_17() ? "bU" : "activeContainer");
            defaultContainer = getField(entityHuman, v1_17() ? "bV" : "defaultContainer");

            packetConstructor = v1_17() ? packetClass.getConstructor(Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class, Integer.TYPE, Boolean.TYPE)
            : packetClass.getConstructor(Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class, Integer.TYPE);
            vec3D = vecClass.getConstructor(Double.TYPE, Double.TYPE, Double.TYPE);

            vehiclePacketConstructor = vehiclePacket.getConstructor(entity);

            Object[] enumObjects = v1_17() ? getNmnClass("protocol.game.PacketPlayOutPosition$EnumPlayerTeleportFlags").getEnumConstants() :  getNmsClass("PacketPlayOutPosition$EnumPlayerTeleportFlags").getEnumConstants();
            teleportFlags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(enumObjects[4], enumObjects[3])));

            justTeleportedField = getField(connectionClass, "justTeleported");
            teleportPosField = getField(connectionClass, v1_17() ? (v1_19() ? (v1_19_1() ? "C" : "B") : "y") : "teleportPos");
            lastPosXField = getField(connectionClass, "lastPosX");
            lastPosYField = getField(connectionClass, "lastPosY");
            lastPosZField = getField(connectionClass, "lastPosZ");
            teleportAwaitField = getField(connectionClass, v1_17() ? (v1_19() ? (v1_19_1() ? "D" : "C") : "z") : "teleportAwait");
            AField = getField(connectionClass, v1_19() ? (v1_19_1() ? "E" : "D") : "A");
            eField = getField(connectionClass, v1_17() ? (v1_19_1() ? "i" : "h") : "e");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static void teleportEntity(Entity entity, Location location) {
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        Entity vehicle = entity.getVehicle();
        Object handle = getHandle(vehicle == null ? entity : vehicle);
        try {
            if (location.getWorld() != entity.getWorld()) {
                Method wHandle = location.getWorld().getClass().getDeclaredMethod("getHandle");
                if (!v1_17())
                    spawnIn.invoke(handle, wHandle.invoke(location.getWorld()));
            }
            position.invoke(handle, x,y,z, location.getYaw(), location.getPitch());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void teleport(Player player, Location location, float yawChange) {
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        Entity vehicle = player.getVehicle();
        Object handle = getHandle(vehicle == null ? player : vehicle);
        Object pHandle = getHandle(player);

        try {
            if (location.getWorld() != player.getWorld()) {
                Method wHandle = location.getWorld().getClass().getDeclaredMethod("getHandle");
                spawnIn.invoke(handle, wHandle.invoke(location.getWorld()));
            }
            position.invoke(handle, x,y,z, yaw.get(handle), pitch.get(handle));
            yaw.set(handle, yaw.getFloat(handle) + yawChange);
            Object connection = connectionField.get(pHandle);
            justTeleportedField.set(connection, true);
            teleportPosField.set(connection, vec3D.newInstance(x, y, z));
            lastPosXField.set(connection, x);
            lastPosYField.set(connection, y);
            lastPosZField.set(connection, z);
            int teleportAwait = teleportAwaitField.getInt(connection) + 1;
            if(teleportAwait == 2147483647) teleportAwait = 0;
            teleportAwaitField.set(connection, teleportAwait);
            AField.set(connection, eField.get(connection));

            Object packet = v1_17() ? packetConstructor.newInstance(x, y, z, yawChange, 0, teleportFlags, teleportAwait, true) : packetConstructor.newInstance(x, y, z, yawChange, 0, teleportFlags, teleportAwait);
            sendPacket(packet, player);
            if (vehicle == null)
                return;
            Object vehiclePacket = vehiclePacketConstructor.newInstance(handle);
            sendPacket(vehiclePacket, player);
            List<Entity> passengers;
            try {
                passengers = (List<Entity>) Entity.class.getDeclaredMethod("getPassengers").invoke(vehicle);
            } catch (Exception e) {
                return;
            }
            for (Entity pass : passengers) {
                if (pass.getType() != EntityType.PLAYER || pass.equals(player)) {
                    continue;
                }
                Player p = (Player) pass;
                sendPacket(packet, p);
                sendPacket(vehiclePacket, p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendPacket(Object packet, Player p) {
        try {
            Object handle = getHandle(p);
            Object pConnection = connectionField.get(handle);
            sendMethod.invoke(pConnection, packet);
        } catch (Exception var9) {
            var9.printStackTrace();
        }
    }

    private static Object getHandle(Entity entity) {
        try {
            Method entity_getHandle = entity.getClass().getMethod("getHandle");
            return entity_getHandle.invoke(entity);
        } catch (Exception var2) {
            var2.printStackTrace();
            return null;
        }
    }
    private static boolean v1_17() {
        return Integer.parseInt(getVersion().split("_")[1]) >= 17;
    }

    private static boolean v1_18() {
        return Integer.parseInt(getVersion().split("_")[1]) >= 18;
    }

    private static boolean v1_19()  {
        return Integer.parseInt(getVersion().split("_")[1]) >= 19;
    }

    private static boolean v1_19_1() {
        final String version = Bukkit.getVersion().replace("(MC: ", "").replace(")" , "");
        int versionNumber = Integer.parseInt(version.split("\\.")[2]);
        return v1_19() && versionNumber >= 1;
    }

    private static Class<?> getNmsClass(String name) {
        Class clazz = null;

        try {
            clazz = Class.forName("net.minecraft.server." + (v1_17() ? "" :  getVersion() + ".") + name);
        } catch (ClassNotFoundException var3) {
            var3.printStackTrace();
        }

        return clazz;
    }

    private static Class<?> getNmnClass(String name) {
        Class clazz = null;

        try {
            clazz = Class.forName("net.minecraft.network." + name);
        } catch (ClassNotFoundException var3) {
            var3.printStackTrace();
        }

        return clazz;
    }

    private static Class<?> getNmwClass(String name) {
        Class clazz = null;

        try {
            clazz = Class.forName("net.minecraft.world." + name);
        } catch (ClassNotFoundException var3) {
            var3.printStackTrace();
        }

        return clazz;
    }

    private static String getVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().substring(23);
    }
}
