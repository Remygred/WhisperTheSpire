package whispers.thespire;

import basemod.BaseMod;
import basemod.IUIElement;
import basemod.ModLabel;
import basemod.ModLabeledToggleButton;
import basemod.ModMinMaxSlider;
import basemod.ModPanel;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.RenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import whispers.thespire.config.ModConfig;
import whispers.thespire.i18n.I18n;
import whispers.thespire.ui.OverlayPanel;
import whispers.thespire.ui.SettingsPanel;
import whispers.thespire.ui.ClickableLabel;
import whispers.thespire.ui.TextInputOverlay;

import java.util.ArrayList;
import java.lang.reflect.Field;

@SpireInitializer
public class WhispersTheSpireMod implements RenderSubscriber, PostUpdateSubscriber, PostInitializeSubscriber {
    private static SettingsPanel settingsPanel;
    private static final ArrayList<UiEntry> settingsEntries = new ArrayList<>();
    private static ModLabel headerLabel;
    private static ModLabel hotkeyLabel;
    private static ModLabel toggleHotkeyLabel;
    private static ModLabeledToggleButton overlayToggle;
    private static ModLabeledToggleButton autoToggle;
    private static ModLabeledToggleButton combatToggle;
    private static ModLabeledToggleButton snapshotToggle;
    private static ModLabeledToggleButton rawToggle;
    private static ModLabeledToggleButton showReasonsToggle;
    private static ModLabeledToggleButton multiToggle;
    private static ModLabeledToggleButton knowledgeToggle;
    private static ModMinMaxSlider tempSlider;
    private static ModMinMaxSlider maxTokensSlider;
    private static ModMinMaxSlider timeoutSlider;
    private static ClickableLabel providerLabel;
    private static ClickableLabel baseUrlLabel;
    private static ClickableLabel modelLabel;
    private static ClickableLabel apiKeyLabel;
    private static ClickableLabel languageLabel;
    private static float scrollOffset = 0f;
    private static float scrollMin = 0f;
    private static float scrollMax = 0f;
    private static float viewTop = 0f;
    private static float viewBottom = 0f;
    private static float bgX = 0f;
    private static float bgY = 0f;
    private static float bgW = 0f;
    private static float bgH = 0f;

    public WhispersTheSpireMod() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        ModConfig.load();
        new WhispersTheSpireMod();
    }

    @Override
    public void receiveRender(SpriteBatch sb) {
        // Use Render so the overlay stays above game UI, while letting the cursor render on top.
        OverlayPanel.render(sb);
    }

    @Override
    public void receivePostUpdate() {
        OverlayPanel.update();
        updateSettingsScroll();
    }

    @Override
    public void receivePostInitialize() {
        registerBadgeAndSettings();
    }

    private static void registerBadgeAndSettings() {
        settingsPanel = new SettingsPanel();
        buildSettingsUI(settingsPanel);

        Texture badgeTexture = ImageMaster.loadImage("images/badge.png");
        BaseMod.registerModBadge(badgeTexture,
                "WhispersTheSpire",
                "Codex",
                "LLM assistant overlay and configuration.",
                settingsPanel);
    }

    private static void buildSettingsUI(ModPanel panel) {
        settingsEntries.clear();

        float scale = Settings.scale;
        float invScale = 1f / scale;

        // Background absolute (screen) coordinates match ModPanel.renderBg
        bgW = 1364f * scale;
        bgH = 752f * scale;
        bgX = 278f * scale;
        // ModPanel.renderBg draws at y=OPTION_Y-376 with originY=376 and scale.
        // Bottom edge = OPTION_Y - 376*scale.
        bgY = Settings.OPTION_Y - 376f * scale;

        // Unscaled coordinates for BaseMod UI elements (they multiply by Settings.scale internally)
        float bgXU = bgX * invScale;
        float bgYU = bgY * invScale;
        float bgWU = 1364f;
        float bgHU = 752f;

        float insetLU = 66f;
        float insetRU = 71f;
        float insetBU = 87f;
        float insetTU = 57f;

        float innerXU = bgXU + insetLU;
        float innerYU = bgYU + insetBU;
        float innerWU = bgWU - insetLU - insetRU;
        float innerHU = bgHU - insetBU - insetTU;

        float padXU = 24f;
        float padYU = 20f;

        float contentLeftU = innerXU + padXU;
        float contentRightU = innerXU + innerWU - padXU;
        float contentTopU = innerYU + innerHU - padYU;
        float contentBottomU = innerYU + padYU;

        float contentWidthU = contentRightU - contentLeftU;
        float blockWU = contentWidthU;
        float blockXU = contentLeftU;

        float leftWidthU = blockWU * 0.50f;
        float gapU = blockWU * 0.04f;
        float rightWidthU = blockWU - leftWidthU - gapU;

        float leftXU = blockXU;
        float rightXU = blockXU + leftWidthU + gapU;
        float availableHU = contentTopU - contentBottomU;

        int rightRows = 9;
        int leftRows = 7;
        int maxRows = Math.max(rightRows, leftRows);
        float rowU = clamp(availableHU / (maxRows + 1.5f), 44f, 66f);

        float contentMaxY = contentTopU;
        float contentMinY = contentBottomU;

        float blockHeight = rowU * maxRows;
        // Anchor content higher in the blue panel (move up by ~one row height).
        float headerY = contentTopU - rowU * 1.2f;
        headerLabel = new ModLabel(I18n.t("settings_title"), leftXU, headerY, panel, label -> {});
        addElement(panel, headerLabel, headerY, headerLabel::setY);
        contentMaxY = Math.max(contentMaxY, headerY);
        contentMinY = Math.min(contentMinY, headerY);

        float languageY = headerY - rowU;
        languageLabel = new ClickableLabel("", rightXU, languageY, FontHelper.charDescFont, Color.WHITE, panel, label -> {
            ModConfig.language = I18n.isZh() ? "en" : "zh";
            ModConfig.save();
            applyLocalization();
            OverlayPanel.onLanguageChanged();
        }, TextInputOverlay::isActive);
        updateLanguageLabel();
        addElement(panel, languageLabel, languageY, languageLabel::setY);
        contentMinY = Math.min(contentMinY, languageY);

        float rightY = languageY - rowU;
        overlayToggle = new ModLabeledToggleButton(
                I18n.t("overlay_enabled"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.overlayEnabled,
                panel,
                label -> {},
                button -> {
                    ModConfig.overlayEnabled = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, overlayToggle, rightY, overlayToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        autoToggle = new ModLabeledToggleButton(
                I18n.t("auto_triggers_enabled"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.autoTriggersEnabled,
                panel,
                label -> {},
                button -> {
                    ModConfig.autoTriggersEnabled = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, autoToggle, rightY, autoToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        combatToggle = new ModLabeledToggleButton(
                I18n.t("combat_advice_enabled"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.combatAdviceEnabled,
                panel,
                label -> {},
                button -> {
                    ModConfig.combatAdviceEnabled = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, combatToggle, rightY, combatToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        snapshotToggle = new ModLabeledToggleButton(
                I18n.t("debug_show_snapshot"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.debugShowSnapshot,
                panel,
                label -> {},
                button -> {
                    ModConfig.debugShowSnapshot = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, snapshotToggle, rightY, snapshotToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        rawToggle = new ModLabeledToggleButton(
                I18n.t("debug_show_raw_response"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.debugShowRawResponse,
                panel,
                label -> {},
                button -> {
                    ModConfig.debugShowRawResponse = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, rawToggle, rightY, rawToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        showReasonsToggle = new ModLabeledToggleButton(
                I18n.t("show_reasons"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.showReasons,
                panel,
                label -> {},
                button -> {
                    ModConfig.showReasons = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, showReasonsToggle, rightY, showReasonsToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        knowledgeToggle = new ModLabeledToggleButton(
                I18n.t("use_knowledge"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.useKnowledgeBase,
                panel,
                label -> {},
                button -> {
                    ModConfig.useKnowledgeBase = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, knowledgeToggle, rightY, knowledgeToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        multiToggle = new ModLabeledToggleButton(
                I18n.t("multi_recommendations"),
                rightXU,
                rightY,
                Color.WHITE,
                FontHelper.charDescFont,
                ModConfig.multiRecommendations,
                panel,
                label -> {},
                button -> {
                    ModConfig.multiRecommendations = button.enabled;
                    ModConfig.save();
                }
        );
        addElement(panel, multiToggle, rightY, multiToggle::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= rowU;

        apiKeyLabel = new ClickableLabel("", rightXU, rightY, FontHelper.charDescFont, Color.WHITE, panel, label -> {
            openTextInput(I18n.t("apiKey"), ModConfig.apiKey, I18n.t("apiKey"), value -> {
                ModConfig.apiKey = value;
                updateApiKeyLabel();
                ModConfig.save();
            });
        }, TextInputOverlay::isActive);
        updateApiKeyLabel();
        addElement(panel, apiKeyLabel, rightY, apiKeyLabel::setY);
        contentMinY = Math.min(contentMinY, rightY);

        float leftY = headerY - rowU;
        tempSlider = new ModMinMaxSlider(
                I18n.t("temperature"),
                leftXU,
                leftY,
                0.0f,
                1.0f,
                ModConfig.temperature,
                "",
                panel,
                slider -> {
                    ModConfig.temperature = slider.getValue();
                    ModConfig.save();
                }
        );
        addElement(panel, tempSlider, leftY, tempSlider::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        maxTokensSlider = new ModMinMaxSlider(
                I18n.t("maxTokens"),
                leftXU,
                leftY,
                64.0f,
                4096.0f,
                ModConfig.maxTokens,
                "",
                panel,
                slider -> {
                    ModConfig.maxTokens = Math.round(slider.getValue());
                    ModConfig.save();
                }
        );
        addElement(panel, maxTokensSlider, leftY, maxTokensSlider::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        timeoutSlider = new ModMinMaxSlider(
                I18n.t("timeoutMs"),
                leftXU,
                leftY,
                1000.0f,
                60000.0f,
                ModConfig.timeoutMs,
                "",
                panel,
                slider -> {
                    ModConfig.timeoutMs = Math.round(slider.getValue());
                    ModConfig.save();
                }
        );
        addElement(panel, timeoutSlider, leftY, timeoutSlider::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        providerLabel = new ClickableLabel("", leftXU, leftY, FontHelper.charDescFont, Color.WHITE, panel, label -> {
            openTextInput(I18n.t("provider"), ModConfig.provider, I18n.t("provider"), value -> {
                ModConfig.provider = value;
                updateProviderLabel();
                ModConfig.save();
            });
        }, TextInputOverlay::isActive);
        updateProviderLabel();
        addElement(panel, providerLabel, leftY, providerLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        baseUrlLabel = new ClickableLabel("", leftXU, leftY, FontHelper.charDescFont, Color.WHITE, panel, label -> {
            openTextInput(I18n.t("baseUrl"), ModConfig.baseUrl, I18n.t("baseUrl"), value -> {
                ModConfig.baseUrl = value;
                updateBaseUrlLabel();
                ModConfig.save();
            });
        }, TextInputOverlay::isActive);
        updateBaseUrlLabel();
        addElement(panel, baseUrlLabel, leftY, baseUrlLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        modelLabel = new ClickableLabel("", leftXU, leftY, FontHelper.charDescFont, Color.WHITE, panel, label -> {
            openTextInput(I18n.t("model"), ModConfig.model, I18n.t("model"), value -> {
                ModConfig.model = value;
                updateModelLabel();
                ModConfig.save();
            });
        }, TextInputOverlay::isActive);
        updateModelLabel();
        addElement(panel, modelLabel, leftY, modelLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU;

        hotkeyLabel = new ModLabel(I18n.t("refresh_hotkey"), leftXU, leftY, panel, label -> {});
        addElement(panel, hotkeyLabel, leftY, hotkeyLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);
        leftY -= rowU * 0.9f;

        toggleHotkeyLabel = new ModLabel(I18n.t("toggle_hotkey"), leftXU, leftY, panel, label -> {});
        addElement(panel, toggleHotkeyLabel, leftY, toggleHotkeyLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);

        viewTop = contentTopU;
        viewBottom = contentBottomU;
        scrollOffset = 0f;
        updateScrollBounds(contentMinY, contentMaxY);
        applyScroll();

        settingsPanel.setClipRect(contentLeftU * scale, contentBottomU * scale, contentWidthU * scale, (contentTopU - contentBottomU) * scale);
        applyLocalization();
    }

    private static void openTextInput(String title, String initial, String hint, java.util.function.Consumer<String> onAccept) {
        TextInputOverlay.open(title, initial, hint, onAccept);
    }

    private static void updateProviderLabel() {
        if (providerLabel == null) {
            return;
        }
        providerLabel.setText(I18n.t("provider") + ": " + ellipsize(safe(ModConfig.provider), 28));
    }

    private static void updateBaseUrlLabel() {
        if (baseUrlLabel == null) {
            return;
        }
        baseUrlLabel.setText(I18n.t("baseUrl") + ": " + ellipsize(safe(ModConfig.baseUrl), 36));
    }

    private static void updateModelLabel() {
        if (modelLabel == null) {
            return;
        }
        modelLabel.setText(I18n.t("model") + ": " + ellipsize(safe(ModConfig.model), 28));
    }

    private static void updateApiKeyLabel() {
        if (apiKeyLabel == null) {
            return;
        }
        String suffix;
        if (ModConfig.apiKey == null || ModConfig.apiKey.isEmpty()) {
            suffix = I18n.isZh() ? "\u0028\u7A7A\u0029" : "(empty)";
        } else {
            suffix = I18n.isZh() ? "\u0028\u5DF2\u8BBE\u7F6E\u0029" : "(set)";
        }
        apiKeyLabel.setText(I18n.t("apiKey") + ": " + suffix);
    }

    private static void updateLanguageLabel() {
        if (languageLabel == null) {
            return;
        }
        languageLabel.setText(I18n.t("language"));
    }

    private static void applyLocalization() {
        if (headerLabel != null) {
            headerLabel.text = I18n.t("settings_title");
        }
        if (overlayToggle != null && overlayToggle.text != null) {
            overlayToggle.text.text = I18n.t("overlay_enabled");
        }
        if (autoToggle != null && autoToggle.text != null) {
            autoToggle.text.text = I18n.t("auto_triggers_enabled");
        }
        if (combatToggle != null && combatToggle.text != null) {
            combatToggle.text.text = I18n.t("combat_advice_enabled");
        }
        if (snapshotToggle != null && snapshotToggle.text != null) {
            snapshotToggle.text.text = I18n.t("debug_show_snapshot");
        }
        if (rawToggle != null && rawToggle.text != null) {
            rawToggle.text.text = I18n.t("debug_show_raw_response");
        }
        if (showReasonsToggle != null && showReasonsToggle.text != null) {
            showReasonsToggle.text.text = I18n.t("show_reasons");
        }
        if (knowledgeToggle != null && knowledgeToggle.text != null) {
            knowledgeToggle.text.text = I18n.t("use_knowledge");
        }
        if (multiToggle != null && multiToggle.text != null) {
            multiToggle.text.text = I18n.t("multi_recommendations");
        }
        if (hotkeyLabel != null) {
            hotkeyLabel.text = I18n.t("refresh_hotkey");
        }
        if (toggleHotkeyLabel != null) {
            toggleHotkeyLabel.text = I18n.t("toggle_hotkey");
        }
        updateLanguageLabel();
        updateProviderLabel();
        updateBaseUrlLabel();
        updateModelLabel();
        updateApiKeyLabel();
        updateSliderLabel(tempSlider, I18n.t("temperature"));
        updateSliderLabel(maxTokensSlider, I18n.t("maxTokens"));
        updateSliderLabel(timeoutSlider, I18n.t("timeoutMs"));
    }

    private static void updateSliderLabel(ModMinMaxSlider slider, String label) {
        if (slider == null) {
            return;
        }
        try {
            Field field = ModMinMaxSlider.class.getDeclaredField("label");
            field.setAccessible(true);
            field.set(slider, label);
        } catch (Exception ignored) {
            // If reflection fails, keep existing label.
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String ellipsize(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static void addElement(ModPanel panel, IUIElement element, float baseY, YSetter setter) {
        panel.addUIElement(element);
        settingsEntries.add(new UiEntry(baseY, setter));
    }

    private static void updateSettingsScroll() {
        if (settingsPanel == null || !settingsPanel.isUp) {
            return;
        }
        if (TextInputOverlay.isActive() || !isMouseOverPanel()) {
            return;
        }

        float step = 32f;
        float delta = 0f;
        if (InputHelper.scrollY != 0) {
            delta = -InputHelper.scrollY * step;
        } else if (InputHelper.scrolledUp) {
            delta = -step;
        } else if (InputHelper.scrolledDown) {
            delta = step;
        }

        if (delta != 0f) {
            scrollOffset = clamp(scrollOffset + delta, scrollMin, scrollMax);
            applyScroll();
            InputHelper.scrolledUp = false;
            InputHelper.scrolledDown = false;
            InputHelper.scrollY = 0;
        }
    }

    private static boolean isMouseOverPanel() {
        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        return mx >= bgX && mx <= bgX + bgW && my >= bgY && my <= bgY + bgH;
    }

    private static void updateScrollBounds(float contentMinY, float contentMaxY) {
        float viewHeight = viewTop - viewBottom;
        float contentHeight = contentMaxY - contentMinY;
        if (contentHeight <= viewHeight + 0.01f) {
            scrollMin = 0f;
            scrollMax = 0f;
            scrollOffset = 0f;
            return;
        }

        float minOffset = viewBottom - contentMinY;
        float maxOffset = viewTop - contentMaxY;
        if (minOffset > maxOffset) {
            float tmp = minOffset;
            minOffset = maxOffset;
            maxOffset = tmp;
        }
        scrollMin = minOffset;
        scrollMax = maxOffset;
        scrollOffset = clamp(scrollOffset, scrollMin, scrollMax);
    }

    private static void applyScroll() {
        for (UiEntry entry : settingsEntries) {
            entry.setY.set(entry.baseY + scrollOffset);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface YSetter {
        void set(float value);
    }

    private static class UiEntry {
        private final float baseY;
        private final YSetter setY;

        private UiEntry(float baseY, YSetter setY) {
            this.baseY = baseY;
            this.setY = setY;
        }
    }
}
