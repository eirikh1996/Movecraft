/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft;

import com.earth2me.essentials.Essentials;
import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.palmergames.bukkit.towny.Towny;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.commands.*;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.ChunkManager;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.listener.BlockListener;
import net.countercraft.movecraft.listener.InteractListener;
import net.countercraft.movecraft.listener.PlayerListener;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.mapUpdater.MapUpdateManager;
import net.countercraft.movecraft.repair.RepairManager;
import net.countercraft.movecraft.sign.*;
import net.countercraft.movecraft.towny.TownyCompatManager;
import net.countercraft.movecraft.utils.LegacyUtils;
import net.countercraft.movecraft.utils.TownyUtils;
import net.countercraft.movecraft.utils.UpdateManager;
import net.countercraft.movecraft.worldguard.WorldGuardCompatManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Movecraft extends JavaPlugin {

    private static Movecraft instance;
    private static WorldGuardPlugin worldGuardPlugin;
    private static WorldEditPlugin worldEditPlugin;
    private static WGCustomFlagsPlugin wgCustomFlagsPlugin = null;
    private static Economy economy;
    private static Towny townyPlugin = null;
    private static Essentials essentialsPlugin = null;
    private Logger logger;
    private boolean shuttingDown;
    private boolean startup = true;
    private WorldHandler worldHandler;
    private MovecraftRepair movecraftRepair;


    private AsyncManager asyncManager;
    private RepairManager repairManager;

    public static synchronized Movecraft getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        saveLocaleFiles();
        String packageName = this.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        // Read in config
        if (!Settings.IsLegacy) {
            this.saveDefaultConfig();
        } else {
            saveLegacyConfig();
        }
        try {
            Class.forName("com.destroystokyo.paper.Title");
            Settings.IsPaper = true;
        }catch (Exception e){
            Settings.IsPaper=false;
        }
        if (!Settings.IsPaper){
            logger.warning("======== Movecraft === Use Paper =======");
            logger.warning("Your server version: " + getServer().getVersion());
            logger.warning("You may experience performance issues");
            logger.warning("with this server platform.");
            logger.warning("");
            logger.warning("It is recommended you use Paper");
            logger.warning("for better performance.");
            logger.warning("Download at papermc.io/downloads");
            logger.warning("======== Movecraft === Use Paper =======");
        }
        try {
            Class.forName("com.boydti.fawe.Fawe");
            if (!Settings.IsLegacy){
                logger.warning("======= Movecraft === FAWE detected ========");
                logger.warning("FAWE has been detected on the server");
                logger.warning("the 1.13+ version of FAWE has critical");
                logger.warning("bugs that will break the repair system");
                logger.warning("Therefore, use WorldEdit instead or disable");
                logger.warning("repair functionality and assault");
                logger.warning("======= Movecraft === FAWE detected ========");
            }
        } catch (ClassNotFoundException e) {

        }


        Settings.LOCALE = getConfig().getString("Locale");
        I18nSupport.init();
        Settings.RestrictSiBsToRegions = getConfig().getBoolean("RestrictSiBsToRegions", false);
        Settings.Debug = getConfig().getBoolean("Debug", false);
        Settings.DisableSpillProtection = getConfig().getBoolean("DisableSpillProtection", false);
        // if the PilotTool is specified in the config_legacy.yml file, use it
        Object pt = getConfig().get("PilotTool");
        final Material pilotTool;
        if (pt instanceof Integer){
            int toolID = (int) pt;
            if (!Settings.IsLegacy) {
                throw new IllegalArgumentException("Numerical block IDs are not supported by this version");
            }
            pilotTool = LegacyUtils.getMaterial(toolID);
        } else if (pt instanceof String){
            String str = (String) pt;
            str = str.toUpperCase();
            pilotTool = Material.getMaterial(str);
        } else {
            pilotTool = null;
        }
        Settings.PilotTool = pilotTool != null ? pilotTool : Material.STICK;
        logger.info(pilotTool != null ?I18nSupport.getInternationalisedString("Startup - Recognized Pilot Tool")
                + pilotTool.name().toLowerCase() :
                I18nSupport.getInternationalisedString("Startup - No Pilot Tool"));
        //Switch to interfaces
        try {
            final Class<?> clazz = Class.forName("net.countercraft.movecraft.compat." + version + ".IWorldHandler");
            // Check if we have a NMSHandler class at that location.
            if (WorldHandler.class.isAssignableFrom(clazz)) { // Make sure it actually implements NMS
                this.worldHandler = (WorldHandler) clazz.getConstructor().newInstance(); // Set our handler
            }
        } catch (final Exception e) {
            e.printStackTrace();
            this.getLogger().severe(I18nSupport.getInternationalisedString("Startup - Version Not Supported"));
            this.setEnabled(false);
            return;
        }
        this.getLogger().info(I18nSupport.getInternationalisedString("Startup - Loading Support") + " " + version);

        Settings.SinkCheckTicks = getConfig().getDouble("SinkCheckTicks", 100.0);
        Settings.ManOverboardTimeout = getConfig().getInt("ManOverboardTimeout", 30);
        Settings.ManOverboardDistSquared = Math.pow(getConfig().getDouble("ManOverboardDistance", 1000), 2);
        Settings.SilhouetteViewDistance = getConfig().getInt("SilhouetteViewDistance", 200);
        Settings.SilhouetteBlockCount = getConfig().getInt("SilhouetteBlockCount", 20);
        Settings.ProtectPilotedCrafts = getConfig().getBoolean("ProtectPilotedCrafts", false);
        Settings.AllowCrewSigns = getConfig().getBoolean("AllowCrewSigns", true);
        Settings.SetHomeToCrewSign = getConfig().getBoolean("SetHomeToCrewSign", true);
        Settings.MaxRemoteSigns = getConfig().getInt("MaxRemoteSigns", -1);
        Settings.CraftsUseNetherPortals = getConfig().getBoolean("CraftsUseNetherPortals", false);
        Settings.CheckForUpdates = getConfig().getBoolean("CheckForUpdates", true);
        Settings.RequireCreatePerm = getConfig().getBoolean("RequireCreatePerm", false);
        Settings.RequireNamePerm = getConfig().getBoolean("RequireNamePerm", true);
        Settings.FadeWrecksAfter = getConfig().getInt("FadeWrecksAfter", 0);
        Settings.RequireSneakingForDirectControl = getConfig().getBoolean("RequireSneakingForDirectControl", false);
        Settings.FadeTickCooldown = getConfig().getInt("FadeTickCooldown", 20);
        Settings.FadePercentageOfWreckPerCycle = getConfig().getDouble("FadePercentageOfWreckPerCycle", 10.0);
        if (getConfig().contains("ExtraFadeTimePerBlock")) {
            Map<String, Object> temp = getConfig().getConfigurationSection("ExtraFadeTimePerBlock").getValues(false);
            for (String str : temp.keySet()) {
                Material type;
                try {
                    type = LegacyUtils.getMaterial(Integer.parseInt(str));
                } catch (NumberFormatException e) {
                    type = Material.getMaterial(str);
                }
                Settings.ExtraFadeTimePerBlock.put(type, (Integer) temp.get(str));
            }
        }

        Settings.CollisionPrimer = getConfig().getInt("CollisionPrimer", 1000);
        Settings.ForbiddenRemoteSigns = new HashSet<>();
        List<String> disableShadowBlocks = getConfig().getStringList("DisableShadowBlocks");
        for (String typ : disableShadowBlocks){
            Material type;
            try {
                type = LegacyUtils.getMaterial(Integer.parseInt(typ));
            } catch (NumberFormatException e){
                type = Material.getMaterial(typ);
            } catch (NoSuchMethodError e){
                logger.warning(I18nSupport.getInternationalisedString("Startup - Numerical ID found") + " DisableShadowBlocks: " + typ);
                continue;
            }
            if (type != null) {
                Settings.DisableShadowBlocks.add(type);
            }
        }
        for(String s : getConfig().getStringList("ForbiddenRemoteSigns")) {
            Settings.ForbiddenRemoteSigns.add(s.toLowerCase());
        }

        if (!Settings.CompatibilityMode) {
            for (Material typ : Settings.DisableShadowBlocks) {
                worldHandler.disableShadow(typ);
            }
        }
        //load up WorldGuard if it's present
        if (worldGuardPlugin == null) {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - WG Not Found"));
            Settings.RestrictSiBsToRegions = false;
        } else {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - WG Found"));
            Settings.WorldGuardBlockMoveOnBuildPerm = getConfig().getBoolean("WorldGuardBlockMoveOnBuildPerm", false);
            Settings.WorldGuardBlockSinkOnPVPPerm = getConfig().getBoolean("WorldGuardBlockSinkOnPVPPerm", false);
            logger.log(Level.INFO, "Settings: WorldGuardBlockMoveOnBuildPerm - {0}, WorldGuardBlockSinkOnPVPPerm - {1}", new Object[]{Settings.WorldGuardBlockMoveOnBuildPerm, Settings.WorldGuardBlockSinkOnPVPPerm});
            getServer().getPluginManager().registerEvents(new WorldGuardCompatManager(), this);
        }


        //load up WorldEdit if it's present

        Plugin wEPlugin = getServer().getPluginManager().getPlugin("WorldEdit");

        if (wEPlugin == null || !(wEPlugin instanceof WorldEditPlugin)) {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - WE Not Found"));
        } else {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - WE Found"));
            Settings.RepairTicksPerBlock = getConfig().getInt("RepairTicksPerBlock", 0);
            String weVersion;
            //Now decide which WE compat should be used
            if (!Settings.IsLegacy){
                    weVersion = "we7";

            } else {
                weVersion = "we6";
            }
            //Test if a compatible MovecraftRepair is present
            try {
                final Class<?> clazz = Class.forName("net.countercraft.movecraft.compat." + weVersion + ".IMovecraftRepair");
                //Check if we have a Repair class at that location
                if (MovecraftRepair.class.isAssignableFrom(clazz)){
                    this.movecraftRepair = (MovecraftRepair) clazz.getConstructor(Plugin.class).newInstance(this);
                }
            } catch (final Exception e){
                e.printStackTrace();
                this.getLogger().severe("Could not find a compatible repair class. Disabling repair and assault functions");
                Settings.RepairTicksPerBlock = 0;
            }
            Settings.RepairMaxPercent = getConfig().getDouble("RepairMaxPercent", 50);
            worldEditPlugin = (WorldEditPlugin) wEPlugin;
        }
        Plugin tempTownyPlugin = getServer().getPluginManager().getPlugin("Towny");
        if (tempTownyPlugin != null && tempTownyPlugin instanceof Towny) {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Towny Found"));
            townyPlugin = (Towny) tempTownyPlugin;
            TownyUtils.initTownyConfig();
            Settings.TownyBlockMoveOnSwitchPerm = getConfig().getBoolean("TownyBlockMoveOnSwitchPerm", false);
            Settings.TownyBlockSinkOnNoPVP = getConfig().getBoolean("TownyBlockSinkOnNoPVP", false);
            getServer().getPluginManager().registerEvents(new TownyCompatManager(), this);
            logger.log(Level.INFO, "Settings: TownyBlockMoveOnSwitchPerm - {0}", Settings.TownyBlockMoveOnSwitchPerm);
            logger.log(Level.INFO, "Settings: TownyBlockSinkOnNoPVP - {0}", Settings.TownyBlockSinkOnNoPVP);

        } else {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Towny Not Found"));
        }
        //Essentials
        Plugin tempEssentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (tempEssentialsPlugin != null) {
            if (tempEssentialsPlugin.getDescription().getName().equalsIgnoreCase("essentials")) {
                if (tempEssentialsPlugin.getClass().getName().equals("com.earth2me.essentials.Essentials")) {
                    if (tempEssentialsPlugin instanceof Essentials) {
                        essentialsPlugin = (Essentials) tempEssentialsPlugin;
                        logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Essentials Found"));
                    }
                }
            }
        }
        if (essentialsPlugin == null) {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Essentials Not Found"));
        }
        // and now Vault
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                Settings.RepairMoneyPerBlock = getConfig().getDouble("RepairMoneyPerBlock", 0.0);
                logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Found"));
            } else {
                logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Not Found"));
                economy = null;
            }
        } else {
            logger.log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Not Found"));
            economy = null;
        }
        
        if (shuttingDown && Settings.IGNORE_RESET) {
            logger.log(
                    Level.SEVERE,
                    I18nSupport
                            .getInternationalisedString("Startup - Error - Reload error"));
            logger.log(
                    Level.INFO,
                    I18nSupport
                            .getInternationalisedString("Startup - Error - Disable warning for reload"));
            getPluginLoader().disablePlugin(this);
        } else {

            // Startup procedure
            asyncManager = new AsyncManager();
            asyncManager.runTaskTimer(this, 0, 1);
            MapUpdateManager.getInstance().runTaskTimer(this, 0, 1);
            CraftManager.initialize();

            getServer().getPluginManager().registerEvents(new InteractListener(), this);
            if (worldEditPlugin != null) {
                if (movecraftRepair != null){
                    repairManager = new RepairManager();
                    if (startup)
                        repairManager.runTaskTimerAsynchronously(this, 0, 1);
                    repairManager.convertOldCraftRepairStates();
                }
            }
            this.getCommand("movecraft").setExecutor(new MovecraftCommand());
            this.getCommand("release").setExecutor(new ReleaseCommand());
            this.getCommand("pilot").setExecutor(new PilotCommand());
            this.getCommand("rotate").setExecutor(new RotateCommand());
            this.getCommand("cruise").setExecutor(new CruiseCommand());
            this.getCommand("craftreport").setExecutor(new CraftReportCommand());
            this.getCommand("manoverboard").setExecutor(new ManOverboardCommand());
            this.getCommand("contacts").setExecutor(new ContactsCommand());
            this.getCommand("scuttle").setExecutor(new ScuttleCommand());

            getServer().getPluginManager().registerEvents(new BlockListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(), this);
            getServer().getPluginManager().registerEvents(new ChunkManager(), this);
            getServer().getPluginManager().registerEvents(new AscendSign(), this);
            getServer().getPluginManager().registerEvents(new ContactsSign(), this);
            getServer().getPluginManager().registerEvents(new CraftSign(), this);
            getServer().getPluginManager().registerEvents(new CrewSign(), this);
            getServer().getPluginManager().registerEvents(new CruiseSign(), this);
            getServer().getPluginManager().registerEvents(new DescendSign(), this);
            getServer().getPluginManager().registerEvents(new HelmSign(), this);
            getServer().getPluginManager().registerEvents(new MoveSign(), this);
            getServer().getPluginManager().registerEvents(new NameSign(), this);
            getServer().getPluginManager().registerEvents(new PilotSign(), this);
            getServer().getPluginManager().registerEvents(new RelativeMoveSign(), this);
            getServer().getPluginManager().registerEvents(new ReleaseSign(), this);
            getServer().getPluginManager().registerEvents(new RemoteSign(), this);
            getServer().getPluginManager().registerEvents(new RepairSign(), this);
            getServer().getPluginManager().registerEvents(new SpeedSign(), this);
            getServer().getPluginManager().registerEvents(new StatusSign(), this);
            getServer().getPluginManager().registerEvents(new SubcraftRotateSign(), this);
            getServer().getPluginManager().registerEvents(new TeleportSign(), this);

            //Start the update manager
            UpdateManager.getInstance();
            logger.log(Level.INFO, String.format(
                    I18nSupport.getInternationalisedString("Startup - Enabled message"),
                    getDescription().getVersion()));
        }
        startup = false;
    }

    @Override
    public void onLoad() {
        String packageName = this.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        String[] parts = version.split("_");
        int versionNumber = Integer.valueOf(parts[1]);
        //Check if the server is 1.12 and lower or 1.13 and higher
        Settings.IsPre1_9 = versionNumber < 9;
        Settings.IsLegacy = versionNumber <= 12;
        Settings.is1_14 = versionNumber >= 14;
        instance = this;
        logger = getLogger();
        //load up WorldGuard if it's present
        Plugin wGPlugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wGPlugin instanceof WorldGuardPlugin) {
            worldGuardPlugin = (WorldGuardPlugin) wGPlugin;
            if (startup)
                WorldGuardCompatManager.registerFlags();
        }
    }

    private void saveLegacyConfig(){
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists())
            return;
        InputStream resource = getResource("config_legacy.yml");
        Reader reader = new InputStreamReader(resource);
        FileConfiguration config = YamlConfiguration.loadConfiguration(reader);
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void saveLocaleFiles(){
        final String[] LOCALES = {"en", "cz", "nl"};
        for (String locale : LOCALES){
            saveResource("localisation/movecraftlang_" + locale + ".properties", false);
        }
    }


    public WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    public Economy getEconomy() {
        return economy;
    }

    public WGCustomFlagsPlugin getWGCustomFlagsPlugin() {
        return wgCustomFlagsPlugin;
    }

    public Towny getTownyPlugin() {
        return townyPlugin;
    }

    public Essentials getEssentialsPlugin() {
        return essentialsPlugin;
    }

    public WorldHandler getWorldHandler(){
        return worldHandler;
    }

    public AsyncManager getAsyncManager(){return asyncManager;}


    public MovecraftRepair getMovecraftRepair() {return movecraftRepair;}

    public RepairManager getRepairManager() {
        return repairManager;
    }
}

