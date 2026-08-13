package catalyst.client.application.ui;

import catalyst.client.application.ClientState;
import catalyst.common.dto.lobby.CharacterSummary;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;

import java.util.List;

@Prototype
public class CharacterPanel {

    private static final String[] RACE_LABELS   = {"Hume Male","Hume Female","Elvaan Male","Elvaan Female","Tarutaru Male","Tarutaru Female","Mithra","Galka"};
    private static final String[] SIZE_LABELS   = {"Small","Medium","Large"};
    private static final String[] JOB_LABELS    = {"Warrior","Monk","White Mage","Black Mage","Red Mage","Thief"};
    private static final String[] NATION_LABELS = {"San d'Oria","Bastok","Windurst"};
    private static final int[]    FORCED_SIZE   = {-1,-1,-1,-1,0,0,-1,2};

    // Create form
    private boolean showCreateForm = false;
    private final ImString newName   = new ImString("", 32);
    private final ImInt    raceIdx   = new ImInt(0);
    private final ImInt    sizeIdx   = new ImInt(1);
    private final ImInt    faceNum   = new ImInt(1);
    private final ImBoolean faceB    = new ImBoolean(false);
    private final ImInt    jobIdx    = new ImInt(0);
    private final ImInt    nationIdx = new ImInt(0);

    // Intents
    @Getter private boolean createSubmitted;
    @Getter private boolean refreshRequested;
    @Getter private boolean signOutRequested;
    @Getter private Long    selectCharacterId;
    @Getter private Long    deleteCharacterId;
    @Getter private boolean playRequested;

    public void render(ClientState clientState) {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(760, 560, ImGuiCond.Once);
        ImGui.begin("Character Select");

        if (clientState.getLastErrorMessage() != null) {
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Error: " + clientState.getLastErrorMessage());
        }

        if (ImGui.button("Refresh"))  refreshRequested = true;
        ImGui.sameLine();
        if (ImGui.button("Sign Out")) signOutRequested = true;

        ImGui.separator();
        if (!showCreateForm) {
            if (ImGui.button("Create Character")) showCreateForm = true;
        } else {
            renderCreateForm();
        }

        ImGui.separator();
        ImGui.text("Characters:");

        List<CharacterSummary> characters = clientState.getCharacterList();
        if (characters.isEmpty()) {
            ImGui.textDisabled("No characters found.");
        } else {
            for (CharacterSummary c : characters) {
                String raceLabel = c.raceId() >= 1 && c.raceId() <= RACE_LABELS.length ? RACE_LABELS[c.raceId() - 1] : "Unknown";
                String jobLabel  = c.mainJobId() >= 1 && c.mainJobId() <= JOB_LABELS.length ? JOB_LABELS[c.mainJobId() - 1] : "Job " + c.mainJobId();

                ImGui.text(String.format("%s  %s  Lvl %d  %s  Zone: %d", c.name(), raceLabel, c.mainJobLevel(), jobLabel, c.zoneId()));
                ImGui.sameLine();

                if (ImGui.smallButton("Select##" + c.characterId())) selectCharacterId = c.characterId();
                ImGui.sameLine();
                if (ImGui.smallButton("Delete##" + c.characterId())) deleteCharacterId = c.characterId();
            }
        }

        Long selectedId = clientState.getSelectedCharacterId();
        if (selectedId != null) {
            ImGui.separator();
            if (ImGui.button("Play")) playRequested = true;
            ImGui.sameLine();
            ImGui.text("Ready (ID=" + selectedId + ")");
        }
        ImGui.end();
    }

    private void renderCreateForm() {
        ImGui.text("New Character");
        ImGui.inputText("Name", newName);
        ImGui.combo("Race", raceIdx, RACE_LABELS);
        int forced = FORCED_SIZE[raceIdx.get()];
        if (forced >= 0) {
            sizeIdx.set(forced);
            ImGui.text("Size: " + SIZE_LABELS[forced] + " (fixed)");
        } else {
            ImGui.combo("Size", sizeIdx, SIZE_LABELS);
        }
        ImGui.inputInt("Face (1-8)", faceNum);
        ImGui.sameLine();
        ImGui.checkbox("Variant B", faceB);
        ImGui.combo("Job", jobIdx, JOB_LABELS);
        ImGui.combo("Nation", nationIdx, NATION_LABELS);
        if (ImGui.button("Create"))  createSubmitted = true;
        ImGui.sameLine();
        if (ImGui.button("Cancel"))  showCreateForm = false;
    }

    // Create form accessors
    public String getNewName()   { return newName.get(); }
    public int    getRaceId()    { return raceIdx.get() + 1; }
    public int    getSizeId()    { return sizeIdx.get(); }
    public int    getFaceId()    { return Math.clamp(faceNum.get(), 1, 8) - 1 + (faceB.get() ? 8 : 0); }
    public int    getJobId()     { return jobIdx.get() + 1; }
    public String getNationName(){ return NATION_LABELS[nationIdx.get()]; }

    public void clearIntents() {
        createSubmitted = false; refreshRequested = false; signOutRequested = false;
        selectCharacterId = null; deleteCharacterId = null; playRequested = false;
    }
    public void hideCreateForm() { showCreateForm = false; newName.set(""); }
}