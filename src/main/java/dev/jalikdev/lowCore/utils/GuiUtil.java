package dev.jalikdev.lowCore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GuiUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final ItemStack FILLER = item(Material.GRAY_STAINED_GLASS_PANE, " ");

    private GuiUtil() {
    }

    public static Component title(String value) {
        return component(value);
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(name));
        List<Component> components = new ArrayList<>(lore.length);
        for (String line : lore) components.add(component(line));
        meta.lore(components);
        item.setItemMeta(meta);
        return item;
    }

    public static void fill(Inventory inventory) {
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        Arrays.fill(contents, FILLER);
        inventory.setContents(contents);
    }

    private static Component component(String value) {
        return LEGACY.deserialize(value.replace('§', '&')).decoration(TextDecoration.ITALIC, false);
    }
}
