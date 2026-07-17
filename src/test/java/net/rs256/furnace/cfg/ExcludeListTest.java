package net.rs256.furnace.cfg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExcludeListTest {

    private final ExcludeList excludes =
            ExcludeList.fromLines(
                    List.of(
                            "# comment",
                            "",
                            "**/*.png",
                            "**/*.ogg",
                            "**/*.nbt",
                            "assets/*/textures/**",
                            "assets/*/lang/**",
                            "!assets/*/lang/en_us.json",
                            "**/.mcassetsroot"));

    @Test
    void excludesBinaryExtensionsAnywhere() {
        assertTrue(excludes.isExcluded("assets/minecraft/textures/block/stone.png"));
        assertTrue(excludes.isExcluded("pack.png"));
        assertTrue(excludes.isExcluded("assets/minecraft/sounds/ambient/cave/cave1.ogg"));
        assertTrue(excludes.isExcluded("data/minecraft/structure/igloo/top.nbt"));
    }

    @Test
    void excludesWholeTrees() {
        assertTrue(excludes.isExcluded("assets/minecraft/textures/entity/bee/bee.png"));
        assertTrue(excludes.isExcluded("assets/realms/textures/gui/realms/off_icon.png"));
    }

    @Test
    void languageNegationKeepsOnlyEnUs() {
        assertTrue(excludes.isExcluded("assets/minecraft/lang/ja_jp.json"));
        assertTrue(excludes.isExcluded("assets/minecraft/lang/deprecated.json"));
        assertFalse(excludes.isExcluded("assets/minecraft/lang/en_us.json"));
    }

    @Test
    void keepsDiffableText() {
        assertFalse(excludes.isExcluded("data/minecraft/loot_table/blocks/stone.json"));
        assertFalse(excludes.isExcluded("assets/minecraft/blockstates/stone.json"));
        assertFalse(excludes.isExcluded("assets/minecraft/models/block/stone.json"));
        assertFalse(excludes.isExcluded("version.json"));
    }

    @Test
    void matchesDotfilesAtAnyDepth() {
        assertTrue(excludes.isExcluded(".mcassetsroot"));
        assertTrue(excludes.isExcluded("data/.mcassetsroot"));
        assertTrue(excludes.isExcluded("assets/.mcassetsroot"));
    }
}
