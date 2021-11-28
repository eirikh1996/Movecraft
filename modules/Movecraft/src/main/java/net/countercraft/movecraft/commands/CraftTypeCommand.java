package net.countercraft.movecraft.commands;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.TopicPaginator;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.ChatPaginator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

public class CraftTypeCommand implements TabExecutor {

    private static final Field[] craftTypeFields;
    static {
        craftTypeFields = CraftType.class.getDeclaredFields();
        for(Field field : craftTypeFields){
            field.setAccessible(true);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        CraftType type;
        int page;
        if(args.length == 0 || (args.length == 1 && MathUtils.parseInt(args[0]).isPresent())){
            Optional<CraftType> typeQuery = tryGetCraftFromPlayer(commandSender);
            if(!typeQuery.isPresent()){
                commandSender.sendMessage("You must supply a craft type!");
                return true;
            }
            type = typeQuery.get();
            page = args.length == 0 ? 1 : MathUtils.parseInt(args[0]).getAsInt();
        } else {
            if(args.length > 1){
                OptionalInt pageQuery = MathUtils.parseInt(args[1]);
                if(!pageQuery.isPresent()){
                    commandSender.sendMessage("Argument " + args[1] + " must be a page number");
                    return true;
                }
                page = pageQuery.getAsInt();
            } else {
                page = 1;
            }
            if(args[0].equalsIgnoreCase("list")){
                sendTypeListPage(page, commandSender);
                return true;
            }
            type = CraftManager.getInstance().getCraftTypeFromString(args[0]);
        }
        if(!commandSender.hasPermission("movecraft." + type.getCraftName() + ".pilot")){
            commandSender.sendMessage("You don't have permission for that craft type!");
            return true;
        }
        sendTypePage(type, page, commandSender);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if(strings.length !=1 || !commandSender.hasPermission("movecraft.commands") || !commandSender.hasPermission("movecraft.commands.crafttype"))
            return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        for(CraftType type : CraftManager.getInstance().getCraftTypes())
            if(commandSender.hasPermission("movecraft." + type.getCraftName() + ".pilot"))
                completions.add(type.getCraftName());
        completions.add("list");
        List<String> returnValues = new ArrayList<>();
        for(String completion : completions)
            if(completion.toLowerCase().startsWith(strings[strings.length-1].toLowerCase()))
                returnValues.add(completion);
        return returnValues;
    }

    private void sendTypePage(@NotNull CraftType type, int page, @NotNull  CommandSender commandSender){
        TopicPaginator paginator = new TopicPaginator("Type Info", "/crafttype " + type.getCraftName() + " {PAGE}");
        for(Field field : craftTypeFields){
            if(field.getName().equals("data")){ // don't include the backing data object
                continue;
            }
            Object value;
            try {
                value = field.get(type);
            } catch (IllegalAccessException e) {
                paginator.addLine(field.getName() + ": failed to access");
                continue;
            }
            String repr = field.getName() + ": " + value;
            if(repr.length() > ChatPaginator.GUARANTEED_NO_WRAP_CHAT_PAGE_WIDTH){
                paginator.addLine(field.getName() + ": too long");
            } else {
                paginator.addLine(field.getName() + ": " + value);
            }
        }
        if(!paginator.isInBounds(page)){
            commandSender.sendMessage(String.format("Page %d is out of bounds.", page));
            return;
        }
        for(TextComponent line : paginator.getPage(page))
            commandSender.spigot().sendMessage(line);
    }

    private void sendTypeListPage(int page, @NotNull  CommandSender commandSender){
        TopicPaginator paginator = new TopicPaginator("Type Info", "/crafttype list {PAGE}");
        for(CraftType entry : CraftManager.getInstance().getCraftTypes()){
            paginator.addLine(entry.getCraftName());
        }
        if(!paginator.isInBounds(page)){
            commandSender.sendMessage(String.format("Page %d is out of bounds.", page));
            return;
        }
        for(TextComponent line : paginator.getPage(page))
            commandSender.spigot().sendMessage(line);
    }

    @NotNull
    private Optional<CraftType> tryGetCraftFromPlayer(CommandSender commandSender){
        if (!(commandSender instanceof Player)) {
            return Optional.empty();
        }
        Craft c = CraftManager.getInstance().getCraftByPlayer((Player) commandSender);
        if(c == null){
            return Optional.empty();
        }
        return Optional.of(c.getType());
    }
}
