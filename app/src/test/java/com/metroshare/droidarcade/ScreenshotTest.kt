package com.metroshare.droidarcade

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import com.metroshare.droidarcade.game.Gem
import com.metroshare.droidarcade.game.GemDropGame
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.scenes.GameScene
import com.metroshare.droidarcade.scenes.HowToScene
import com.metroshare.droidarcade.scenes.NameEntryScene
import com.metroshare.droidarcade.scenes.ScoresScene
import com.metroshare.droidarcade.scenes.SettingsScene
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders each screen to a PNG with Robolectric's native canvas so the look can be reviewed
 * without a device. Run with: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 * Output: app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {
    private val w = 1080
    private val h = 2340
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun newApp(): ArcadeApp {
        val app = ArcadeApp(RuntimeEnvironment.getApplication()) {}
        app.settings.musicVolume = 0
        val font = File("src/main/assets/fonts/PressStart2P-Regular.ttf")
        if (font.exists()) Gfx.font = Typeface.createFromFile(font)
        app.resize(w, h)
        app.setInsets(90, 40)
        return app
    }

    private fun step(app: ArcadeApp, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            app.update(1f / 60f)
            t += 1f / 60f
        }
    }

    private fun shot(app: ArcadeApp, name: String) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        app.render(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(app: ArcadeApp, s: Scene) {
        app.go(s)
        step(app, 0.6f)
    }

    private fun gameOf(scene: GameScene) =
        GameScene::class.java.getDeclaredField("game").apply { isAccessible = true }.get(scene) as GemDropGame

    @Test
    fun title() {
        val app = newApp()
        step(app, 1.3f)
        shot(app, "01_title")
    }

    @Test
    fun gameplay() {
        val app = newApp()
        val scene = GameScene(app)
        show(app, scene)
        val game = gameOf(scene)
        step(app, GemDropGame.READY_TIME + 0.1f)
        val b = game.board
        val pattern = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5),
            intArrayOf(3, 3, 1, 0, 2, 4),
            intArrayOf(5, 2, 4, 1, 0, 2),
            intArrayOf(1, -1, 0, 2, 5, 3),
            intArrayOf(-1, -1, 3, 5, -1, 1),
            intArrayOf(-1, -1, -1, 4, -1, -1),
        )
        for ((k, row) in pattern.withIndex()) {
            for (c in 0 until 6) if (row[c] >= 0) b[c, b.rows - 1 - k] = row[c]
        }
        step(app, 1.6f)
        shot(app, "02_game")
        val p = game.piece!!
        for (k in 0 until 3) p.gems[k] = 3
        game.hardDrop()
        step(app, 0.2f)
        shot(app, "03_game_flash")
        step(app, 0.3f)
        shot(app, "04_game_burst")
    }

    @Test
    fun gameOver() {
        val app = newApp()
        val scene = GameScene(app)
        show(app, scene)
        val game = gameOf(scene)
        var n = 0
        while (!game.isOver && n++ < 2000) {
            step(app, 0.05f)
            if (game.phase == GemDropGame.Phase.FALLING) game.hardDrop()
        }
        step(app, 3f)
        shot(app, "05_gameover")
    }

    @Test
    fun menus() {
        val app = newApp()
        show(app, SettingsScene(app))
        shot(app, "06_settings")
        show(app, ScoresScene(app, 2))
        shot(app, "07_scores")
        show(app, NameEntryScene(app, 31337, 9, 'N', 4))
        shot(app, "08_name_entry")
        show(app, HowToScene(app))
        shot(app, "09_how_to_play")
    }

    @Test
    fun gems() {
        val app = newApp()
        val bmp = Bitmap.createBitmap(1040, 360, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0A0018.toInt())
        for (g in 0 until Gem.MAX_COLORS) app.sprites.draw(c, g, 90f + g * 170f, 90f, 160f, 0f)
        for (k in 0 until 6) app.sprites.draw(c, Gem.MAGIC, 90f + k * 170f, 270f, 160f, k / 14f)
        FileOutputStream(File(out, "00_gems.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
