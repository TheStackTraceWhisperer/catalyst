package catalyst.client.application.ui;

import imgui.ImGui;
import imgui.type.ImBoolean;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class DebugLogPanel {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final List<String> lines = new ArrayList<>();
    private final ImBoolean autoScroll = new ImBoolean(true);

    public void log(String message) {
        lines.add("[" + LocalTime.now().format(FMT) + "] " + message);
    }

    public void render() {
        ImGui.setNextWindowPos(800, 20, imgui.flag.ImGuiCond.Once);
        ImGui.setNextWindowSize(460, 680, imgui.flag.ImGuiCond.Once);
        ImGui.begin("Debug Log");
        ImGui.checkbox("Auto scroll", autoScroll);
        ImGui.sameLine();
        if (ImGui.button("Clear")) lines.clear();
        ImGui.separator();
        ImGui.beginChild("log-scroll");
        for (String line : lines) ImGui.textUnformatted(line);
        if (autoScroll.get()) ImGui.setScrollHereY(1f);
        ImGui.endChild();
        ImGui.end();
    }
}
