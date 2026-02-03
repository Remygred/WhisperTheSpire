package whispers.thespire;

import basemod.BaseMod;
import basemod.IUIElement;
import basemod.ModLabel;
import basemod.ModLabeledButton;
import basemod.ModLabeledToggleButton;
import basemod.ModMinMaxSlider;
import basemod.ModPanel;
import basemod.ModTextPanel;
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
import whispers.thespire.ui.OverlayPanel;
import whispers.thespire.ui.SettingsPanel;

import java.util.ArrayList;

@SpireInitializer
public class WhispersTheSpireMod implements RenderSubscriber, PostUpdateSubscriber, PostInitializeSubscriber {
    private static SettingsPanel settingsPanel;
    private static final ArrayList<UiEntry> settingsEntries = new ArrayList<>();
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

        bgW = 1364f * Settings.scale;
        bgH = 752f * Settings.scale;
        bgX = 278f * Settings.scale;
        bgY = Settings.OPTION_Y - 376f * Settings.scale;

        float insetL = 67f * Settings.scale;
        float insetR = 70f * Settings.scale;
        float insetB = 87f * Settings.scale;
        float insetT = 56f * Settings.scale;

        float innerX = bgX + insetL;
        float innerY = bgY + insetB;
        float innerW = bgW - insetL - insetR;
        float innerH = bgH - insetB - insetT;

        float padX = 24f * Settings.scale;
        float padY = 20f * Settings.scale;

        float contentLeft = innerX + padX;
        float contentRight = innerX + innerW - padX;
        float contentTop = innerY + innerH - padY;
        float contentBottom = innerY + padY;

        float contentWidth = contentRight - contentLeft;
        float blockW = Math.min(contentWidth, 980f * Settings.scale);
        float blockX = contentLeft + (contentWidth - blockW) / 2f;

        float leftWidth = blockW * 0.46f;
        float gap = blockW * 0.08f;
        float rightWidth = blockW - leftWidth - gap;

        float leftX = blockX;
        float rightX = blockX + leftWidth + gap;
        float rightButtonX = rightX + rightWidth * 0.64f;
        float availableH = contentTop - contentBottom;

        int maxIndex = 9;
        float row = clamp(availableH / (maxIndex + 1.5f), 34f * Settings.scale, 64f * Settings.scale);
        float centerY = contentBottom + availableH / 2f;
        float blockTop = centerY + (maxIndex / 2f) * row;

        float contentMaxY = blockTop;
        float contentMinY = contentBottom;

        float headerY = blockTop;
        ModLabel header = new ModLabel("WhispersTheSpire Settings", leftX, headerY, panel, label -> {});
        addElement(panel, header, headerY, header::setY);
        contentMaxY = Math.max(contentMaxY, headerY);
        contentMinY = Math.min(contentMinY, headerY);

        float rightY = blockTop - row;
        ModLabeledToggleButton overlayToggle = new ModLabeledToggleButton(
                "Overlay enabled",
                rightX,
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
        rightY -= row;

        ModLabeledToggleButton autoToggle = new ModLabeledToggleButton(
                "Auto triggers enabled",
                rightX,
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
        rightY -= row;

        ModLabeledToggleButton combatToggle = new ModLabeledToggleButton(
                "Combat advice enabled",
                rightX,
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
        rightY -= row;

        ModLabeledToggleButton snapshotToggle = new ModLabeledToggleButton(
                "Debug: show snapshot",
                rightX,
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
        rightY -= row;

        ModLabeledToggleButton rawToggle = new ModLabeledToggleButton(
                "Debug: show raw response",
                rightX,
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
        rightY -= row;

        ModTextPanel textPanel = new ModTextPanel();

        ModLabel providerLabel = new ModLabel("", rightX, rightY, panel, label -> {});
        updateProviderLabel(providerLabel);
        addElement(panel, providerLabel, rightY, providerLabel::setY);
        ModLabeledButton providerEdit = new ModLabeledButton("Edit", rightButtonX, rightY, panel, button -> {
            textPanel.show(panel, ModConfig.provider, "Provider", "Edit provider", tp -> {
                ModConfig.provider = ModTextPanel.textField;
                updateProviderLabel(providerLabel);
                ModConfig.save();
            }, tp -> {});
        });
        addElement(panel, providerEdit, rightY, providerEdit::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= row;

        ModLabel baseUrlLabel = new ModLabel("", rightX, rightY, panel, label -> {});
        updateBaseUrlLabel(baseUrlLabel);
        addElement(panel, baseUrlLabel, rightY, baseUrlLabel::setY);
        ModLabeledButton baseUrlEdit = new ModLabeledButton("Edit", rightButtonX, rightY, panel, button -> {
            textPanel.show(panel, ModConfig.baseUrl, "Base URL", "Edit baseUrl", tp -> {
                ModConfig.baseUrl = ModTextPanel.textField;
                updateBaseUrlLabel(baseUrlLabel);
                ModConfig.save();
            }, tp -> {});
        });
        addElement(panel, baseUrlEdit, rightY, baseUrlEdit::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= row;

        ModLabel modelLabel = new ModLabel("", rightX, rightY, panel, label -> {});
        updateModelLabel(modelLabel);
        addElement(panel, modelLabel, rightY, modelLabel::setY);
        ModLabeledButton modelEdit = new ModLabeledButton("Edit", rightButtonX, rightY, panel, button -> {
            textPanel.show(panel, ModConfig.model, "Model", "Edit model", tp -> {
                ModConfig.model = ModTextPanel.textField;
                updateModelLabel(modelLabel);
                ModConfig.save();
            }, tp -> {});
        });
        addElement(panel, modelEdit, rightY, modelEdit::setY);
        contentMinY = Math.min(contentMinY, rightY);
        rightY -= row;

        ModLabel apiKeyLabel = new ModLabel("", rightX, rightY, panel, label -> {});
        updateApiKeyLabel(apiKeyLabel);
        addElement(panel, apiKeyLabel, rightY, apiKeyLabel::setY);
        ModLabeledButton apiKeyEdit = new ModLabeledButton("Edit", rightButtonX, rightY, panel, button -> {
            textPanel.show(panel, ModConfig.apiKey, "API Key", "Edit apiKey", tp -> {
                ModConfig.apiKey = ModTextPanel.textField;
                updateApiKeyLabel(apiKeyLabel);
                ModConfig.save();
            }, tp -> {});
        });
        addElement(panel, apiKeyEdit, rightY, apiKeyEdit::setY);
        contentMinY = Math.min(contentMinY, rightY);

        float leftY = blockTop - row;
        ModMinMaxSlider tempSlider = new ModMinMaxSlider(
                "temperature",
                leftX,
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
        leftY -= row;

        ModMinMaxSlider maxTokensSlider = new ModMinMaxSlider(
                "maxTokens",
                leftX,
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
        leftY -= row;

        ModMinMaxSlider timeoutSlider = new ModMinMaxSlider(
                "timeoutMs",
                leftX,
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
        leftY -= row;

        ModLabel hotkeyLabel = new ModLabel("Refresh hotkey: F8", leftX, leftY, panel, label -> {});
        addElement(panel, hotkeyLabel, leftY, hotkeyLabel::setY);
        contentMinY = Math.min(contentMinY, leftY);

        viewTop = contentTop;
        viewBottom = contentBottom;
        scrollOffset = 0f;
        updateScrollBounds(contentMinY, contentMaxY);
        applyScroll();

        settingsPanel.setClipRect(contentLeft, contentBottom, contentWidth, contentTop - contentBottom);
    }

    private static void updateProviderLabel(ModLabel label) {
        label.text = "provider: " + ellipsize(safe(ModConfig.provider), 28);
    }

    private static void updateBaseUrlLabel(ModLabel label) {
        label.text = "baseUrl: " + ellipsize(safe(ModConfig.baseUrl), 36);
    }

    private static void updateModelLabel(ModLabel label) {
        label.text = "model: " + ellipsize(safe(ModConfig.model), 28);
    }

    private static void updateApiKeyLabel(ModLabel label) {
        label.text = "apiKey: " + (ModConfig.apiKey == null || ModConfig.apiKey.isEmpty() ? "(empty)" : "(set)");
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
        if (!isMouseOverPanel()) {
            return;
        }

        float step = 32f * Settings.scale;
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
