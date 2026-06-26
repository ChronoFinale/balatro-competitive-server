package com.balatro.engine.joker.def;

import com.balatro.grammar.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.engine.joker.JokerLibrary;
import com.balatro.grammar.Trigger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Persistence + validation for builder-authored jokers: a saved def round-trips
 * through disk, re-registers on reload (flowing through {@code create()}), and
 * the safety guards (key format, built-in collision, PNG sprites) hold.
 */
class CustomJokerStoreTest {

    private static JokerDef def(String key) {
        return new JokerDef(key, "Custom " + key, "+9 Mult", com.balatro.grammar.Rarity.COMMON, 4, 0, 0, null, null, true,
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                        Effect.mult(new Value.Const(9)))));
    }

    @Test
    void savesReloadsAndRegisters(@TempDir Path dir) {
        new CustomJokerStore(dir).save(def("j_custom_alpha"));

        // a fresh store over the same directory rediscovers and re-registers it
        CustomJokerStore reopened = new CustomJokerStore(dir);
        reopened.loadAll();
        assertThat(reopened.get("j_custom_alpha")).isNotNull();
        assertThat(reopened.get("j_custom_alpha").name()).isEqualTo("Custom j_custom_alpha");
        assertThat(JokerLibrary.create("j_custom_alpha")).isInstanceOf(DataJoker.class);
    }

    @Test
    void rejectsBadKeysAndBuiltinCollisions(@TempDir Path dir) {
        CustomJokerStore store = new CustomJokerStore(dir);
        assertThatThrownBy(() -> store.save(def("nope"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save(def("j_joker"))) // a built-in key
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spriteUploadValidatesPngAndRecordsUrl(@TempDir Path dir) throws Exception {
        CustomJokerStore store = new CustomJokerStore(dir);
        store.save(def("j_custom_art"));

        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
        JokerDef updated = store.saveSprite("j_custom_art", 1, png);
        assertThat(updated.spriteUrl()).isEqualTo("/custom/j_custom_art.1x.png");
        assertThat(Files.exists(dir.resolve("j_custom_art.1x.png"))).isTrue();

        byte[] notPng = {1, 2, 3, 4};
        assertThatThrownBy(() -> store.saveSprite("j_custom_art", 2, notPng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveAssetIsTraversalSafe(@TempDir Path dir) {
        CustomJokerStore store = new CustomJokerStore(dir);
        assertThat(store.resolveAsset("../secret.txt")).isNull();
        assertThat(store.resolveAsset("ok.png")).isNotNull();
    }
}
