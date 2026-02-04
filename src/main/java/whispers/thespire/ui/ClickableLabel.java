package whispers.thespire.ui;

import basemod.IUIElement;
import basemod.ModPanel;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public class ClickableLabel implements IUIElement {
    public final ModPanel parent;
    public final BitmapFont font;
    public final Color color;
    public String text;

    private float xU;
    private float yU;
    private float x;
    private float y;
    private final Hitbox hb;
    private final Consumer<ClickableLabel> onClick;
    private final BooleanSupplier inputBlocked;
    private boolean wasDown = false;

    public ClickableLabel(String text, float xU, float yU, BitmapFont font, Color color, ModPanel parent, Consumer<ClickableLabel> onClick) {
        this(text, xU, yU, font, color, parent, onClick, null);
    }

    public ClickableLabel(String text, float xU, float yU, BitmapFont font, Color color, ModPanel parent, Consumer<ClickableLabel> onClick, BooleanSupplier inputBlocked) {
        this.text = text;
        this.xU = xU;
        this.yU = yU;
        this.font = font;
        this.color = color;
        this.parent = parent;
        this.onClick = onClick;
        this.inputBlocked = inputBlocked;
        this.hb = new Hitbox(0f, 0f, 0f, 0f);
        updateScaled();
    }

    public void setText(String text) {
        this.text = text;
        updateHitbox();
    }

    public void set(float xU, float yU) {
        this.xU = xU;
        this.yU = yU;
        updateScaled();
    }

    public void setY(float yU) {
        this.yU = yU;
        updateScaled();
    }

    public void setX(float xU) {
        this.xU = xU;
        updateScaled();
    }

    private void updateScaled() {
        float scale = Settings.scale;
        this.x = xU * scale;
        this.y = yU * scale;
        updateHitbox();
    }

    private void updateHitbox() {
        float width = FontHelper.getWidth(font, text == null ? "" : text, 1.0f);
        float height = font.getLineHeight();
        hb.width = width;
        hb.height = height;
        hb.move(x + width / 2f, y + height / 2f);
    }

    @Override
    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
        FontHelper.renderFontLeftDownAligned(sb, font, text, x, y, color);
    }

    @Override
    public void update() {
        if (inputBlocked != null && inputBlocked.getAsBoolean()) {
            return;
        }
        hb.update();
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (hb.hovered && down && !wasDown) {
            if (onClick != null) {
                onClick.accept(this);
            }
        }
        wasDown = down;
    }

    @Override
    public int renderLayer() {
        return 2;
    }

    @Override
    public int updateOrder() {
        return 0;
    }
}
