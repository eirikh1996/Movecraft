package net.countercraft.movecraft.utils;

import net.countercraft.movecraft.localisation.I18nSupport;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bukkit.util.ChatPaginator.CLOSED_CHAT_PAGE_HEIGHT;

public class TopicPaginator {
    private final String title;
    private final List<TextComponent> lines = new ArrayList<>();
    private final String command;

    public TopicPaginator(String title, String command){
        this.title = title;
        this.command = command;
    }

    public boolean addLine(String line){
        return addLine(new TextComponent(line));
    }

    public boolean addLine(TextComponent line){
        boolean result = lines.add(line);
        lines.sort(Comparator.comparing(TextComponent::getText));
        return result;
    }

    /**
     * Page numbers begin at 1
     * @param pageNumber
     * @return An array of lines to send as a page
     */
    public TextComponent[] getPage(int pageNumber){
        if(!isInBounds(pageNumber))
            throw new IndexOutOfBoundsException(I18nSupport.getInternationalisedString("Paginator - Page Number")+ " " + pageNumber + " "+ I18nSupport.getInternationalisedString("Paginator - Exceeds Bounds") + "<1, " + getPageCount() + ">");
        TextComponent[] tempLines = new TextComponent[pageNumber == getPageCount() ? (lines.size()%CLOSED_CHAT_PAGE_HEIGHT) + 1 : CLOSED_CHAT_PAGE_HEIGHT];
        tempLines[0] = new TextComponent("§e§l--- §r§6" + title +" §e§l-- §r§6page §c" + pageNumber + "§e/§c" + getPageCount() + " §e§l---");
        for(int i = 0; i< tempLines.length-2; i++)
            tempLines[i+1] = new TextComponent(lines.get(((CLOSED_CHAT_PAGE_HEIGHT-1) * (pageNumber-1)) + i));
        BaseComponent prevPage = new TextComponent(pageNumber == 1 ? "§4---" : "§2<<<");
        BaseComponent nextPage = new TextComponent(pageNumber == getPageCount() ? "§4---" : "§2>>>");
        if (pageNumber > 1) {
            prevPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command.replace("{PAGE}", String.valueOf(pageNumber - 1))));
        }
        if (pageNumber < getPageCount()) {
            nextPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command.replace("{PAGE}", String.valueOf(pageNumber + 1))));
        }
        TextComponent arrows = new TextComponent();
        arrows.addExtra(prevPage);
        arrows.addExtra("|");
        arrows.addExtra(nextPage);
        tempLines[tempLines.length - 1] = arrows;
        return tempLines;
    }

    public int getPageCount(){
        return (int)Math.ceil(((double)lines.size())/(CLOSED_CHAT_PAGE_HEIGHT-1));
    }

    public boolean isInBounds(int pageNumber){
        return pageNumber > 0 && pageNumber <= getPageCount();
    }

    public boolean isEmpty(){
        return lines.isEmpty();
    }
}