package whispers.thespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.helpers.input.ScrollInputProcessor;
import whispers.thespire.ui.OverlayPanel;

public class OverlayScrollPatch {
    // Block map/reward scrolling when the cursor is over the overlay, and forward the wheel to the panel.
    @SpirePatch(clz = ScrollInputProcessor.class, method = "scrolled")
    public static class ScrolledPatch {
        public static SpireReturn<Boolean> Prefix(ScrollInputProcessor __instance, int amount) {
            try {
                if (OverlayPanel.shouldBlockScroll()) {
                    OverlayPanel.onScrollEvent(amount);
                    return SpireReturn.Return(false);
                }
            } catch (Exception ignored) {
                // Never break the game input flow.
            }
            return SpireReturn.Continue();
        }
    }
}
