package whispers.thespire.skills;

import java.util.List;

public class SkillRecord {
    public String id;
    public String contextType;
    public String character;
    public String actionType;
    public String title;
    public String action;
    public String reason;
    public String summary;
    public long createdAt;
    public long lastUsedAt;
    public int uses;
    public int wins;
    public int losses;
    public float avgHpDelta;
    public String sourceHash;
    public List<String> tags;
    public int ascensionBracket = -1;
}
