package name.minecad.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public final class InventoryTabManager {
    private enum InventoryTabView {
        VANILLA,
        MINE_CAD
    }

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;

    private int additionalTabsOffsetX = 200;
    private int additionalTabsOffsetY = 0;
    private InventoryTabView activeInventoryTab = InventoryTabView.VANILLA;
    private final InventoryTab mineCadTab;
    private Screen currentScreen;

    public InventoryTabManager(InventoryTab mineCadTab) {
        this.mineCadTab = mineCadTab;
    }

    public void setAdditionalTabsOffset(int xOffset, int yOffset) {
        additionalTabsOffsetX = xOffset;
        additionalTabsOffsetY = yOffset;
    }

    public void registerForScreen(Minecraft client, Screen screen, int width, int height) {
        boolean inventoryScreen = screen instanceof InventoryScreen;
        boolean creativeInventoryScreen = screen instanceof CreativeModeInventoryScreen;

        if (!inventoryScreen && !creativeInventoryScreen) {
            return;
        }

        currentScreen = screen;
        syncOverlayState();
        ScreenEvents.remove(screen).register(InventoryOverlayState::clear);

        int backgroundWidth = creativeInventoryScreen ? 195 : 176;
        int backgroundHeight = creativeInventoryScreen ? 136 : 166;
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        final InventoryTab[] tabs = new InventoryTab[2];

        // MineCad tab stays inside the same inventory screen.
        tabs[1] = mineCadTab;
        tabs[1].setIcon(new ItemStack(Items.BOOK));
        tabs[1].setX(x + additionalTabsOffsetX);
        tabs[1].setY((y - TAB_HEIGHT) + additionalTabsOffsetY);
        tabs[1].setSelected(activeInventoryTab == InventoryTabView.MINE_CAD);
        tabs[1].setAction(() -> setActiveInventoryTab(InventoryTabView.MINE_CAD, tabs[0], tabs[1]));
        Screens.getWidgets(screen).add(tabs[1]);

    InventoryTabWindow window = tabs[1].getWindow();
    if (window != null) {
      window.setSize(backgroundWidth, backgroundHeight);
      ScreenMouseEvents.allowMouseClick(screen).register((currentScreen, event) ->
          !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), x, y, backgroundWidth, backgroundHeight)
      );
      ScreenMouseEvents.allowMouseRelease(screen).register((currentScreen, event) ->
          !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), x, y, backgroundWidth, backgroundHeight)
      );
      ScreenMouseEvents.allowMouseDrag(screen).register((currentScreen, event, horizontalAmount, verticalAmount) ->
          !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), x, y, backgroundWidth, backgroundHeight)
      );
      ScreenMouseEvents.allowMouseScroll(screen).register((currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount) ->
          !isWindowInteractionBlocked(tabs[1], mouseX, mouseY, x, y, backgroundWidth, backgroundHeight)
      );
      ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, tickProgress) -> {
        if (activeInventoryTab == InventoryTabView.MINE_CAD && tabs[1].isSelectedTab()) {
          window.drawWindow(graphics, currentScreen.getFont(), x, y);
        }
      });
    }

        if (creativeInventoryScreen) {
            ScreenMouseEvents.afterMouseClick(screen).register((clickedScreen, event, consumed) -> {
                if (activeInventoryTab != InventoryTabView.MINE_CAD || tabs[1] == null) {
                    return consumed;
                }

                double mouseX = event.x();
                double mouseY = event.y();
                boolean clickedMineCadTab = isInside(mouseX, mouseY, tabs[1].getX(), tabs[1].getY(), TAB_WIDTH, TAB_HEIGHT);
                if (clickedMineCadTab) {
                    return consumed;
                }

                boolean clickedTopVanillaTabs = isInside(mouseX, mouseY, x, y - 30, backgroundWidth, 30);
                boolean clickedBottomVanillaTabs = isInside(mouseX, mouseY, x, y + backgroundHeight, backgroundWidth, 30);
                if (clickedTopVanillaTabs || clickedBottomVanillaTabs) {
                    setActiveInventoryTab(InventoryTabView.VANILLA, tabs[0], tabs[1]);
                }

                return consumed;
            });
        }
    }

    private void setActiveInventoryTab(InventoryTabView view, InventoryTab inventoryTab, InventoryTab mineCadTab) {
        activeInventoryTab = view;
        syncOverlayState();
        if (inventoryTab != null) {
            inventoryTab.setSelected(view == InventoryTabView.VANILLA);
        }
        if (mineCadTab != null) {
            mineCadTab.setSelected(view == InventoryTabView.MINE_CAD);
        }
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void syncOverlayState() {
        InventoryOverlayState.setBlockingScreen(
                activeInventoryTab == InventoryTabView.MINE_CAD ? currentScreen : null
        );
    }

  private boolean isWindowInteractionBlocked(InventoryTab tab, double mouseX, double mouseY, int x, int y, int width, int height) {
    return activeInventoryTab == InventoryTabView.MINE_CAD
        && tab != null
        && tab.isSelectedTab()
        && isInside(mouseX, mouseY, x, y, width, height);
  }
}

