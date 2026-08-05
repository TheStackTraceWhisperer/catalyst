package catalyst.client.application.ui;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;

import java.util.ArrayList;
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
    @Getter private String  selectCharacterId;
    @Getter private String  deleteCharacterId;
    @Getter private boolean playRequested;

    // Data
    private List<CharRow> characters = new ArrayList<>();
    private String selectedId = null;
    private String selectedName = null;
    private String statusMessage = "";

    public record CharRow(String id, String name, String raceName, int size, int face, String jobName, String nationName) {}

    public void setCharacters(List<CharRow> rows) { this.characters = rows; }
    public void setSelectedCharacter(String id, String name) { this.selectedId = id; this.selectedName = name; }
    public void setStatus(String msg) { this.statusMessage = msg; }

    public void render() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(760, 560, ImGuiCond.Once);
        ImGui.begin("Character Select");

        ImGui.text(statusMessage);
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
        if (characters.isEmpty()) {
            ImGui.textDisabled("No characters.");
        } else {
            for (CharRow rc : characters) {
                ImGui.text(rc.name() + "  " + rc.raceName() + "  size=" + rc.size() + "  face=" + rc.face()
                    + "  " + rc.jobName() + "  " + rc.nationName());
                ImGui.sameLine();
                if (ImGui.smallButton("Select##" + rc.id())) selectCharacterId = rc.id();
                ImGui.sameLine();
                if (ImGui.smallButton("Delete##" + rc.id())) deleteCharacterId = rc.id();
            }
        }

        if (selectedId != null) {
            ImGui.separator();
            if (ImGui.button("Play")) playRequested = true;
            ImGui.sameLine();
            ImGui.text("Ready: " + selectedName);
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
    public String getNewName()  { return newName.get(); }
    public int    getRaceId()   { return raceIdx.get() + 1; }
    public int    getSizeId()   { return sizeIdx.get(); }
    public int    getFaceId()   { return Math.clamp(faceNum.get(), 1, 8) - 1 + (faceB.get() ? 8 : 0); }
    public int    getJobId()    { return jobIdx.get() + 1; }
    public int    getNationId() { return nationIdx.get(); }

    public void clearIntents() {
        createSubmitted = false; refreshRequested = false; signOutRequested = false;
        selectCharacterId = null; deleteCharacterId = null; playRequested = false;
    }
    public void hideCreateForm()  { showCreateForm = false; newName.set(""); }
}
