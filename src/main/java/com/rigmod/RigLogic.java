package com.rigmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RigLogic {
    private static final Pattern NAME = Pattern.compile("^\\s*([1-9])\\s*([HhVv])\\s*$");

    /** side is 'H' (host) or 'V' (viewer). */
    public record Tag(char side, int number) {}

    private RigLogic() {}

    /** The item model id of a stack (the item id unless a resource pack/command changed it). */
    public static Identifier idOf(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null ? model : Registries.ITEM.getId(stack.getItem());
    }

    /** Parses items NAMED "N H" / "N V". Returns null for anything else. */
    public static Tag nameTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!stack.contains(DataComponentTypes.CUSTOM_NAME)) return null;
        Matcher m = NAME.matcher(stack.getName().getString());
        if (!m.matches()) return null;
        return new Tag(Character.toUpperCase(m.group(2).charAt(0)), m.group(1).charAt(0) - '0');
    }

    /**
     * Which number/side a stack is. First by the item ids set in the menu
     * (e.g. every zombie head = host 1), then by its name "N H" / "N V".
     */
    public static Tag tag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = idOf(stack);
        char[] sides = {'H', 'V'};
        for (char side : sides) {
            for (int n = 1; n <= 9; n++) {
                Identifier want = RigConfig.id(side, n);
                if (want != null && want.equals(id)) return new Tag(side, n);
            }
        }
        return nameTag(stack);
    }

    /** A fresh stack of the item configured in the menu for this number, or null if blank. */
    public static ItemStack fromConfig(char side, int number) {
        Identifier id = RigConfig.id(side, number);
        if (id == null) return null;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            // Not a normal item id: treat it as a resource-pack item model.
            ItemStack out = new ItemStack(Items.PAPER);
            out.set(DataComponentTypes.ITEM_MODEL, id);
            return out;
        }
        return new ItemStack(item);
    }

    /**
     * RENDER-ONLY. Any recognised item that is NOT part of the dropper slot display
     * (on the ground, in hand, on the cursor, in the inventory...) is drawn as the number that
     * "dropped" for its side (picked when the rig was turned on). The real item is never touched.
     */
    public static ItemStack disguise(ItemStack stack) {
        if (!RigState.enabled || stack == null || stack.isEmpty()) return stack;
        if (RigView.isNormal(stack)) return stack;
        Tag t = tag(stack);
        if (t == null) return stack;
        int shown = RigState.pick(t.side());
        if (shown == t.number()) return stack;
        return lookFor(t.side(), shown, stack);
    }

    private static ItemStack lookFor(char side, int number, ItemStack real) {
        ItemStack out;
        ItemStack snap = RigView.snapshot(side, number);
        if (snap != null) {
            out = snap.copy();
        } else {
            out = fromConfig(side, number);
            if (out == null) out = new ItemStack(Items.PAPER);
            if (nameTag(real) != null) {
                out.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(number + " " + side).setStyle(real.getName().getStyle()));
            }
        }
        out.setCount(real.getCount());
        return out;
    }
}
