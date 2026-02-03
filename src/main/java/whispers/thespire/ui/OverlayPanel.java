package whispers.thespire.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import whispers.thespire.config.ModConfig;
import whispers.thespire.llm.LLMClient;
import whispers.thespire.llm.OpenAICompatClient;
import whispers.thespire.llm.model.LLMRecommendation;
import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;
import whispers.thespire.logic.AutoRequestController;
import whispers.thespire.logic.TriggerManager;
import whispers.thespire.state.SnapshotManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class OverlayPanel {
    private static final String BUTTON_REFRESH = "Refresh";
    private static final String BUTTON_HIDE = "Hide";
    private static final float WIDTH = 320f;
    private static final float HEIGHT = 120f;
    private static final float MIN_WIDTH = 220f;
    private static final float MIN_HEIGHT = 120f;
    private static final float PADDING = 16f;
    private static final float TITLE_H = 28f;
    private static final float HANDLE_SIZE = 16f;
    private static final float RESIZE_BORDER = 8f;
    private static final float SCREEN_MARGIN = 20f;
    private static final float BUTTON_PAD = 6f;
    private static final float BUTTON_GAP = 6f;
    private static final Color BG_COLOR = new Color(0f, 0f, 0f, 0.6f);
    private static final Color TITLE_COLOR = new Color(0f, 0f, 0f, 0.8f);
    private static final Color HANDLE_COLOR = new Color(1f, 1f, 1f, 0.8f);
    private static final Color BUTTON_BG = new Color(1f, 1f, 1f, 0.15f);
    private static final Color TEXT_COLOR = new Color(1f, 1f, 1f, 1f);

    private static boolean initialized = false;
    private static float x;
    private static float y;
    private static float w;
    private static float h;
    private static boolean dragging = false;
    private static boolean resizing = false;
    private static float dragOffsetX = 0f;
    private static float dragOffsetY = 0f;
    private static ResizeMode resizeMode = ResizeMode.NONE;
    private static float resizeStartX = 0f;
    private static float resizeStartY = 0f;
    private static float startX = 0f;
    private static float startY = 0f;
    private static float startW = 0f;
    private static float startH = 0f;
    private static String summaryLine = "snapshot n/a";
    private static String statusLine = "snapshot n/a";
    private static String lastSnapshotJson = null;
    private static String wrappedSource = null;
    private static float wrappedWidth = -1f;
    private static final List<String> wrappedLines = new ArrayList<>();

    private static final LLMClient LLM_CLIENT = new LLMClient(new OpenAICompatClient());
    private static final AutoRequestController AUTO_CONTROLLER = new AutoRequestController();
    private static Future<LLMResult> llmFuture;
    private static String llmStateLine = "Idle";
    private static String llmSummary = "";
    private static List<LLMRecommendation> llmRecommendations;
    private static String llmRaw;
    private static long lastSuccessMs = 0L;
    private static String lastContextType = "N/A";
    private static String lastAutoReason = "";
    private static int lastCombatTurn = -1;
    private static String currentRequestContext = "";
    private static String currentRequestDisplayContext = "";
    private static boolean currentRequestAuto = false;
    private static String currentRequestReason = "";

    private enum ResizeMode {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static void update() {
        if (!isInRun()) {
            dragging = false;
            resizing = false;
            return;
        }

        if (Gdx.input.isKeyJustPressed(ModConfig.hotkeyRefresh)) {
            requestLLMManual();
        }

        pollLlmFuture();
        handleAutoTriggers();

        if (!ModConfig.overlayEnabled) {
            dragging = false;
            resizing = false;
            return;
        }

        ensureInitialized();

        float mouseX = InputHelper.mX;
        float mouseY = InputHelper.mY;
        boolean mouseDown = InputHelper.isMouseDown;
        boolean justClicked = InputHelper.justClickedLeft;

        float titleH = TITLE_H * Settings.scale;
        float handleSize = HANDLE_SIZE * Settings.scale;
        float border = RESIZE_BORDER * Settings.scale;
        float buttonPad = BUTTON_PAD * Settings.scale;
        float buttonGap = BUTTON_GAP * Settings.scale;
        float buttonHeight = titleH - buttonPad * 2f;

        float hideW = getButtonWidth(BUTTON_HIDE, buttonPad);
        float refreshW = getButtonWidth(BUTTON_REFRESH, buttonPad);
        float buttonY = y + h - titleH + buttonPad;
        float hideX = x + w - buttonPad - hideW;
        float refreshX = hideX - buttonGap - refreshW;

        boolean inPanel = isInside(mouseX, mouseY, x, y, w, h);
        boolean inTitle = isInside(mouseX, mouseY, x, y + h - titleH, w, titleH);
        boolean inHide = isInside(mouseX, mouseY, hideX, buttonY, hideW, buttonHeight);
        boolean inRefresh = isInside(mouseX, mouseY, refreshX, buttonY, refreshW, buttonHeight);
        ResizeMode hoverResize = getResizeMode(mouseX, mouseY, border);
        boolean inHandle = isInside(mouseX, mouseY, x + w - handleSize, y, handleSize, handleSize);

        if (justClicked) {
            if (inHide) {
                ModConfig.overlayEnabled = false;
                ModConfig.save();
                dragging = false;
                resizing = false;
                return;
            } else if (inRefresh) {
                requestLLMManual();
            } else if (hoverResize != ResizeMode.NONE) {
                startResize(hoverResize, mouseX, mouseY);
            } else if (inTitle) {
                dragging = true;
                resizing = false;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
        }

        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
            clampPosition();
        } else if (resizing) {
            applyResize(mouseX, mouseY);
            clampPosition();
        } else {
            clampSize();
            clampPosition();
        }

        if ((inPanel && (justClicked || mouseDown)) || dragging || resizing) {
            consumeClick();
        }

        if (!mouseDown && (dragging || resizing)) {
            dragging = false;
            resizing = false;
            resizeMode = ResizeMode.NONE;
            ModConfig.savePanel(x, y, w, h);
        }

        refreshSnapshot();
    }

    public static void render(SpriteBatch sb) {
        if (!isInRun() || sb == null || !ModConfig.overlayEnabled) {
            return;
        }

        ensureInitialized();

        Texture bg = ImageMaster.WHITE_SQUARE_IMG;
        float pad = PADDING * Settings.scale;
        float titleH = TITLE_H * Settings.scale;
        float handleSize = HANDLE_SIZE * Settings.scale;
        float buttonPad = BUTTON_PAD * Settings.scale;
        float buttonGap = BUTTON_GAP * Settings.scale;
        float buttonHeight = titleH - buttonPad * 2f;
        float hideW = getButtonWidth(BUTTON_HIDE, buttonPad);
        float refreshW = getButtonWidth(BUTTON_REFRESH, buttonPad);
        float buttonY = y + h - titleH + buttonPad;
        float hideX = x + w - buttonPad - hideW;
        float refreshX = hideX - buttonGap - refreshW;

        sb.setColor(BG_COLOR);
        sb.draw(bg, x, y, w, h);
        sb.setColor(TITLE_COLOR);
        sb.draw(bg, x, y + h - titleH, w, titleH);
        sb.setColor(HANDLE_COLOR);
        sb.draw(bg, x + w - handleSize, y, handleSize, handleSize);
        sb.setColor(BUTTON_BG);
        sb.draw(bg, refreshX, buttonY, refreshW, buttonHeight);
        sb.draw(bg, hideX, buttonY, hideW, buttonHeight);
        sb.setColor(Color.WHITE);

        float textX = x + pad;
        float statusY = y + h - titleH - pad;
        float lineHeight = FontHelper.topPanelInfoFont.getLineHeight();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, summaryLine, textX, statusY, TEXT_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, statusLine, textX, statusY - lineHeight, TEXT_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, BUTTON_REFRESH, refreshX + buttonPad, buttonY + buttonHeight, TEXT_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, BUTTON_HIDE, hideX + buttonPad, buttonY + buttonHeight, TEXT_COLOR);
        if (ModConfig.autoTriggersEnabled) {
            float autoY = y + h - buttonPad;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, "AUTO", textX, autoY, TEXT_COLOR);
        }

        float contentX = x + pad;
        float contentWidth = w - pad * 2f;
        float contentBottom = y + pad;
        float cursorY = statusY - lineHeight * 2f - pad * 0.5f;

        cursorY = renderLlmSection(sb, contentX, cursorY, contentBottom, contentWidth);

        if (ModConfig.debugShowSnapshot && lastSnapshotJson != null && !lastSnapshotJson.isEmpty()) {
            renderJson(sb, FontHelper.tipBodyFont, contentX, cursorY - pad, contentBottom, contentWidth);
        }
    }

    private static boolean isInRun() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.player != null;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        float defaultW = WIDTH * Settings.scale;
        float defaultH = HEIGHT * Settings.scale;
        float pad = PADDING * Settings.scale;
        float defaultX = Settings.WIDTH - defaultW - pad;
        float defaultY = Settings.HEIGHT - defaultH - pad;

        w = ModConfig.panelW > 0f ? ModConfig.panelW : defaultW;
        h = ModConfig.panelH > 0f ? ModConfig.panelH : defaultH;
        x = ModConfig.panelX >= 0f ? ModConfig.panelX : defaultX;
        y = ModConfig.panelY >= 0f ? ModConfig.panelY : defaultY;

        clampSize();
        clampPosition();
        initialized = true;
    }

    private static void clampSize() {
        float minW = MIN_WIDTH * Settings.scale;
        float minH = MIN_HEIGHT * Settings.scale;
        float maxW = Math.max(minW, Settings.WIDTH - SCREEN_MARGIN * Settings.scale);
        float maxH = Math.max(minH, Settings.HEIGHT - SCREEN_MARGIN * Settings.scale);
        w = clamp(w, minW, maxW);
        h = clamp(h, minH, maxH);
    }

    private static void clampPosition() {
        float maxX = Math.max(0f, Settings.WIDTH - w);
        float maxY = Math.max(0f, Settings.HEIGHT - h);
        x = clamp(x, 0f, maxX);
        y = clamp(y, 0f, maxY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ResizeMode getResizeMode(float mx, float my, float border) {
        if (!isInside(mx, my, x, y, w, h)) {
            return ResizeMode.NONE;
        }
        boolean left = mx >= x && mx <= x + border;
        boolean right = mx <= x + w && mx >= x + w - border;
        boolean bottom = my >= y && my <= y + border;
        boolean top = my <= y + h && my >= y + h - border;

        if (left && top) {
            return ResizeMode.TOP_LEFT;
        }
        if (right && top) {
            return ResizeMode.TOP_RIGHT;
        }
        if (left && bottom) {
            return ResizeMode.BOTTOM_LEFT;
        }
        if (right && bottom) {
            return ResizeMode.BOTTOM_RIGHT;
        }
        if (left) {
            return ResizeMode.LEFT;
        }
        if (right) {
            return ResizeMode.RIGHT;
        }
        if (top) {
            return ResizeMode.TOP;
        }
        if (bottom) {
            return ResizeMode.BOTTOM;
        }
        return ResizeMode.NONE;
    }

    private static void startResize(ResizeMode mode, float mouseX, float mouseY) {
        resizing = true;
        dragging = false;
        resizeMode = mode;
        resizeStartX = mouseX;
        resizeStartY = mouseY;
        startX = x;
        startY = y;
        startW = w;
        startH = h;
    }

    private static void applyResize(float mouseX, float mouseY) {
        float dx = mouseX - resizeStartX;
        float dy = mouseY - resizeStartY;

        float newX = startX;
        float newY = startY;
        float newW = startW;
        float newH = startH;

        switch (resizeMode) {
            case LEFT:
                newX = startX + dx;
                newW = startW - dx;
                break;
            case RIGHT:
                newW = startW + dx;
                break;
            case TOP:
                newH = startH + dy;
                break;
            case BOTTOM:
                newY = startY + dy;
                newH = startH - dy;
                break;
            case TOP_LEFT:
                newX = startX + dx;
                newW = startW - dx;
                newH = startH + dy;
                break;
            case TOP_RIGHT:
                newW = startW + dx;
                newH = startH + dy;
                break;
            case BOTTOM_LEFT:
                newX = startX + dx;
                newW = startW - dx;
                newY = startY + dy;
                newH = startH - dy;
                break;
            case BOTTOM_RIGHT:
                newW = startW + dx;
                newY = startY + dy;
                newH = startH - dy;
                break;
            default:
                break;
        }

        float minW = MIN_WIDTH * Settings.scale;
        float minH = MIN_HEIGHT * Settings.scale;
        float maxW = Math.max(minW, Settings.WIDTH - SCREEN_MARGIN * Settings.scale);
        float maxH = Math.max(minH, Settings.HEIGHT - SCREEN_MARGIN * Settings.scale);

        boolean anchorLeft = resizeMode == ResizeMode.LEFT || resizeMode == ResizeMode.TOP_LEFT || resizeMode == ResizeMode.BOTTOM_LEFT;
        boolean anchorRight = resizeMode == ResizeMode.RIGHT || resizeMode == ResizeMode.TOP_RIGHT || resizeMode == ResizeMode.BOTTOM_RIGHT;
        boolean anchorBottom = resizeMode == ResizeMode.BOTTOM || resizeMode == ResizeMode.BOTTOM_LEFT || resizeMode == ResizeMode.BOTTOM_RIGHT;
        boolean anchorTop = resizeMode == ResizeMode.TOP || resizeMode == ResizeMode.TOP_LEFT || resizeMode == ResizeMode.TOP_RIGHT;

        if (anchorLeft) {
            float right = startX + startW;
            newW = clamp(newW, minW, maxW);
            newX = right - newW;
        } else if (anchorRight) {
            newW = clamp(newW, minW, maxW);
            newX = startX;
        }

        if (anchorBottom) {
            float top = startY + startH;
            newH = clamp(newH, minH, maxH);
            newY = top - newH;
        } else if (anchorTop) {
            newH = clamp(newH, minH, maxH);
            newY = startY;
        }

        x = newX;
        y = newY;
        w = newW;
        h = newH;
    }

    private static boolean isInside(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    private static void consumeClick() {
        // Consume input so map nodes/cards/reward buttons don't receive the click while interacting with the panel.
        InputHelper.justClickedLeft = false;
        InputHelper.isMouseDown = false;
    }

    private static float getButtonWidth(String text, float buttonPad) {
        return FontHelper.getWidth(FontHelper.topPanelInfoFont, text, 1.0f) + buttonPad * 2f;
    }

    private static void refreshSnapshot() {
        SnapshotManager.Result result = SnapshotManager.update(ModConfig.debugShowSnapshot, false);
        if (result != null) {
            summaryLine = result.summaryLine == null ? "" : result.summaryLine;
            statusLine = result.statusLine == null ? "" : result.statusLine;
            lastSnapshotJson = result.json;
        }
    }

    private static void requestLLMManual() {
        if (isBusy()) {
            llmStateLine = "busy: request in progress";
            return;
        }

        SnapshotManager.requestRefresh();
        SnapshotManager.Result snapshot = SnapshotManager.update(true, ModConfig.combatAdviceEnabled);
        if (snapshot == null || snapshot.json == null || snapshot.snapshot == null) {
            llmStateLine = "snapshot unavailable";
            return;
        }

        if (ModConfig.apiKey == null || ModConfig.apiKey.trim().isEmpty()) {
            llmStateLine = "API key missing";
            return;
        }

        String displayContext = "MANUAL";
        if (snapshot.snapshot.screen_context != null && snapshot.snapshot.screen_context.equals("COMBAT")) {
            displayContext = "COMBAT";
        }
        requestLLM(snapshot, false, displayContext, "manual");
    }

    private static void pollLlmFuture() {
        if (llmFuture == null || !llmFuture.isDone()) {
            return;
        }
        try {
            LLMResult result = llmFuture.get();
            if (result != null && result.ok) {
                llmStateLine = "ok";
                llmSummary = result.summary == null ? "" : result.summary;
                llmRecommendations = result.recommendations;
                llmRaw = null;
                lastSuccessMs = System.currentTimeMillis();
                lastContextType = currentRequestDisplayContext == null || currentRequestDisplayContext.isEmpty()
                        ? "N/A" : currentRequestDisplayContext;
            } else if (result != null) {
                llmStateLine = result.errorMessage == null ? "error" : result.errorMessage;
                llmSummary = "";
                llmRecommendations = null;
                llmRaw = result.raw;
            } else {
                llmStateLine = "error: empty result";
                llmSummary = "";
                llmRecommendations = null;
                llmRaw = null;
            }
        } catch (Exception e) {
            llmStateLine = "error: " + e.getClass().getSimpleName();
            llmSummary = "";
            llmRecommendations = null;
            llmRaw = null;
        } finally {
            llmFuture = null;
        }
    }

    private static void handleAutoTriggers() {
        if (!ModConfig.autoTriggersEnabled) {
            return;
        }
        TriggerManager.TriggerEvent event = TriggerManager.pollEvent();
        if (event == null) {
            return;
        }
        if (isBusy()) {
            llmStateLine = "COMBAT_TURN".equals(event.contextType) ? "combat skipped: busy" : "auto skipped: busy";
            return;
        }

        SnapshotManager.requestRefresh();
        SnapshotManager.Result snapshot = SnapshotManager.update(true, ModConfig.combatAdviceEnabled);
        if (snapshot == null || snapshot.json == null || snapshot.snapshot == null) {
            llmStateLine = "auto skipped: snapshot unavailable";
            return;
        }

        AutoRequestController.Decision decision = AUTO_CONTROLLER.shouldRequest(event, snapshot.snapshot.snapshot_hash, false);
        if (!decision.allow) {
            if ("COMBAT_TURN".equals(event.contextType)) {
                llmStateLine = decision.reason.replace("auto skipped", "combat skipped");
            } else {
                llmStateLine = decision.reason;
            }
            return;
        }

        AUTO_CONTROLLER.recordRequested(event.contextType, snapshot.snapshot.snapshot_hash);
        requestLLM(snapshot, true, event.contextType, "auto: " + event.reason);
    }

    private static void requestLLM(SnapshotManager.Result snapshot, boolean isAuto, String displayContext, String reason) {
        if (snapshot == null || snapshot.json == null || snapshot.snapshot == null) {
            llmStateLine = "snapshot unavailable";
            return;
        }
        if (ModConfig.apiKey == null || ModConfig.apiKey.trim().isEmpty()) {
            llmStateLine = "API key missing";
            return;
        }
        if (isBusy()) {
            llmStateLine = isAuto ? "auto skipped: busy" : "busy: request in progress";
            return;
        }

        currentRequestAuto = isAuto;
        currentRequestContext = snapshot.snapshot.screen_context;
        currentRequestDisplayContext = displayContext;
        currentRequestReason = reason;
        if (isAuto) {
            lastAutoReason = reason;
        }
        if (snapshot.snapshot.combat != null && snapshot.snapshot.combat.turn != null) {
            lastCombatTurn = snapshot.snapshot.combat.turn;
        }

        llmStateLine = isAuto ? "Analyzing... (" + reason + ")" : "Analyzing...";
        llmSummary = "";
        llmRecommendations = null;
        llmRaw = null;

        LLMRequest request = new LLMRequest();
        request.baseUrl = ModConfig.baseUrl;
        request.model = ModConfig.model;
        request.apiKey = ModConfig.apiKey;
        request.temperature = ModConfig.temperature;
        request.maxTokens = ModConfig.maxTokens;
        request.timeoutMs = ModConfig.timeoutMs;
        request.contextType = snapshot.snapshot.screen_context;
        request.snapshotJson = snapshot.json;
        request.snapshotHash = snapshot.snapshot.snapshot_hash;

        llmFuture = LLM_CLIENT.submit(request);
    }

    private static boolean isBusy() {
        return llmFuture != null && !llmFuture.isDone();
    }

    private static float renderLlmSection(SpriteBatch sb, float x, float topY, float bottomY, float maxWidth) {
        float yCursor = topY;
        BitmapFont font = FontHelper.tipBodyFont;
        float lineHeight = font.getLineHeight();

        boolean combatDisplay = (lastContextType != null && lastContextType.startsWith("COMBAT"))
                || (currentRequestDisplayContext != null && currentRequestDisplayContext.startsWith("COMBAT"))
                || (currentRequestContext != null && currentRequestContext.startsWith("COMBAT"));
        if (lastCombatTurn > 0 && combatDisplay) {
            yCursor = renderWrappedBlock(sb, FontHelper.topPanelInfoFont, "Turn " + lastCombatTurn + " suggestion", x, yCursor, bottomY, maxWidth);
        }

        String lastSec = lastSuccessMs > 0 ? String.valueOf((System.currentTimeMillis() - lastSuccessMs) / 1000) : "--";
        String meta = "last=" + lastSec + "s, context=" + lastContextType;
        if (lastAutoReason != null && !lastAutoReason.isEmpty()) {
            meta = meta + ", " + lastAutoReason;
        }
        yCursor = renderWrappedBlock(sb, FontHelper.smallDialogOptionFont, meta, x, yCursor, bottomY, maxWidth);

        if (llmFuture != null && !llmFuture.isDone()) {
            String analyzingText = llmStateLine == null || llmStateLine.isEmpty() ? "Analyzing..." : llmStateLine;
            yCursor = renderWrappedBlock(sb, font, analyzingText, x, yCursor, bottomY, maxWidth);
            return yCursor;
        }

        if (llmSummary != null && !llmSummary.isEmpty()) {
            yCursor = renderWrappedBlock(sb, font, "Summary: " + llmSummary, x, yCursor, bottomY, maxWidth);
        }

        if (llmRecommendations != null && !llmRecommendations.isEmpty()) {
            int index = 1;
            for (LLMRecommendation rec : llmRecommendations) {
                if (rec == null) {
                    continue;
                }
                String title = rec.title == null ? "" : rec.title;
                String action = rec.action == null ? "" : rec.action;
                String reason = rec.reason == null ? "" : rec.reason;
                String header = index + ") " + title + " - " + action;
                yCursor = renderWrappedBlock(sb, font, header, x, yCursor, bottomY, maxWidth);
                yCursor = renderWrappedBlock(sb, FontHelper.smallDialogOptionFont, "reason: " + reason, x + 12f * Settings.scale, yCursor, bottomY, maxWidth - 12f * Settings.scale);
                yCursor -= lineHeight * 0.3f;
                index++;
            }
            return yCursor;
        }

        if (llmStateLine != null && !llmStateLine.isEmpty()) {
            yCursor = renderWrappedBlock(sb, font, "LLM: " + llmStateLine, x, yCursor, bottomY, maxWidth);
        }

        if (ModConfig.debugShowRawResponse && llmRaw != null && !llmRaw.isEmpty()) {
            yCursor = renderWrappedBlock(sb, FontHelper.smallDialogOptionFont, "raw: " + llmRaw, x, yCursor, bottomY, maxWidth);
        }

        return yCursor;
    }

    private static float renderWrappedBlock(SpriteBatch sb, BitmapFont font, String text, float x, float topY, float bottomY, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return topY;
        }
        float yCursor = topY;
        float lineHeight = font.getLineHeight();
        List<String> lines = wrapText(font, text, maxWidth);
        for (String line : lines) {
            if (yCursor < bottomY) {
                break;
            }
            FontHelper.renderFontLeftTopAligned(sb, font, line, x, yCursor, TEXT_COLOR);
            yCursor -= lineHeight;
        }
        return yCursor;
    }

    private static void renderJson(SpriteBatch sb, BitmapFont font, float x, float topY, float bottomY, float maxWidth) {
        if (wrappedSource == null || !wrappedSource.equals(lastSnapshotJson) || wrappedWidth != maxWidth) {
            wrappedLines.clear();
            wrappedLines.addAll(wrapText(font, lastSnapshotJson, maxWidth));
            wrappedSource = lastSnapshotJson;
            wrappedWidth = maxWidth;
        }

        float yCursor = topY;
        float lineHeight = font.getLineHeight();
        for (String line : wrappedLines) {
            if (yCursor < bottomY) {
                break;
            }
            FontHelper.renderFontLeftTopAligned(sb, font, line, x, yCursor, TEXT_COLOR);
            yCursor -= lineHeight;
        }
    }

    private static List<String> wrapText(BitmapFont font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
            if (FontHelper.getWidth(font, current.toString(), 1.0f) > maxWidth) {
                if (current.length() > 1) {
                    char last = current.charAt(current.length() - 1);
                    current.setLength(current.length() - 1);
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(last);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
