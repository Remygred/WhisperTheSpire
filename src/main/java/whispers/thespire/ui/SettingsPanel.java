package whispers.thespire.ui;

import basemod.IUIElement;
import basemod.ModPanel;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;

import java.util.ArrayList;
import java.lang.reflect.Field;

public class SettingsPanel extends ModPanel {
    private float clipX = 0f;
    private float clipY = 0f;
    private float clipW = 0f;
    private float clipH = 0f;
    private OrthographicCamera fallbackCamera;

    public void setClipRect(float x, float y, float w, float h) {
        this.clipX = x;
        this.clipY = y;
        this.clipW = w;
        this.clipH = h;
    }

    @Override
    public void render(SpriteBatch sb) {
        renderBg(sb);

        ArrayList<IUIElement> elements = getRenderElements();
        if (elements == null || elements.isEmpty()) {
            return;
        }

        if (clipW <= 0f || clipH <= 0f) {
            for (IUIElement element : elements) {
                element.render(sb);
            }
            return;
        }

        Rectangle clipBounds = new Rectangle(clipX, clipY, clipW, clipH);
        Rectangle scissors = new Rectangle();

        sb.flush();
        ScissorStack.calculateScissors(getCamera(), sb.getTransformMatrix(), clipBounds, scissors);
        ScissorStack.pushScissors(scissors);

        for (IUIElement element : elements) {
            element.render(sb);
        }

        sb.flush();
        ScissorStack.popScissors();
    }

    private OrthographicCamera getCamera() {
        try {
            Field field = CardCrawlGame.class.getDeclaredField("camera");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof OrthographicCamera) {
                return (OrthographicCamera) value;
            }
        } catch (Exception ignored) {
            // Fallback to a local camera if reflection fails.
        }

        if (fallbackCamera == null
                || fallbackCamera.viewportWidth != Settings.WIDTH
                || fallbackCamera.viewportHeight != Settings.HEIGHT) {
            fallbackCamera = new OrthographicCamera(Settings.WIDTH, Settings.HEIGHT);
            fallbackCamera.position.set(Settings.WIDTH / 2f, Settings.HEIGHT / 2f, 0f);
            fallbackCamera.update();
        }
        return fallbackCamera;
    }
}
