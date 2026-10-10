package name.minecad.wand;

import net.minecraft.world.item.Item;

public class Wand {
    private Item itemForWand;
    int itemsScanned = 0;

    public Wand(Item itemForWand) {
        this.itemForWand = itemForWand;
    }

    public void changeWand(Item newItem) {
        this.itemForWand = newItem;
        System.out.println("Wand item changed to: " + newItem);
    }

    public Item getItemForWand() {
        return this.itemForWand;
    }
}
