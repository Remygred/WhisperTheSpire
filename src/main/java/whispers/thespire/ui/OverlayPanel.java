package whispers.thespire.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import whispers.thespire.config.ModConfig;
import whispers.thespire.i18n.I18n;
import whispers.thespire.llm.LLMClient;
import whispers.thespire.llm.OpenAICompatClient;
import whispers.thespire.llm.GeminiClient;
import whispers.thespire.llm.model.LLMRecommendation;
import whispers.thespire.llm.model.LLMRequest;
import whispers.thespire.llm.model.LLMResult;
import whispers.thespire.logic.AutoRequestController;
import whispers.thespire.logic.TriggerManager;
import whispers.thespire.state.SnapshotManager;
import whispers.thespire.state.GameStateSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class OverlayPanel {
    private static final String BUTTON_REFRESH = "refresh";
    private static final String BUTTON_HIDE = "hide";
    private static final float WIDTH = 320f;
    private static final float HEIGHT = 120f;
    private static final float MIN_WIDTH = 220f;
    private static final float MIN_HEIGHT = 120f;
    private static final float PADDING = 16f;
    private static final float TITLE_H = 44f;
    private static final float HANDLE_SIZE = 16f;
    private static final float RESIZE_BORDER = 8f;
    private static final float SCREEN_MARGIN = 20f;
    private static final float BUTTON_PAD = 8f;
    private static final float BUTTON_GAP = 8f;
    private static final Color TEXT_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final String BUTTON_SHOW = "show";
    private static final Texture MODPANEL_BG = ImageMaster.loadImage("img/ModPanelBg.png");

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
    private static GameStateSnapshot lastSnapshot;
    private static String wrappedSource = null;
    private static float wrappedWidth = -1f;
    private static final List<String> wrappedLines = new ArrayList<>();
    private static float contentScrollX = 0f;
    private static float contentScrollY = 0f;
    private static float contentMaxScrollX = 0f;
    private static float contentMaxScrollY = 0f;
    private static String lastContentKey = null;
    private static int pendingScrollAmount = 0;
    private static boolean draggingScrollV = false;
    private static boolean draggingScrollH = false;
    private static float scrollDragOffset = 0f;
    private static final float SCROLLBAR_PAD = 8f;
    private static final float SCROLLBAR_SCALE = 0.65f;
    private static OrthographicCamera fallbackCamera;

    private static final LLMClient LLM_CLIENT = new LLMClient(new OpenAICompatClient(), new GeminiClient());
    private static final AutoRequestController AUTO_CONTROLLER = new AutoRequestController();
    private static Future<LLMResult> llmFuture;
    private static String llmStateLine = "Idle";
    private static String llmSummary = "";
    private static List<LLMRecommendation> llmRecommendations;
    private static String llmRaw;
    private static Integer llmNextPickIndex = null;
    private static List<String> llmRoutePlan;
    private static long lastSuccessMs = 0L;
    private static String lastContextType = "N/A";
    private static String lastAutoReason = "";
    private static int lastCombatTurn = -1;
    private static String currentRequestContext = "";
    private static String currentRequestDisplayContext = "";
    private static boolean currentRequestAuto = false;
    private static String currentRequestReason = "";
    private static GameStateSnapshot currentRequestSnapshot;
    private static boolean languageRefreshPending = false;
    private static boolean wasInRun = false;

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
            if (wasInRun) {
                onRunEnded();
            }
            wasInRun = false;
            dragging = false;
            resizing = false;
            return;
        }
        if (!wasInRun) {
            wasInRun = true;
        }

        if (Gdx.input.isKeyJustPressed(ModConfig.hotkeyToggleOverlay)) {
            ModConfig.overlayEnabled = !ModConfig.overlayEnabled;
            ModConfig.save();
            if (ModConfig.overlayEnabled) {
                ensureInitialized();
                resetScrollPosition();
            }
        }

        if (languageRefreshPending) {
            if (!isBusy()) {
                languageRefreshPending = false;
                requestLLMManual();
            }
        }

        if (Gdx.input.isKeyJustPressed(ModConfig.hotkeyRefresh)) {
            requestLLMManual();
        }

        pollLlmFuture();
        handleAutoTriggers();

        if (!ModConfig.overlayEnabled) {
            dragging = false;
            resizing = false;
            updateHiddenButton();
            return;
        }

        ensureInitialized();

        float mouseX = InputHelper.mX;
        float mouseY = InputHelper.mY;
        boolean mouseDown = InputHelper.isMouseDown;
        boolean justClicked = InputHelper.justClickedLeft;

        float titleH = getTitleHeight();
        float handleSize = HANDLE_SIZE * Settings.scale;
        float border = RESIZE_BORDER * Settings.scale;
        float buttonPad = BUTTON_PAD * Settings.scale;
        float buttonGap = BUTTON_GAP * Settings.scale;
        float yesH = ImageMaster.OPTION_YES.getHeight() * Settings.scale;
        float btnScale = 1.0f;
        float buttonHeight = yesH * btnScale;
        float refreshW = ImageMaster.OPTION_YES.getWidth() * Settings.scale * btnScale;
        float hideW = ImageMaster.OPTION_NO.getWidth() * Settings.scale * btnScale;
        float buttonY = y + h - buttonHeight - buttonPad * 0.5f;
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

        if (!dragging && !resizing) {
            updateContentScroll();
        }

        refreshSnapshot();
    }

    public static void onLanguageChanged() {
        languageRefreshPending = true;
        // Reset scroll so the updated language is visible from the top.
        resetScrollPosition();
    }

    private static void resetScrollPosition() {
        contentScrollX = 0f;
        contentScrollY = 0f;
        lastContentKey = null;
    }

    public static boolean shouldBlockScroll() {
        if (!ModConfig.overlayEnabled || !isInRun()) {
            return false;
        }
        ensureInitialized();
        return isInside(InputHelper.mX, InputHelper.mY, x, y, w, h);
    }

    public static void onScrollEvent(int amount) {
        pendingScrollAmount += amount;
    }

    public static void render(SpriteBatch sb) {
        if (!isInRun() || sb == null) {
            return;
        }

        if (!ModConfig.overlayEnabled) {
            renderHiddenButton(sb);
            return;
        }

        ensureInitialized();

        Texture bgPanel = MODPANEL_BG == null ? ImageMaster.POTION_UI_BG : MODPANEL_BG;
        Texture yesTex = ImageMaster.OPTION_YES;
        Texture noTex = ImageMaster.OPTION_NO;
        float pad = PADDING * Settings.scale;
        float titleH = getTitleHeight();
        float handleSize = HANDLE_SIZE * Settings.scale;
        float buttonPad = BUTTON_PAD * Settings.scale;
        float buttonGap = BUTTON_GAP * Settings.scale;
        float yesH = yesTex.getHeight() * Settings.scale;
        float btnScale = 1.0f;
        float buttonHeight = yesH * btnScale;
        float refreshW = yesTex.getWidth() * Settings.scale * btnScale;
        float hideW = noTex.getWidth() * Settings.scale * btnScale;
        float buttonY = y + h - buttonHeight - buttonPad * 0.5f;
        float hideX = x + w - buttonPad - hideW;
        float refreshX = hideX - buttonGap - refreshW;

        sb.setColor(Color.WHITE);
        sb.draw(bgPanel, x, y, w, h);
        sb.setColor(Color.WHITE);
        sb.draw(yesTex, refreshX, buttonY, refreshW, buttonHeight);
        sb.draw(noTex, hideX, buttonY, hideW, buttonHeight);

        String refreshText = ellipsize(FontHelper.cardTitleFont, I18n.t(BUTTON_REFRESH), refreshW * 0.9f, 0.75f);
        String hideText = ellipsize(FontHelper.cardTitleFont, I18n.t(BUTTON_HIDE), hideW * 0.9f, 0.75f);
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, refreshText,
                refreshX + refreshW / 2f,
                buttonY + buttonHeight * 0.62f,
                Settings.CREAM_COLOR, 0.75f);
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, hideText,
                hideX + hideW / 2f,
                buttonY + buttonHeight * 0.62f,
                Settings.CREAM_COLOR, 0.75f);
        if (ModConfig.autoTriggersEnabled) {
            float autoY = y + h - buttonPad;
            float textX = x + pad;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, I18n.t("auto"), textX, autoY, Settings.GOLD_COLOR);
        }
        renderContent(sb, pad, titleH);
    }

    private static void updateHiddenButton() {
        float scale = Settings.scale;
        float btnH = ImageMaster.OPTION_YES.getHeight() * scale * 0.8f;
        float btnW = ImageMaster.OPTION_YES.getWidth() * scale * 0.8f;
        float shift = 320f * scale;
        float xBtn = Settings.WIDTH - btnW - 20f * scale - shift;
        if (xBtn < 20f * scale) {
            xBtn = 20f * scale;
        }
        float yBtn = Settings.HEIGHT - btnH - 20f * scale;
        boolean inBtn = isInside(InputHelper.mX, InputHelper.mY, xBtn, yBtn, btnW, btnH);
        boolean clicked = InputHelper.justClickedLeft;
        boolean down = InputHelper.isMouseDown;
        if (inBtn && (clicked || down)) {
            if (clicked) {
                ModConfig.overlayEnabled = true;
                ModConfig.save();
                ensureInitialized();
                resetScrollPosition();
            }
            consumeClick();
            InputHelper.isMouseDown = false;
            InputHelper.touchDown = false;
            InputHelper.touchUp = false;
        }
    }

    private static void renderHiddenButton(SpriteBatch sb) {
        float scale = Settings.scale;
        float btnH = ImageMaster.OPTION_YES.getHeight() * scale * 0.8f;
        float btnW = ImageMaster.OPTION_YES.getWidth() * scale * 0.8f;
        float shift = 320f * scale;
        float xBtn = Settings.WIDTH - btnW - 20f * scale - shift;
        if (xBtn < 20f * scale) {
            xBtn = 20f * scale;
        }
        float yBtn = Settings.HEIGHT - btnH - 20f * scale;
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.OPTION_YES, xBtn, yBtn, btnW, btnH);
        String text = ellipsize(FontHelper.cardTitleFont, I18n.t(BUTTON_SHOW), btnW * 0.9f, 0.75f);
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, text,
                xBtn + btnW / 2f,
                yBtn + btnH * 0.62f,
                Settings.CREAM_COLOR, 0.75f);
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
        InputHelper.justReleasedClickLeft = false;
    }


    private static void refreshSnapshot() {
        SnapshotManager.Result result = SnapshotManager.update(ModConfig.debugShowSnapshot, false);
        if (result != null) {
            summaryLine = result.summaryLine == null ? "" : result.summaryLine;
            statusLine = result.statusLine == null ? "" : result.statusLine;
            lastSnapshotJson = result.json;
            lastSnapshot = result.snapshot;
        }
    }

    private static void requestLLMManual() {
        if (isBusy()) {
            cancelCurrentRequest();
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
                llmNextPickIndex = result.nextPickIndex;
                llmRoutePlan = result.routePlan;
                lastSuccessMs = System.currentTimeMillis();
                lastContextType = currentRequestDisplayContext == null || currentRequestDisplayContext.isEmpty()
                        ? "N/A" : currentRequestDisplayContext;
            } else if (result != null) {
                llmStateLine = result.errorMessage == null ? "error" : result.errorMessage;
                llmSummary = "";
                llmRecommendations = null;
                llmRaw = result.raw;
                llmNextPickIndex = null;
                llmRoutePlan = null;
            } else {
                llmStateLine = "error: empty result";
                llmSummary = "";
                llmRecommendations = null;
                llmRaw = null;
                llmNextPickIndex = null;
                llmRoutePlan = null;
            }
        } catch (Exception e) {
            llmStateLine = "error: " + e.getClass().getSimpleName();
            llmSummary = "";
            llmRecommendations = null;
            llmRaw = null;
            llmNextPickIndex = null;
            llmRoutePlan = null;
        } finally {
            llmFuture = null;
            currentRequestSnapshot = null;
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
        currentRequestSnapshot = snapshot.snapshot;
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
        llmNextPickIndex = null;
        llmRoutePlan = null;

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
        if (snapshot.snapshot.run != null) {
            request.ascension = snapshot.snapshot.run.ascension;
            request.character = snapshot.snapshot.run.character;
            request.seed = snapshot.snapshot.run.seed;
            request.floor = snapshot.snapshot.run.floor;
            request.act = snapshot.snapshot.run.act;
            request.hp = snapshot.snapshot.run.hp;
            request.maxHp = snapshot.snapshot.run.maxHp;
            request.gold = snapshot.snapshot.run.gold;
        }
        if (snapshot.snapshot.map != null) {
            StringBuilder current = new StringBuilder();
            if (snapshot.snapshot.map.curr_x != null && snapshot.snapshot.map.curr_y != null) {
                String type = I18n.localizeRoomType(snapshot.snapshot.map.curr_type);
                current.append(type).append("(")
                        .append(snapshot.snapshot.map.curr_x)
                        .append(",")
                        .append(snapshot.snapshot.map.curr_y)
                        .append(")");
            } else if (snapshot.snapshot.map.curr_type != null) {
                current.append(I18n.localizeRoomType(snapshot.snapshot.map.curr_type));
            }
            request.mapCurrent = current.toString();
            if (snapshot.snapshot.map.next_nodes != null && !snapshot.snapshot.map.next_nodes.isEmpty()) {
                List<GameStateSnapshot.NodeInfo> nodes = new ArrayList<>(snapshot.snapshot.map.next_nodes);
                nodes.sort((a, b) -> {
                    int ax = a == null || a.x == null ? Integer.MAX_VALUE : a.x;
                    int bx = b == null || b.x == null ? Integer.MAX_VALUE : b.x;
                    return Integer.compare(ax, bx);
                });
                StringBuilder next = new StringBuilder();
                int count = 0;
                for (GameStateSnapshot.NodeInfo node : nodes) {
                    if (node == null || node.x == null || node.y == null) {
                        continue;
                    }
                    if (count > 0) {
                        next.append(" | ");
                    }
                    String type = I18n.localizeRoomType(node.room_type);
                    next.append(count + 1).append(") ").append(type)
                            .append("(").append(node.x).append(",").append(node.y).append(")");
                    count++;
                }
                request.mapNext = next.toString();
            }
        }
        request.mapFullAvailable = snapshot.snapshot.map_full != null;
        request.essentialFacts = buildEssentialFacts(snapshot.snapshot);

        llmFuture = LLM_CLIENT.submit(request);
    }

    private static String buildEssentialFacts(GameStateSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String character = snapshot.run != null ? safeStr(snapshot.run.character) : "";
        String ascension = snapshot.run != null && snapshot.run.ascension != null ? snapshot.run.ascension.toString() : "";
        String hp = snapshot.run != null && snapshot.run.hp != null ? snapshot.run.hp.toString() : "";
        String maxHp = snapshot.run != null && snapshot.run.maxHp != null ? snapshot.run.maxHp.toString() : "";
        String gold = snapshot.run != null && snapshot.run.gold != null ? snapshot.run.gold.toString() : "";
        sb.append("character=").append(character.isEmpty() ? "unknown" : character);
        sb.append(", ascension=").append(ascension.isEmpty() ? "unknown" : ascension);
        sb.append(", hp=").append(hp.isEmpty() ? "?" : hp).append("/").append(maxHp.isEmpty() ? "?" : maxHp);
        sb.append(", gold=").append(gold.isEmpty() ? "?" : gold);

        List<AbstractCard> fullDeck = AbstractDungeon.player != null ? AbstractDungeon.player.masterDeck.group : null;
        if (fullDeck != null && !fullDeck.isEmpty()) {
            sb.append(", deck_count=").append(fullDeck.size());
            sb.append(", deck=[");
            int added = 0;
            for (AbstractCard card : fullDeck) {
                if (card == null) {
                    continue;
                }
                String name = card.name != null && !card.name.isEmpty() ? card.name : card.cardID;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (added > 0) {
                    sb.append(",");
                }
                sb.append(name);
                if (card.timesUpgraded > 0) {
                    sb.append("+").append(card.timesUpgraded);
                }
                added++;
            }
            sb.append("]");
        } else if (snapshot.deck_summary != null && !snapshot.deck_summary.isEmpty()) {
            sb.append(", deck_count=").append(snapshot.deck_summary.size());
            sb.append(", deck=[");
            int added = 0;
            for (GameStateSnapshot.CardInfo card : snapshot.deck_summary) {
                if (card == null) {
                    continue;
                }
                String name = card.name != null && !card.name.isEmpty() ? card.name : card.card_id;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (added > 0) {
                    sb.append(",");
                }
                sb.append(name);
                if (Boolean.TRUE.equals(card.upgraded)) {
                    sb.append("+");
                }
                added++;
            }
            sb.append("]");
        } else {
            sb.append(", deck_count=0");
        }

        if (snapshot.relics != null && !snapshot.relics.isEmpty()) {
            sb.append(", relics=[");
            int limit = Math.min(20, snapshot.relics.size());
            int added = 0;
            for (int i = 0; i < limit; i++) {
                GameStateSnapshot.RelicInfo relic = snapshot.relics.get(i);
                if (relic == null) {
                    continue;
                }
                String name = relic.name != null && !relic.name.isEmpty() ? relic.name : relic.relic_id;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (added > 0) {
                    sb.append(",");
                }
                sb.append(name);
                added++;
            }
            sb.append("]");
        } else {
            sb.append(", relics=[]");
        }

        if (snapshot.potions != null && !snapshot.potions.isEmpty()) {
            sb.append(", potions=[");
            int limit = Math.min(5, snapshot.potions.size());
            int added = 0;
            for (int i = 0; i < limit; i++) {
                GameStateSnapshot.PotionInfo potion = snapshot.potions.get(i);
                if (potion == null) {
                    continue;
                }
                String name = potion.name != null && !potion.name.isEmpty() ? potion.name : potion.potion_id;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (added > 0) {
                    sb.append(",");
                }
                sb.append(name);
                added++;
            }
            sb.append("]");
        } else {
            sb.append(", potions=[]");
        }

        return sb.toString();
    }

    private static String safeStr(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBusy() {
        return llmFuture != null && !llmFuture.isDone();
    }

    private static void cancelCurrentRequest() {
        try {
            if (llmFuture != null) {
                llmFuture.cancel(true);
            }
        } catch (Exception ignored) {
            // ignore cancellation errors
        } finally {
            llmFuture = null;
        }
    }

    private static void onRunEnded() {
        cancelCurrentRequest();
        AUTO_CONTROLLER.reset();
        SnapshotManager.reset();
        llmStateLine = "Idle";
        llmSummary = "";
        llmRecommendations = null;
        llmRaw = null;
        llmNextPickIndex = null;
        llmRoutePlan = null;
        lastSuccessMs = 0L;
        lastContextType = "N/A";
        lastAutoReason = "";
        lastSnapshot = null;
        lastSnapshotJson = null;
        lastContentKey = null;
        languageRefreshPending = false;
        currentRequestContext = "";
        currentRequestDisplayContext = "";
        currentRequestAuto = false;
        currentRequestReason = "";
        currentRequestSnapshot = null;
    }

    private static void renderContent(SpriteBatch sb, float pad, float titleH) {
        ContentRect fullRect = getContentRectFull(pad, titleH);
        ContentRect rect = getContentRectForText(pad, titleH);
        if (rect.height <= 0f || rect.width <= 0f) {
            return;
        }

        List<RenderLine> lines = buildContentLines(rect.width);
        if (lines.isEmpty()) {
            return;
        }

        String contentKey = buildContentKey();
        if (lastContentKey == null || !lastContentKey.equals(contentKey)) {
            contentScrollX = 0f;
            contentScrollY = 0f;
            lastContentKey = contentKey;
        }

        float totalHeight = 0f;
        float maxLineWidth = 0f;
        float maxContentWidth = 0f;
        for (RenderLine line : lines) {
            float lineHeight = line.font.getLineHeight();
            totalHeight += lineHeight + line.gapAfter;
            float lineWidth = FontHelper.getWidth(line.font, line.text, 1.0f);
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
            maxContentWidth = Math.max(maxContentWidth, lineWidth + line.indent);
        }

        float viewHeight = rect.height;
        float viewWidth = rect.width;
        float startY = rect.top;
        if (totalHeight < viewHeight) {
            startY = rect.top - (viewHeight - totalHeight) / 2f;
        }

        float baseX = rect.left;
        if (maxLineWidth < viewWidth) {
            baseX = rect.left + (viewWidth - maxLineWidth) / 2f;
        }

        contentMaxScrollY = Math.max(0f, totalHeight - viewHeight);
        contentMaxScrollX = Math.max(0f, maxContentWidth - viewWidth);
        contentScrollY = clamp(contentScrollY, 0f, contentMaxScrollY);
        contentScrollX = clamp(contentScrollX, 0f, contentMaxScrollX);

        Rectangle clipBounds = new Rectangle(rect.left, rect.bottom, rect.width, rect.height);
        Rectangle scissors = new Rectangle();
        sb.flush();
        ScissorStack.calculateScissors(getCamera(), sb.getTransformMatrix(), clipBounds, scissors);
        ScissorStack.pushScissors(scissors);

        float yCursor = startY + contentScrollY;
        for (RenderLine line : lines) {
            float lineHeight = line.font.getLineHeight();
            float lineTop = yCursor;
            float lineBottom = lineTop - lineHeight;
            if (lineBottom < rect.bottom) {
                break;
            }
            if (lineTop <= rect.top + lineHeight) {
                float x = baseX - contentScrollX + line.indent;
                FontHelper.renderFontLeftTopAligned(sb, line.font, line.text, x, lineTop, line.color);
            }
            yCursor -= lineHeight + line.gapAfter;
        }

        sb.flush();
        ScissorStack.popScissors();

        renderScrollbars(sb, fullRect);
    }

    private static void renderScrollbars(SpriteBatch sb, ContentRect rect) {
        ScrollBarMetrics vBar = getVerticalBar(rect);
        ScrollBarMetrics hBar = getHorizontalBar(rect);
        float scale = Settings.scale;

        if (vBar != null && ImageMaster.SCROLL_BAR_TOP != null) {
            sb.setColor(Color.WHITE);
            float topH = ImageMaster.SCROLL_BAR_TOP.getHeight() * scale;
            float bottomH = ImageMaster.SCROLL_BAR_BOTTOM.getHeight() * scale;
            float middleH = Math.max(0f, vBar.trackH - topH - bottomH);
            sb.draw(ImageMaster.SCROLL_BAR_TOP, vBar.trackX, vBar.trackY + vBar.trackH - topH, vBar.trackW, topH);
            sb.draw(ImageMaster.SCROLL_BAR_MIDDLE, vBar.trackX, vBar.trackY + bottomH, vBar.trackW, middleH);
            sb.draw(ImageMaster.SCROLL_BAR_BOTTOM, vBar.trackX, vBar.trackY, vBar.trackW, bottomH);

            if (ImageMaster.SCROLL_BAR_TRAIN != null) {
                sb.draw(ImageMaster.SCROLL_BAR_TRAIN, vBar.thumbX, vBar.thumbY, vBar.thumbW, vBar.thumbH);
            }
        }

        if (hBar != null && ImageMaster.SCROLL_BAR_LEFT != null) {
            sb.setColor(Color.WHITE);
            float leftW = ImageMaster.SCROLL_BAR_LEFT.getWidth() * scale;
            float rightW = ImageMaster.SCROLL_BAR_RIGHT.getWidth() * scale;
            float middleW = Math.max(0f, hBar.trackW - leftW - rightW);
            sb.draw(ImageMaster.SCROLL_BAR_LEFT, hBar.trackX, hBar.trackY, leftW, hBar.trackH);
            sb.draw(ImageMaster.SCROLL_BAR_HORIZONTAL_MIDDLE, hBar.trackX + leftW, hBar.trackY, middleW, hBar.trackH);
            sb.draw(ImageMaster.SCROLL_BAR_RIGHT, hBar.trackX + hBar.trackW - rightW, hBar.trackY, rightW, hBar.trackH);

            if (ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN != null) {
                sb.draw(ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN, hBar.thumbX, hBar.thumbY, hBar.thumbW, hBar.thumbH);
            }
        }
    }

    private static String buildLocalizedSummary() {
        if (lastSnapshot == null || lastSnapshot.run == null) {
            return summaryLine == null ? "" : summaryLine;
        }
        String screen = I18n.localizeContext(lastSnapshot.screen_context == null ? "OTHER" : lastSnapshot.screen_context);
        String floor = lastSnapshot.run.floor == null ? "?" : lastSnapshot.run.floor.toString();
        String asc = lastSnapshot.run.ascension == null ? "?" : ("A" + lastSnapshot.run.ascension);
        String hp = lastSnapshot.run.hp == null ? "?" : lastSnapshot.run.hp.toString();
        String maxHp = lastSnapshot.run.maxHp == null ? "?" : lastSnapshot.run.maxHp.toString();
        String gold = lastSnapshot.run.gold == null ? "?" : lastSnapshot.run.gold.toString();
        return I18n.t("screen") + "=" + screen + ", "
                + I18n.t("floor") + "=" + floor + ", "
                + I18n.t("asc") + "=" + asc + ", "
                + I18n.t("hp") + "=" + hp + "/" + maxHp + ", "
                + I18n.t("gold") + "=" + gold;
    }

    private static String buildLocalizedStatus() {
        if (lastSnapshot == null) {
            return statusLine == null ? "" : statusLine;
        }
        StringBuilder sb = new StringBuilder();
        if (lastSnapshot.trimmed) {
            sb.append(I18n.t("snapshot_trimmed"));
        } else {
            sb.append(I18n.t("snapshot_ok"));
        }
        sb.append(" ").append(I18n.t("size")).append("=").append(lastSnapshot.json_size);
        if (lastSnapshot.dropped_fields != null && !lastSnapshot.dropped_fields.isEmpty()) {
            sb.append(" ").append(I18n.t("dropped")).append("=").append(String.join(",", lastSnapshot.dropped_fields));
        }
        if (lastSnapshot.snapshot_hash != null && lastSnapshot.snapshot_hash.length() >= 8) {
            sb.append(" ").append(I18n.t("hash")).append("=").append(lastSnapshot.snapshot_hash.substring(0, 8));
        }
        return sb.toString();
    }

    private static String buildContentKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(summaryLine == null ? "" : summaryLine).append('|');
        sb.append(statusLine == null ? "" : statusLine).append('|');
        sb.append(llmSummary == null ? "" : llmSummary).append('|');
        sb.append(llmStateLine == null ? "" : llmStateLine).append('|');
        if (llmRecommendations != null) {
            for (LLMRecommendation rec : llmRecommendations) {
                if (rec == null) {
                    continue;
                }
                sb.append(rec.title == null ? "" : rec.title).append('|');
                sb.append(rec.action == null ? "" : rec.action).append('|');
                sb.append(rec.reason == null ? "" : rec.reason).append('|');
            }
        }
        sb.append(llmRaw == null ? "" : llmRaw).append('|');
        sb.append(lastSnapshotJson == null ? "" : lastSnapshotJson).append('|');
        sb.append(lastContextType == null ? "" : lastContextType).append('|');
        sb.append(lastAutoReason == null ? "" : lastAutoReason).append('|');
        sb.append(ModConfig.language == null ? "" : ModConfig.language).append('|');
        sb.append(ModConfig.showReasons).append('|');
        sb.append(ModConfig.multiRecommendations);
        sb.append('|').append(ModConfig.useKnowledgeBase);
        sb.append('|').append(llmNextPickIndex == null ? "" : llmNextPickIndex.toString());
        if (llmRoutePlan != null) {
            for (String plan : llmRoutePlan) {
                sb.append('|').append(plan == null ? "" : plan);
            }
        }
        return sb.toString();
    }

    private static List<RenderLine> buildContentLines(float maxWidth) {
        List<RenderLine> lines = new ArrayList<>();
        String localizedSummary = buildLocalizedSummary();
        String localizedStatus = buildLocalizedStatus();
        addWrappedLine(lines, FontHelper.topPanelInfoFont, localizedSummary, 0f, TEXT_COLOR, 0f, maxWidth);
        addWrappedLine(lines, FontHelper.topPanelInfoFont, localizedStatus, 0f, TEXT_COLOR, 8f * Settings.scale, maxWidth);

        String lastSec = lastSuccessMs > 0 ? String.valueOf((System.currentTimeMillis() - lastSuccessMs) / 1000) : "--";
        String metaContext = lastContextType == null || lastContextType.isEmpty() ? I18n.t("na") : I18n.localizeContext(lastContextType);
        String meta = I18n.t("last") + "=" + lastSec + "s, " + I18n.t("context") + "=" + metaContext;
        if (lastAutoReason != null && !lastAutoReason.isEmpty()) {
            meta = meta + ", " + lastAutoReason;
        }
        addWrappedLine(lines, FontHelper.smallDialogOptionFont, meta, 0f, TEXT_COLOR, 8f * Settings.scale, maxWidth);

        if (llmFuture != null && !llmFuture.isDone()) {
            String analyzingText = llmStateLine == null || llmStateLine.isEmpty() ? I18n.t("analyzing") : llmStateLine;
            addWrappedLine(lines, FontHelper.tipBodyFont, analyzingText, 0f, TEXT_COLOR, 0f, maxWidth);
            return lines;
        }

        if (llmSummary != null && !llmSummary.isEmpty()) {
            addWrappedLine(lines, FontHelper.tipBodyFont, I18n.t("summary") + ": " + llmSummary, 0f, TEXT_COLOR, 6f * Settings.scale, maxWidth);
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
                addWrappedLine(lines, FontHelper.tipBodyFont, header, 0f, TEXT_COLOR, 2f * Settings.scale, maxWidth);
                if (ModConfig.showReasons) {
                    addWrappedLine(lines, FontHelper.smallDialogOptionFont, I18n.t("reason") + ": " + reason, 18f * Settings.scale, TEXT_COLOR, 6f * Settings.scale, maxWidth);
                }
                index++;
            }
        } else if (llmStateLine != null && !llmStateLine.isEmpty()) {
            addWrappedLine(lines, FontHelper.tipBodyFont, I18n.t("llm") + ": " + llmStateLine, 0f, TEXT_COLOR, 0f, maxWidth);
        }

        if (llmNextPickIndex != null && llmNextPickIndex > 0) {
            addWrappedLine(lines, FontHelper.smallDialogOptionFont,
                    I18n.t("next_pick") + ": " + llmNextPickIndex, 0f, TEXT_COLOR, 6f * Settings.scale, maxWidth);
        }
        if (llmRoutePlan != null && !llmRoutePlan.isEmpty()) {
            for (String plan : llmRoutePlan) {
                if (plan == null || plan.trim().isEmpty()) {
                    continue;
                }
                addWrappedLine(lines, FontHelper.smallDialogOptionFont,
                        I18n.t("route_plan") + ": " + plan, 0f, TEXT_COLOR, 4f * Settings.scale, maxWidth);
            }
        }

        if (ModConfig.debugShowRawResponse && llmRaw != null && !llmRaw.isEmpty()) {
            addWrappedLine(lines, FontHelper.smallDialogOptionFont, "raw: " + llmRaw, 0f, TEXT_COLOR, 6f * Settings.scale, maxWidth);
        }

        if (ModConfig.debugShowSnapshot && lastSnapshotJson != null && !lastSnapshotJson.isEmpty()) {
            for (String line : lastSnapshotJson.split("\n")) {
                addWrappedLine(lines, FontHelper.tipBodyFont, line, 0f, TEXT_COLOR, 0f, maxWidth);
            }
        }

        return lines;
    }

    private static void addWrappedLine(List<RenderLine> lines, BitmapFont font, String text, float indent, Color color, float gapAfter, float maxWidth) {
        if (text == null) {
            text = "";
        }
        float available = Math.max(40f * Settings.scale, (maxWidth - indent) * 0.92f);
        List<String> wrapped = wrapText(font, text, available);
        if (wrapped.isEmpty()) {
            lines.add(new RenderLine("", font, indent, color, gapAfter));
            return;
        }
        for (int i = 0; i < wrapped.size(); i++) {
            float gap = (i == wrapped.size() - 1) ? gapAfter : 0f;
            lines.add(new RenderLine(wrapped.get(i), font, indent, color, gap));
        }
    }

    private static void updateContentScroll() {
        ContentRect fullRect = getContentRectFull(PADDING * Settings.scale, getTitleHeight());
        ContentRect rect = getContentRectForText(PADDING * Settings.scale, getTitleHeight());
        if (rect.width <= 0f || rect.height <= 0f) {
            return;
        }
        float mouseX = InputHelper.mX;
        float mouseY = InputHelper.mY;
        boolean mouseDown = InputHelper.isMouseDown;
        boolean justClicked = InputHelper.justClickedLeft;

        ScrollBarMetrics vBar = getVerticalBar(fullRect);
        ScrollBarMetrics hBar = getHorizontalBar(fullRect);

        boolean inPanel = isInside(mouseX, mouseY, x, y, w, h);
        boolean inRect = isInside(mouseX, mouseY, rect.left, rect.bottom, rect.width, rect.height);
        int pending = pendingScrollAmount;
        if (pending != 0) {
            pendingScrollAmount = 0;
        }
        boolean hasScrollInput = pending != 0 || InputHelper.scrollY != 0 || InputHelper.scrolledUp || InputHelper.scrolledDown;

        if (draggingScrollV) {
            if (!mouseDown) {
                draggingScrollV = false;
            } else if (vBar != null && vBar.trackH > 0f) {
                float newThumbY = clamp(mouseY - scrollDragOffset, vBar.trackY, vBar.trackY + vBar.trackH - vBar.thumbH);
                float ratio = 1f - ((newThumbY - vBar.trackY) / (vBar.trackH - vBar.thumbH));
                contentScrollY = clamp(ratio * contentMaxScrollY, 0f, contentMaxScrollY);
            }
            return;
        }

        if (draggingScrollH) {
            if (!mouseDown) {
                draggingScrollH = false;
            } else if (hBar != null && hBar.trackW > 0f) {
                float newThumbX = clamp(mouseX - scrollDragOffset, hBar.trackX, hBar.trackX + hBar.trackW - hBar.thumbW);
                float ratio = (newThumbX - hBar.trackX) / (hBar.trackW - hBar.thumbW);
                contentScrollX = clamp(ratio * contentMaxScrollX, 0f, contentMaxScrollX);
            }
            return;
        }

        if (!inPanel) {
            return;
        }

        if (justClicked) {
            if (vBar != null && isInside(mouseX, mouseY, vBar.thumbX, vBar.thumbY, vBar.thumbW, vBar.thumbH)) {
                draggingScrollV = true;
                scrollDragOffset = mouseY - vBar.thumbY;
                consumeClick();
                return;
            }
            if (hBar != null && isInside(mouseX, mouseY, hBar.thumbX, hBar.thumbY, hBar.thumbW, hBar.thumbH)) {
                draggingScrollH = true;
                scrollDragOffset = mouseX - hBar.thumbX;
                consumeClick();
                return;
            }
            if (vBar != null && isInside(mouseX, mouseY, vBar.trackX, vBar.trackY, vBar.trackW, vBar.trackH)) {
                float targetY = clamp(mouseY - vBar.thumbH * 0.5f, vBar.trackY, vBar.trackY + vBar.trackH - vBar.thumbH);
                float ratio = 1f - ((targetY - vBar.trackY) / (vBar.trackH - vBar.thumbH));
                contentScrollY = clamp(ratio * contentMaxScrollY, 0f, contentMaxScrollY);
                consumeClick();
                return;
            }
            if (hBar != null && isInside(mouseX, mouseY, hBar.trackX, hBar.trackY, hBar.trackW, hBar.trackH)) {
                float ratio = (mouseX - hBar.trackX) / (hBar.trackW - hBar.thumbW);
                contentScrollX = clamp(ratio * contentMaxScrollX, 0f, contentMaxScrollX);
                consumeClick();
                return;
            }
        }

        float step = 32f * Settings.scale;
        float deltaUnits = 0f;
        if (pending != 0) {
            deltaUnits = pending;
        } else if (InputHelper.scrollY != 0) {
            deltaUnits = InputHelper.scrollY;
        } else if (InputHelper.scrolledUp) {
            deltaUnits = -1f;
        } else if (InputHelper.scrolledDown) {
            deltaUnits = 1f;
        }

        if (deltaUnits != 0f && inRect) {
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            if (shift) {
                contentScrollX = clamp(contentScrollX + deltaUnits * step, 0f, contentMaxScrollX);
            } else {
                contentScrollY = clamp(contentScrollY + deltaUnits * step, 0f, contentMaxScrollY);
            }
            InputHelper.scrollY = 0;
            InputHelper.scrolledUp = false;
            InputHelper.scrolledDown = false;
        } else if (hasScrollInput) {
            // Consume scroll input even if no scrollable content, to prevent map scrolling under the panel.
            InputHelper.scrollY = 0;
            InputHelper.scrolledUp = false;
            InputHelper.scrolledDown = false;
        }
    }

    private static ContentRect getContentRectFull(float pad, float titleH) {
        float left = x + pad;
        float right = x + w - pad;
        float bottom = y + pad;
        float top = y + h - titleH - pad;
        return new ContentRect(left, bottom, right - left, top - bottom, top);
    }

    private static ContentRect getContentRectForText(float pad, float titleH) {
        ContentRect full = getContentRectFull(pad, titleH);
        float reserveX = 18f * Settings.scale;
        float reserveY = 12f * Settings.scale;
        float left = full.left;
        float bottom = full.bottom + reserveY;
        float width = Math.max(0f, full.width - reserveX);
        float height = Math.max(0f, full.height - reserveY);
        float top = bottom + height;
        return new ContentRect(left, bottom, width, height, top);
    }

    private static ScrollBarMetrics getVerticalBar(ContentRect rect) {
        if (contentMaxScrollY <= 1f || ImageMaster.SCROLL_BAR_TOP == null || ImageMaster.SCROLL_BAR_TRAIN == null) {
            return null;
        }
        float scale = Settings.scale;
        float trackPad = SCROLLBAR_PAD * scale;
        float trackW = ImageMaster.SCROLL_BAR_TOP.getWidth() * scale * SCROLLBAR_SCALE;
        float trackX = rect.left + rect.width - trackW - trackPad;
        float trackY = rect.bottom + trackPad;
        float trackH = rect.height - trackPad * 2f;
        float trainW = ImageMaster.SCROLL_BAR_TRAIN.getWidth() * scale * SCROLLBAR_SCALE;
        float thumbH = Math.max(ImageMaster.SCROLL_BAR_TRAIN.getHeight() * scale * SCROLLBAR_SCALE,
                trackH * (rect.height / (rect.height + contentMaxScrollY)));
        float thumbY = trackY + (trackH - thumbH) * (1f - (contentScrollY / contentMaxScrollY));
        float thumbX = trackX + (trackW - trainW) * 0.5f;
        return new ScrollBarMetrics(trackX, trackY, trackW, trackH, thumbX, thumbY, trainW, thumbH);
    }

    private static ScrollBarMetrics getHorizontalBar(ContentRect rect) {
        if (contentMaxScrollX <= 1f || ImageMaster.SCROLL_BAR_LEFT == null || ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN == null) {
            return null;
        }
        float scale = Settings.scale;
        float trackPad = SCROLLBAR_PAD * scale;
        float trackH = ImageMaster.SCROLL_BAR_LEFT.getHeight() * scale * SCROLLBAR_SCALE;
        float trackX = rect.left + trackPad;
        float trackY = rect.bottom + trackPad;
        float trackW = rect.width - trackPad * 2f;
        float trainH = ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN.getHeight() * scale * SCROLLBAR_SCALE;
        float thumbW = Math.max(ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN.getWidth() * scale * SCROLLBAR_SCALE,
                trackW * (rect.width / (rect.width + contentMaxScrollX)));
        float thumbX = trackX + (trackW - thumbW) * (contentScrollX / contentMaxScrollX);
        float thumbY = trackY + (trackH - trainH) * 0.5f;
        return new ScrollBarMetrics(trackX, trackY, trackW, trackH, thumbX, thumbY, thumbW, trainH);
    }

    private static OrthographicCamera getCamera() {
        try {
            java.lang.reflect.Field field = CardCrawlGame.class.getDeclaredField("camera");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof OrthographicCamera) {
                return (OrthographicCamera) value;
            }
        } catch (Exception ignored) {
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

    private static List<String> wrapText(BitmapFont font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String[] rawLines = text.split("\\n", -1);
        for (String raw : rawLines) {
            if (raw.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
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
        }
        return lines;
    }

    private static float getTitleHeight() {
        float yesH = ImageMaster.OPTION_YES.getHeight() * Settings.scale;
        float btnScale = 1.0f;
        float buttonHeight = yesH * btnScale;
        return Math.max(TITLE_H * Settings.scale, buttonHeight + BUTTON_PAD * Settings.scale * 2f);
    }

    private static String ellipsize(BitmapFont font, String text, float maxWidth, float scale) {
        if (text == null) {
            return "";
        }
        if (FontHelper.getWidth(font, text, scale) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        float suffixWidth = FontHelper.getWidth(font, suffix, scale);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            float width = FontHelper.getWidth(font, sb.toString(), scale);
            if (width + suffixWidth > maxWidth) {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
        }
        return sb.toString() + suffix;
    }

    private static class RenderLine {
        private final String text;
        private final BitmapFont font;
        private final float indent;
        private final Color color;
        private final float gapAfter;

        private RenderLine(String text, BitmapFont font, float indent, Color color, float gapAfter) {
            this.text = text == null ? "" : text;
            this.font = font;
            this.indent = indent;
            this.color = color;
            this.gapAfter = gapAfter;
        }
    }

    private static class ContentRect {
        private final float left;
        private final float bottom;
        private final float width;
        private final float height;
        private final float top;

        private ContentRect(float left, float bottom, float width, float height, float top) {
            this.left = left;
            this.bottom = bottom;
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
            this.top = top;
        }
    }

    private static class ScrollBarMetrics {
        private final float trackX;
        private final float trackY;
        private final float trackW;
        private final float trackH;
        private final float thumbX;
        private final float thumbY;
        private final float thumbW;
        private final float thumbH;

        private ScrollBarMetrics(float trackX, float trackY, float trackW, float trackH,
                                 float thumbX, float thumbY, float thumbW, float thumbH) {
            this.trackX = trackX;
            this.trackY = trackY;
            this.trackW = trackW;
            this.trackH = trackH;
            this.thumbX = thumbX;
            this.thumbY = thumbY;
            this.thumbW = thumbW;
            this.thumbH = thumbH;
        }
    }
}
