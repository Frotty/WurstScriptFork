package de.peeeq.wurstio.languageserver;

import com.google.common.io.Files;
import config.*;
import de.peeeq.wurstio.languageserver.requests.MapRequest;
import de.peeeq.wurstio.languageserver.requests.RequestFailedException;
import de.peeeq.wurstio.mpq.MpqEditor;
import de.peeeq.wurstio.mpq.MpqEditorFactory;
import de.peeeq.wurstio.utils.W3InstallationData;
import de.peeeq.wurstscript.RunArgs;
import net.moonlightflower.wc3libs.bin.app.MapFlag;
import net.moonlightflower.wc3libs.bin.app.MapHeader;
import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.bin.app.W3I.Force;
import net.moonlightflower.wc3libs.bin.app.W3I.Player;
import net.moonlightflower.wc3libs.dataTypes.app.Controller;
import net.moonlightflower.wc3libs.dataTypes.app.LoadingScreenBackground;
import net.moonlightflower.wc3libs.port.GameVersion;
import org.apache.commons.lang.StringUtils;
import org.eclipse.lsp4j.MessageType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectConfigBuilder {
    public static final String FILE_NAME = "wurst.build";

    public static MapRequest.CompilationResult apply(WurstProjectConfigData projectConfig, File targetMap, File compiledScript, File buildDir,
                                                     RunArgs runArgs, W3InstallationData w3data) throws IOException {
        if (projectConfig.getProjectName().isEmpty()) {
            throw new RequestFailedException(MessageType.Error, "wurst.build is missing projectName.");
        }

        MapRequest.CompilationResult result = new MapRequest.CompilationResult();
        result.script = compiledScript;
        try (MpqEditor mpq = MpqEditorFactory.getEditor(Optional.of(targetMap), true)) {
            byte[] scriptBytes;
            if (!projectConfig.getBuildMapData().getName().isEmpty()) {
                result.script = new File(buildDir, "war3mapj_with_config.j.txt");
                // Apply w3i config values
                W3I w3I = prepareW3I(projectConfig, mpq);
                FileInputStream inputStream = new FileInputStream(compiledScript);
                StringWriter sw = new StringWriter();

                if (runArgs.isLua()) {
                    w3I.injectConfigsInLuaScript(inputStream, sw);
                } else {
                    if (w3data.getWc3PatchVersion().isPresent()) {
                        w3I.injectConfigsInJassScript(inputStream, sw, w3data.getWc3PatchVersion().get());
                    } else {
                        GameVersion version = GameVersion.VERSION_1_32;
                        System.out.println(
                            "Failed to determine installed game version. Falling back to " + version
                        );
                        w3I.injectConfigsInJassScript(inputStream, sw, version);
                    }
                }
                scriptBytes = sw.toString().getBytes(StandardCharsets.UTF_8);

                result.w3i = new File(buildDir, "war3map.w3i");
                if (runArgs.isLua()) {
                    w3I.setScriptLang(W3I.ScriptLang.LUA);
                    w3I.setFileVersion(W3I.EncodingFormat.W3I_0x1F.getVersion());
                }
                w3I.write(result.w3i);
                Files.write(scriptBytes, result.script);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        applyMapHeader(projectConfig, targetMap);

        return result;
    }

    private static W3I prepareW3I(WurstProjectConfigData projectConfig, MpqEditor mpq) throws Exception {
        W3I w3I = new W3I(mpq.extractFile("war3map.w3i"));
        WurstProjectBuildMapData buildMapData = projectConfig.getBuildMapData();
        if (StringUtils.isNotBlank(buildMapData.getName())) {
            w3I.setMapName(buildMapData.getName());
        }
        if (StringUtils.isNotBlank(buildMapData.getAuthor())) {
            w3I.setMapAuthor(buildMapData.getAuthor());
        }
        applyScenarioData(w3I, buildMapData);

        if (!buildMapData.getPlayers().isEmpty()) {
            applyPlayers(projectConfig, w3I);
        }
        if (!buildMapData.getForces().isEmpty()) {
            applyForces(projectConfig, w3I);
        }
        applyOptionFlags(projectConfig, w3I);

        return w3I;
    }

    private static void applyOptionFlags(WurstProjectConfigData projectConfig, W3I w3I) {
        WurstProjectBuildOptionFlagsData optionsFlags = projectConfig.getBuildMapData().getOptionsFlags();
        w3I.setFlag(MapFlag.HIDE_MINIMAP, optionsFlags.getForcesFixed() || w3I.getFlag(MapFlag.HIDE_MINIMAP));
        w3I.setFlag(MapFlag.FIXED_PLAYER_FORCE_SETTING, optionsFlags.getForcesFixed() || w3I.getFlag(MapFlag.FIXED_PLAYER_FORCE_SETTING));
        w3I.setFlag(MapFlag.MASKED_AREAS_PARTIALLY_VISIBLE, optionsFlags.getForcesFixed() || w3I.getFlag(MapFlag.MASKED_AREAS_PARTIALLY_VISIBLE));
        w3I.setFlag(MapFlag.SHOW_WATER_WAVES_ON_CLIFF_SHORES, optionsFlags.getShowWavesOnCliffShores() || w3I.getFlag(MapFlag.SHOW_WATER_WAVES_ON_CLIFF_SHORES));
        w3I.setFlag(MapFlag.SHOW_WATER_WAVES_ON_ROLLING_SHORES, optionsFlags.getShowWavesOnRollingShores() || w3I.getFlag(MapFlag.SHOW_WATER_WAVES_ON_ROLLING_SHORES));
    }

    private static void applyScenarioData(W3I w3I, WurstProjectBuildMapData buildMapData) {
        WurstProjectBuildScenarioData scenarioData = buildMapData.getScenarioData();
        if (StringUtils.isNotBlank(scenarioData.getSuggestedPlayers())) {
            w3I.setPlayersRecommendedAmount(scenarioData.getSuggestedPlayers());
        }
        if (StringUtils.isNotBlank(scenarioData.getDescription())) {
            w3I.setMapDescription(scenarioData.getDescription());
        }
        if (scenarioData.getLoadingScreen() != null) {
            applyLoadingScreen(w3I, scenarioData.getLoadingScreen());
        }
    }

    private static void applyLoadingScreen(W3I w3I, WurstProjectBuildLoadingScreenData loadingScreenData) {
        if (StringUtils.isNotBlank(loadingScreenData.getModel())) {
            w3I.getLoadingScreen().setBackground(new LoadingScreenBackground.CustomBackground(new File(loadingScreenData.getModel())));
        } else if (StringUtils.isNotBlank(loadingScreenData.getBackground())) {
            w3I.getLoadingScreen().setBackground(LoadingScreenBackground.PresetBackground.findByName(loadingScreenData.getBackground()));
        }

        w3I.getLoadingScreen().setTitle(loadingScreenData.getTitle());
        w3I.getLoadingScreen().setSubtitle(loadingScreenData.getSubTitle());
        w3I.getLoadingScreen().setText(loadingScreenData.getText());
    }

    private static void applyForces(WurstProjectConfigData projectConfig, W3I w3I) {
        w3I.clearForces();
        ArrayList<WurstProjectBuildForce> forces = projectConfig.getBuildMapData().getForces();
        for (WurstProjectBuildForce wforce : forces) {
            W3I.Force force = new Force();
            force.setName(wforce.getName());
            force.setFlag(W3I.Force.Flags.Flag.ALLIED, wforce.getFlags().getAllied());
            force.setFlag(W3I.Force.Flags.Flag.ALLIED_VICTORY, wforce.getFlags().getAlliedVictory());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_VISION, wforce.getFlags().getSharedVision());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_UNIT_CONTROL, wforce.getFlags().getSharedControl());
            force.setFlag(W3I.Force.Flags.Flag.SHARED_UNIT_CONTROL_ADVANCED, wforce.getFlags().getSharedControlAdvanced());
            force.addPlayerNums(wforce.getPlayerIds());
            w3I.addForce(force);
        }
        w3I.setFlag(MapFlag.USE_CUSTOM_FORCES, true);
    }

    private static void applyPlayers(WurstProjectConfigData projectConfig, W3I w3I) {
        List<W3I.Player> existing = new ArrayList<>(w3I.getPlayers());
        w3I.getPlayers().clear();
        ArrayList<WurstProjectBuildPlayer> players = projectConfig.getBuildMapData().getPlayers();
        for (WurstProjectBuildPlayer wplayer : players) {
            Optional<W3I.Player> old = existing.stream().filter(player -> player.getNum() == wplayer.getId()).findFirst();
            W3I.Player player = new Player();
            player.setNum(wplayer.getId());
            w3I.addPlayer(player);

            old.ifPresent(player1 -> applyExistingPlayerConfig(player1, player));

            setVolatilePlayerConfig(wplayer, player);
        }
    }

    private static void applyExistingPlayerConfig(W3I.Player oldPlayer, W3I.Player player) {
        player.setStartPos(oldPlayer.getStartPos());
        player.setName(oldPlayer.getName());
        player.setRace(oldPlayer.getRace());
        player.setType(oldPlayer.getType());
        player.setStartPosFixed(oldPlayer.getStartPosFixed());
        player.setAllyLowPrioFlags(oldPlayer.getAllyLowPrioFlags());
        player.setAllyHighPrioFlags(oldPlayer.getAllyHighPrioFlags());
    }

    private static void setVolatilePlayerConfig(WurstProjectBuildPlayer wplayer, W3I.Player player) {
        if (wplayer.getName() != null) {
            player.setName(wplayer.getName());
        }

        if (wplayer.getRace() != null) {
            W3I.Player.UnitRace val = W3I.Player.UnitRace.valueOf(wplayer.getRace().toString());
            if (val != null) {
                player.setRace(val);
            }
        }
        if (wplayer.getController() != null) {
            net.moonlightflower.wc3libs.dataTypes.app.Controller val1 = Controller.valueOf(wplayer.getController().toString());
            if (val1 != null) {
                player.setType(val1);
            }
        }
        if (wplayer.getFixedStartLoc() != null) {
            player.setStartPosFixed(wplayer.getFixedStartLoc() ? 1 : 0);
        }
    }

    private static void applyMapHeader(WurstProjectConfigData projectConfig, File targetMap) throws IOException {
        MapHeader mapHeader = MapHeader.ofFile(targetMap);
        if (projectConfig.getBuildMapData().getPlayers().size() > 0) {
            mapHeader.setMaxPlayersCount(projectConfig.getBuildMapData().getPlayers().size());
        }
        if (StringUtils.isNotBlank(projectConfig.getBuildMapData().getName())) {
            mapHeader.setMapName(projectConfig.getBuildMapData().getName());
        }
        mapHeader.writeToMapFile(targetMap);
    }
}
