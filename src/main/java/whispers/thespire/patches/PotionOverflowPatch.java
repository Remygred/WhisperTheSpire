package whispers.thespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.ui.panels.PotionPopUp;
import whispers.thespire.logic.TriggerManager;

public class PotionOverflowPatch {
    // Patch the potion replacement popup opening to detect overflow events.
    @SpirePatch(clz = PotionPopUp.class, method = "open", paramtypez = {int.class, AbstractPotion.class})
    public static class OpenPatch {
        public static void Prefix(PotionPopUp __instance, int slot, AbstractPotion potion) {
            try {
                TriggerManager.notifyPotionOverflow(potion);
            } catch (Exception ignored) {
                // Never break the game flow if our trigger fails.
            }
        }
    }
}
