package name.minecad.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import name.minecad.client.mixin.AbstractContainerScreenAccessor;
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
         final InventoryTab[] tabs = new InventoryTab[2];

         // MineCad tab stays inside the same inventory screen.
         tabs[1] = mineCadTab;
         tabs[1].setIcon(new ItemStack(Items.BOOK));
         // Initial position will be updated in the render event for proper handling of screen size changes
         tabs[1].setX(0);
         tabs[1].setY(0);
         tabs[1].setSelected(activeInventoryTab == InventoryTabView.MINE_CAD);
         tabs[1].setAction(() -> {
             // Toggle the tab - if it's already selected, deselect it (switch to VANILLA)
             if (activeInventoryTab == InventoryTabView.MINE_CAD && tabs[1].isSelectedTab()) {
                 setActiveInventoryTab(InventoryTabView.VANILLA, tabs[0], tabs[1]);
             } else {
                 setActiveInventoryTab(InventoryTabView.MINE_CAD, tabs[0], tabs[1]);
             }
         });
         Screens.getWidgets(screen).add(tabs[1]);

      // One window per screen; position and size are read from the real vanilla screen every time.
      InventoryTabWindow window = new InventoryTabWindow(backgroundWidth, backgroundHeight);
      {
        ScreenMouseEvents.allowMouseClick(screen).register((currentScreen, event) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            return !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), b[0], b[1], b[2], b[3]);
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((currentScreen, event) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            return !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), b[0], b[1], b[2], b[3]);
        });
        ScreenMouseEvents.allowMouseDrag(screen).register((currentScreen, event, horizontalAmount, verticalAmount) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            return !isWindowInteractionBlocked(tabs[1], event.x(), event.y(), b[0], b[1], b[2], b[3]);
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            return !isWindowInteractionBlocked(tabs[1], mouseX, mouseY, b[0], b[1], b[2], b[3]);
        });
        ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, tickProgress) -> {
          int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
          int x = b[0];
          int y = b[1];
          // Update tab position based on the vanilla screen's actual position
          tabs[1].setX(x + additionalTabsOffsetX);
          tabs[1].setY((y - TAB_HEIGHT) + additionalTabsOffsetY);

          if (activeInventoryTab == InventoryTabView.MINE_CAD && tabs[1].isSelectedTab()) {
            window.setSize(b[2], b[3]);
            window.drawWindow(graphics, currentScreen.getFont(), x, y);

            // Vanilla may have requested a scrollbar/text cursor underneath; the last request wins.
            if (isInside(mouseX, mouseY, x, y, b[2], b[3])) {
              graphics.requestCursor(CursorTypes.ARROW);
            }
          }
        });
      }

          if (creativeInventoryScreen) {
              ScreenMouseEvents.afterMouseClick(screen).register((clickedScreen, event, consumed) -> {
                  if (tabs[1] == null) {
                      return consumed;
                  }

                  int[] b = bounds(clickedScreen, backgroundWidth, backgroundHeight);
                  int creativeBackgroundWidth = b[2];
                  int x = b[0];
                  int y = b[1];
                  double mouseX = event.x();
                  double mouseY = event.y();
                  boolean clickedMineCadTab = isInside(mouseX, mouseY, tabs[1].getX(), tabs[1].getY(), TAB_WIDTH, TAB_HEIGHT);
                  if (clickedMineCadTab) {
                      return consumed;
                  }

                  boolean clickedTopVanillaTabs = isInside(mouseX, mouseY, x, y - 30, creativeBackgroundWidth, 30);
                  boolean clickedBottomVanillaTabs = isInside(mouseX, mouseY, x, y + b[3], creativeBackgroundWidth, 30);
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

    /** Returns {leftPos, topPos, imageWidth, imageHeight} read from the vanilla screen (falls back to defaults). */
    private static int[] bounds(Screen screen, int defaultWidth, int defaultHeight) {
        if (screen instanceof AbstractContainerScreenAccessor accessor) {
            return new int[] {
                    accessor.minecad$getLeftPos(),
                    accessor.minecad$getTopPos(),
                    accessor.minecad$getImageWidth(),
                    accessor.minecad$getImageHeight()
            };
        }
        return new int[] {(screen.width - defaultWidth) / 2, (screen.height - defaultHeight) / 2, defaultWidth, defaultHeight};
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

