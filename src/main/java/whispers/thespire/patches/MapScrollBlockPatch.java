package whispers.thespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import whispers.thespire.ui.OverlayPanel;

public class MapScrollBlockPatch {
    // Extra safety: clear scroll inputs on the map when the overlay wants to consume the wheel.
    @SpirePatch(clz = DungeonMapScreen.class, method = "update")
    public static class UpdatePatch {
        public static void Prefix(DungeonMapScreen __instance) {
            try {
                if (OverlayPanel.shouldBlockScroll()) {
                    InputHelper.scrollY = 0;
                    InputHelper.scrolledUp = false;
                    InputHelper.scrolledDown = false;
                }
            } catch (Exception ignored) {
                // Never break map update.
            }
        }
    }
}
