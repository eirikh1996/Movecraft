package net.countercraft.movecraft.commands;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.TopicPaginator;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

public class CraftInfoCommand implements CommandExecutor {
    private static final List<Function<Craft, ? extends Iterable<String>>> providers = new ArrayList<>();
    static {
        registerMultiProvider(CraftInfoCommand::allowedBlockProvider);
        registerProvider((craft -> "Craft size: " + craft.getHitBox().size()));
        registerProvider((craft -> "Craft midpoint: " + craft.getHitBox().getMidPoint()));
        registerProvider((craft -> "Craft min x: " + craft.getHitBox().getMinX()));
        registerProvider((craft -> "Craft min y: " + craft.getHitBox().getMinY()));
        registerProvider((craft -> "Craft min z: " + craft.getHitBox().getMinZ()));
        registerProvider((craft -> "Craft max x: " + craft.getHitBox().getMaxX()));
        registerProvider((craft -> "Craft max y: " + craft.getHitBox().getMaxY()));
        registerProvider((craft -> "Craft max z: " + craft.getHitBox().getMaxZ()));
        registerProvider((craft -> "Craft world: " + craft.getWorld().getName()));
        registerProvider((craft -> "Craft type: " + craft.getType().getCraftName()));
        registerProvider((craft -> "Craft name: " + craft.getName()));
        registerProvider((craft -> "Is cruising: " + craft.getCruising()));
        registerProvider((craft -> "Cruise direction: " + craft.getCruiseDirection()));
        registerProvider((craft -> "Craft speed: " + craft.getSpeed()));
        registerProvider((craft -> "Mean cruise time: " + craft.getMeanCruiseTime()));
        registerProvider((craft -> "Is disabled: " + craft.getDisabled()));
        registerProvider((craft -> "Current gear: " + craft.getCurrentGear()));
    }

    private static @NotNull List<String> allowedBlockProvider(@NotNull Craft craft){
        return new ArrayList<>();
    }

    public static void registerMultiProvider(@NotNull Function<Craft, ? extends Iterable<String>> provider){
        providers.add(provider);
    }

    public static void registerProvider(@NotNull Function< Craft,  String> provider){
        providers.add(provider.andThen((String t) -> new ArrayList<>()));
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(args.length == 0){
            if (!(sender instanceof Player)){
                sender.sendMessage("Supply a parameter.");
                return true;
            }
            Craft craft = CraftManager.getInstance().getCraftByPlayer(((Player) sender));
            if(craft == null){
                sender.sendMessage("You must be piloting a craft.");
                return true;
            }
            craftInfo(sender, craft, 1);
            return true;
        }
        OptionalInt pageQuery;
        if (sender instanceof  Player && (pageQuery = MathUtils.parseInt(args[0])).isPresent()){
            Craft craft = CraftManager.getInstance().getCraftByPlayer(((Player) sender));
            if(craft == null){
                sender.sendMessage("You must be piloting a craft.");
                return true;
            }
            craftInfo(sender, craft, pageQuery.getAsInt());
            return true;
        }
        final Craft craft = CraftManager.getInstance().getCraftByPlayerName(args[0]);
        if (craft == null) {
            sender.sendMessage("No player found");
            return true;
        }
        if(args.length > 1){
            pageQuery = MathUtils.parseInt(args[1]);
            if(!pageQuery.isPresent()){
                sender.sendMessage("Parameter " + args[1] + " must be a page number.");
                return true;
            }
        } else {
            pageQuery = OptionalInt.of(1);
        }
        craftInfo(sender, craft, pageQuery.getAsInt());
        return true;
    }

    public void craftInfo(@NotNull CommandSender commandSender, @NotNull Craft craft, int page) {
        craftInfo(commandSender, craft, page, "");
    }

    public void craftInfo(@NotNull CommandSender commandSender, @NotNull Craft craft, int page, String playerName) {
        TopicPaginator paginator = new TopicPaginator("Craft Info", "/craftinfo " + playerName + " {PAGE}");
        for(Function<Craft, ? extends Iterable<String>> provider : providers){
            for(String line : provider.apply(craft)){
                paginator.addLine(line);
            }
        }
        if(!paginator.isInBounds(page)){
            commandSender.sendMessage(String.format("Page %d is out of bounds.", page));
            return;
        }
        for(TextComponent line : paginator.getPage(page))
            commandSender.spigot().sendMessage(line);
    }

}
