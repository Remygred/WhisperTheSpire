package whispers.thespire.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.function.Consumer;

public class TextInputOverlay implements InputProcessor {
    private static final TextInputOverlay INSTANCE = new TextInputOverlay();

    private static boolean active = false;
    private static String title = "";
    private static String hint = "";
    private static String text = "";
    private static Consumer<String> onConfirm;
    private static InputProcessor previousProcessor;

    private static final Color DIM = new Color(0f, 0f, 0f, 0.6f);
    private static final Color TEXT_COLOR = new Color(1f, 1f, 1f, 1f);

    private static boolean lastMouseDown = false;

    private TextInputOverlay() {}

    public static boolean isActive() {
        return active;
    }

    public static void open(String titleText, String initial, String hintText, Consumer<String> confirm) {
        title = titleText == null ? "" : titleText;
        hint = hintText == null ? "" : hintText;
        text = initial == null ? "" : initial;
        onConfirm = confirm;
        active = true;
        previousProcessor = Gdx.input.getInputProcessor();
        Gdx.input.setInputProcessor(INSTANCE);
    }

    public static void update() {
        if (!active) {
            return;
        }
        Layout layout = computeLayout();
        boolean down = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean justPressed = down && !lastMouseDown;
        lastMouseDown = down;

        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        if (justPressed) {
            if (isInside(mx, my, layout.confirmX, layout.buttonY, layout.yesW, layout.yesH)) {
                confirm();
            } else if (isInside(mx, my, layout.cancelX, layout.buttonY, layout.noW, layout.noH)) {
                cancel();
            }
        }
    }

    public static void render(SpriteBatch sb) {
        if (!active || sb == null) {
            return;
        }
        Layout layout = computeLayout();

        sb.setColor(DIM);
        Texture dimBg = ImageMaster.WHITE_SQUARE_IMG;
        sb.draw(dimBg, layout.panelX, layout.panelY, layout.panelW, layout.panelH);
        sb.setColor(Color.WHITE);

        Texture panelTex = ImageMaster.OPTION_CONFIRM;
        sb.draw(panelTex, layout.overlayX, layout.overlayY, layout.overlayW, layout.overlayH);

        Texture yesTex = ImageMaster.OPTION_YES;
        Texture noTex = ImageMaster.OPTION_NO;
        sb.draw(noTex, layout.cancelX, layout.buttonY, layout.noW, layout.noH);
        sb.draw(yesTex, layout.confirmX, layout.buttonY, layout.yesW, layout.yesH);

        BitmapFont titleFont = FontHelper.cardTitleFont;
        BitmapFont bodyFont = FontHelper.tipBodyFont;
        BitmapFont smallFont = FontHelper.smallDialogOptionFont;

        float titleY = layout.overlayY + layout.overlayH - layout.padTop;
        FontHelper.renderFontCentered(sb, titleFont, title, layout.overlayX + layout.overlayW / 2f, titleY, TEXT_COLOR, 0.9f);

        float hintY = titleY - titleFont.getLineHeight() - 6f * Settings.scale;
        if (hint != null && !hint.isEmpty()) {
            FontHelper.renderFontCentered(sb, smallFont, hint, layout.overlayX + layout.overlayW / 2f, hintY, TEXT_COLOR, 0.9f);
        }

        float textY = layout.inputY + layout.inputH * 0.68f;
        String displayText = text == null ? "" : text;
        FontHelper.renderFontCentered(sb, bodyFont, displayText, layout.overlayX + layout.overlayW / 2f, textY, TEXT_COLOR, 0.9f);

        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, "Cancel",
                layout.cancelX + layout.noW / 2f,
                layout.buttonY + layout.noH * 0.60f,
                Settings.CREAM_COLOR, 0.85f);
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, "Confirm",
                layout.confirmX + layout.yesW / 2f,
                layout.buttonY + layout.yesH * 0.60f,
                Settings.CREAM_COLOR, 0.85f);
    }

    private static Layout computeLayout() {
        float scale = Settings.scale;
        Layout layout = new Layout();
        layout.panelX = 278f * scale;
        layout.panelY = Settings.OPTION_Y - 376f * scale;
        layout.panelW = 1364f * scale;
        layout.panelH = 752f * scale;

        float innerX = layout.panelX + 66f * scale;
        float innerY = layout.panelY + 87f * scale;
        float innerW = (1364f - 66f - 71f) * scale;
        float innerH = (752f - 87f - 57f) * scale;

        Texture panelTex = ImageMaster.OPTION_CONFIRM;
        float baseW = panelTex.getWidth() * scale;
        float baseH = panelTex.getHeight() * scale;
        float fitScale = Math.min(1f, Math.min(innerW / baseW, innerH / baseH));
        layout.panelScale = fitScale;
        layout.overlayW = baseW * fitScale;
        layout.overlayH = baseH * fitScale;
        layout.overlayX = innerX + (innerW - layout.overlayW) / 2f;
        layout.overlayY = innerY + (innerH - layout.overlayH) / 2f;

        layout.pad = 22f * scale * fitScale;
        layout.padTop = 34f * scale * fitScale;

        Texture yesTex = ImageMaster.OPTION_YES;
        Texture noTex = ImageMaster.OPTION_NO;
        layout.yesW = yesTex.getWidth() * scale * fitScale;
        layout.yesH = yesTex.getHeight() * scale * fitScale;
        layout.noW = noTex.getWidth() * scale * fitScale;
        layout.noH = noTex.getHeight() * scale * fitScale;
        layout.buttonGap = 16f * scale * fitScale;
        layout.buttonY = layout.overlayY + layout.overlayH * 0.16f;
        float buttonsW = layout.noW + layout.yesW + layout.buttonGap;
        float buttonsX = layout.overlayX + (layout.overlayW - buttonsW) / 2f;
        layout.cancelX = buttonsX;
        layout.confirmX = buttonsX + layout.noW + layout.buttonGap;

        float inputW = layout.overlayW - layout.pad * 2f;
        float inputH = 58f * scale * fitScale;
        layout.inputW = inputW;
        layout.inputH = inputH;
        layout.inputX = layout.overlayX + layout.pad;
        layout.inputY = layout.overlayY + layout.overlayH * 0.58f - inputH / 2f;
        return layout;
    }

    private static boolean isInside(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    private static class Layout {
        float panelX;
        float panelY;
        float panelW;
        float panelH;
        float overlayX;
        float overlayY;
        float overlayW;
        float overlayH;
        float pad;
        float padTop;
        float panelScale;
        float yesW;
        float yesH;
        float noW;
        float noH;
        float buttonGap;
        float buttonY;
        float confirmX;
        float cancelX;
        float inputX;
        float inputY;
        float inputW;
        float inputH;
    }


    private static void confirm() {
        if (onConfirm != null) {
            onConfirm.accept(text == null ? "" : text);
        }
        close();
    }

    private static void cancel() {
        close();
    }

    private static void close() {
        active = false;
        if (previousProcessor != null) {
            Gdx.input.setInputProcessor(previousProcessor);
            previousProcessor = null;
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!active) {
            return false;
        }
        if (keycode == Input.Keys.BACKSPACE) {
            if (text != null && !text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
            return true;
        }
        if (keycode == Input.Keys.ENTER) {
            confirm();
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            cancel();
            return true;
        }
        if (keycode == Input.Keys.V && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
            String clip = Gdx.app.getClipboard() == null ? null : Gdx.app.getClipboard().getContents();
            if (clip != null && !clip.isEmpty()) {
                text = (text == null ? "" : text) + clip;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        if (!active) {
            return false;
        }
        if (character == '\b' || character == '\r' || character == '\n') {
            return false;
        }
        if (character < 32) {
            return false;
        }
        text = (text == null ? "" : text) + character;
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }
}
