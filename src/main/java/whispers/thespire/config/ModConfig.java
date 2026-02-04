package whispers.thespire.config;

import com.badlogic.gdx.Input;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;

import java.util.Properties;

public class ModConfig {
    private static final String MOD_ID = "whispersthespire";
    private static final String CONFIG_NAME = "WhispersTheSpireConfig";

    public static boolean overlayEnabled = true;
    public static boolean autoTriggersEnabled = false;
    public static boolean combatAdviceEnabled = false;
    public static boolean debugShowSnapshot = false;
    public static boolean debugShowRawResponse = false;
    public static boolean showReasons = true;
    public static boolean multiRecommendations = false;
    public static boolean useKnowledgeBase = true;
    public static String provider = "openai_compat";
    public static String baseUrl = "https://api.openai.com/v1";
    public static String model = "gpt-4o-mini";
    public static float temperature = 0.2f;
    public static int maxTokens = 512;
    public static int timeoutMs = 12000;
    public static String apiKey = "";
    public static int hotkeyRefresh = Input.Keys.F8;
    public static int hotkeyToggleOverlay = Input.Keys.F9;
    public static String language = "zh";

    public static float panelX = -1f;
    public static float panelY = -1f;
    public static float panelW = -1f;
    public static float panelH = -1f;

    private static SpireConfig config;

    public static void load() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty("overlayEnabled", Boolean.toString(overlayEnabled));
            defaults.setProperty("autoTriggersEnabled", Boolean.toString(autoTriggersEnabled));
            defaults.setProperty("combatAdviceEnabled", Boolean.toString(combatAdviceEnabled));
            defaults.setProperty("debugShowSnapshot", Boolean.toString(debugShowSnapshot));
            defaults.setProperty("debugShowRawResponse", Boolean.toString(debugShowRawResponse));
            defaults.setProperty("showReasons", Boolean.toString(showReasons));
            defaults.setProperty("multiRecommendations", Boolean.toString(multiRecommendations));
            defaults.setProperty("useKnowledgeBase", Boolean.toString(useKnowledgeBase));
            defaults.setProperty("provider", provider);
            defaults.setProperty("baseUrl", baseUrl);
            defaults.setProperty("model", model);
            defaults.setProperty("temperature", Float.toString(temperature));
            defaults.setProperty("maxTokens", Integer.toString(maxTokens));
            defaults.setProperty("timeoutMs", Integer.toString(timeoutMs));
            defaults.setProperty("apiKey", apiKey);
            defaults.setProperty("hotkeyRefresh", Integer.toString(hotkeyRefresh));
            defaults.setProperty("hotkeyToggleOverlay", Integer.toString(hotkeyToggleOverlay));
            defaults.setProperty("language", language);
            defaults.setProperty("panelX", Float.toString(panelX));
            defaults.setProperty("panelY", Float.toString(panelY));
            defaults.setProperty("panelW", Float.toString(panelW));
            defaults.setProperty("panelH", Float.toString(panelH));

            config = new SpireConfig(MOD_ID, CONFIG_NAME, defaults);
            config.load();

            overlayEnabled = config.getBool("overlayEnabled");
            autoTriggersEnabled = config.getBool("autoTriggersEnabled");
            combatAdviceEnabled = config.getBool("combatAdviceEnabled");
            debugShowSnapshot = config.getBool("debugShowSnapshot");
            debugShowRawResponse = config.getBool("debugShowRawResponse");
            showReasons = config.getBool("showReasons");
            multiRecommendations = config.getBool("multiRecommendations");
            useKnowledgeBase = config.getBool("useKnowledgeBase");
            provider = config.getString("provider");
            baseUrl = config.getString("baseUrl");
            model = config.getString("model");
            temperature = config.getFloat("temperature");
            maxTokens = config.getInt("maxTokens");
            timeoutMs = config.getInt("timeoutMs");
            apiKey = config.getString("apiKey");
            hotkeyRefresh = config.getInt("hotkeyRefresh");
            hotkeyToggleOverlay = config.getInt("hotkeyToggleOverlay");
            language = config.getString("language");
            panelX = config.getFloat("panelX");
            panelY = config.getFloat("panelY");
            panelW = config.getFloat("panelW");
            panelH = config.getFloat("panelH");
        } catch (Exception e) {
            System.err.println("WhispersTheSpire: failed to load config, using defaults. " + e.getMessage());
        }
    }

    public static void save() {
        if (config == null) {
            try {
                config = new SpireConfig(MOD_ID, CONFIG_NAME);
            } catch (Exception e) {
                System.err.println("WhispersTheSpire: failed to open config for save. " + e.getMessage());
                return;
            }
        }

        try {
            config.setBool("overlayEnabled", overlayEnabled);
            config.setBool("autoTriggersEnabled", autoTriggersEnabled);
            config.setBool("combatAdviceEnabled", combatAdviceEnabled);
            config.setBool("debugShowSnapshot", debugShowSnapshot);
            config.setBool("debugShowRawResponse", debugShowRawResponse);
            config.setBool("showReasons", showReasons);
            config.setBool("multiRecommendations", multiRecommendations);
            config.setBool("useKnowledgeBase", useKnowledgeBase);
            config.setString("provider", safe(provider));
            config.setString("baseUrl", safe(baseUrl));
            config.setString("model", safe(model));
            config.setFloat("temperature", temperature);
            config.setInt("maxTokens", maxTokens);
            config.setInt("timeoutMs", timeoutMs);
            config.setString("apiKey", safe(apiKey));
            config.setInt("hotkeyRefresh", hotkeyRefresh);
            config.setInt("hotkeyToggleOverlay", hotkeyToggleOverlay);
            config.setString("language", safe(language));
            config.setFloat("panelX", panelX);
            config.setFloat("panelY", panelY);
            config.setFloat("panelW", panelW);
            config.setFloat("panelH", panelH);
            config.save();
        } catch (Exception e) {
            System.err.println("WhispersTheSpire: failed to save config (apiKey set: " + (apiKey != null && !apiKey.isEmpty()) + "). " + e.getMessage());
        }
    }

    public static void savePanel(float x, float y, float w, float h) {
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;
        save();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
