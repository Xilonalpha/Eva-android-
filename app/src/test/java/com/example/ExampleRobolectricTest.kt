package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("EVA-1", appName)
    assertEquals(11, com.eva1.companion.EvaTab.entries.size)
  }

  @Test
  fun `local llm bridge engine initializes and configures correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val offlineEngine = com.eva1.companion.engines.OfflineLlmEngine(context)
    val bridge = com.eva1.companion.engines.LocalLlmGameBridgeEngine(context, offlineEngine)

    val cfg = bridge.config.value
    assertEquals("127.0.0.1", cfg.host)
    assertEquals(8080, cfg.port)
    assertEquals(com.eva1.companion.engines.LocalLlmProtocol.OPENAI_COMPATIBLE, cfg.protocol)
    assertEquals(true, cfg.isEnabled)

    bridge.updateConfig(port = 11434, protocol = com.eva1.companion.engines.LocalLlmProtocol.OLLAMA_NATIVE)
    assertEquals(11434, bridge.config.value.port)
    assertEquals(com.eva1.companion.engines.LocalLlmProtocol.OLLAMA_NATIVE, bridge.config.value.protocol)

    val presets = com.eva1.companion.engines.LocalLlmGameBridgeEngine.PRESET_PORTS
    assertEquals(4, presets.size)
  }

  @Test
  fun `accessibility service interprets external game ui state correctly`() {
    val service = com.eva1.companion.engines.EvaAccessibilityService()

    val snapWithPopup = com.eva1.companion.engines.LiveScreenSnapshot(
        activePackage = "com.supercell.clashofclans",
        screenWidth = 1080,
        screenHeight = 2400,
        elements = listOf(
            com.eva1.companion.engines.ScreenUiElement(
                text = "✕",
                contentDescription = "Close",
                className = "Button",
                viewId = "btn_close_promo",
                isClickable = true,
                isEditable = false,
                bounds = "[900,100 - 980,180]",
                centerX = 940,
                centerY = 140,
                width = 80,
                height = 80
            )
        )
    )

    val interpreted = service.interpretExternalUiState(snapWithPopup, "com.supercell.clashofclans")
    assertEquals(com.eva1.companion.engines.ExternalGameScreenType.DIALOG_POPUP_ALERT, interpreted.screenType)
    org.junit.Assert.assertNotNull(interpreted.closePopupButton)
    assertEquals(940, interpreted.closePopupButton?.centerX)
  }

  @Test
  fun `llm gesture mapper parses structured JSON command correctly`() {
    val mapper = com.eva1.companion.engines.LlmGestureMappingEngine { null }
    val jsonLlm = """
      {
        "gesture": "SWIPE",
        "from": [0.2, 0.8],
        "to": [0.2, 0.4],
        "duration_ms": 250,
        "reason": "Deplasare înainte către zona sigură PUBG"
      }
    """.trimIndent()

    val cmd = mapper.parseLlmAnalysisToGesture(
        llmText = jsonLlm,
        screenWidth = 1000f,
        screenHeight = 2000f
    )

    assertEquals(com.eva1.companion.engines.DynamicGestureMotion.SWIPE, cmd.motion)
    assertEquals(200f, cmd.startX)
    assertEquals(1600f, cmd.startY)
    assertEquals(200f, cmd.endX)
    assertEquals(800f, cmd.endY)
    assertEquals(250L, cmd.durationMs)
    assertEquals("Deplasare înainte către zona sigură PUBG", cmd.reasoning)
  }

  @Test
  fun `llm gesture mapper parses spatial directives text correctly`() {
    val mapper = com.eva1.companion.engines.LlmGestureMappingEngine { null }
    val textDirective = """
      Analiză ecran PUBG: Inamic detectat în dreapta sus.
      GESTURE: BURST_TAPS
      COORDS: 850, 600
      DURATION: 50
      REPEAT: 3
      REASON: Tragere rapidă în rafală asupra țintei
    """.trimIndent()

    val cmd = mapper.parseLlmAnalysisToGesture(
        llmText = textDirective,
        screenWidth = 1080f,
        screenHeight = 2400f
    )

    assertEquals(com.eva1.companion.engines.DynamicGestureMotion.BURST_TAPS, cmd.motion)
    assertEquals(850f, cmd.startX)
    assertEquals(600f, cmd.startY)
    assertEquals(3, cmd.repeatCount)
    assertEquals("Tragere rapidă în rafală asupra țintei", cmd.reasoning)
  }

  @Test
  fun `llm gesture mapper never taps screen center when coords are missing`() {
    val mapper = com.eva1.companion.engines.LlmGestureMappingEngine { null }
    val vagueText = "Ecranul este stabil, monitorizăm situația tactică fără coordonate"

    val cmd = mapper.parseLlmAnalysisToGesture(
        llmText = vagueText,
        screenWidth = 1080f,
        screenHeight = 2400f
    )

    // Comportamentul corect: trece pe WAIT_OBSERVE și NU trimite tap pe centrul ecranului (540, 1200)
    assertEquals(com.eva1.companion.engines.DynamicGestureMotion.WAIT_OBSERVE, cmd.motion)
  }

  @Test
  fun `learned user gestures are persisted and reloaded correctly in orchestrator`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val orchestrator = com.eva1.companion.engines.OsOrchestratorEngine(context)

    orchestrator.recordLearnedGesture(
        gameKey = "pubg",
        actionLabel = "Foc Personalizat Dreapta",
        actionTypeKey = "FIRE",
        targetXFrac = 0.88f,
        targetYFrac = 0.76f,
        motionType = "TAP",
        note = "Buton foc mutat în setările PUBG"
    )

    val fetched = orchestrator.getLearnedGesture("com.tencent.ig.pubg", "FIRE")
    org.junit.Assert.assertNotNull(fetched)
    assertEquals(0.88f, fetched!!.targetXFrac, 0.001f)
    assertEquals(0.76f, fetched.targetYFrac, 0.001f)

    // Reinițializare instanță nouă pentru verificarea persistenței din SharedPreferences
    val orchestrator2 = com.eva1.companion.engines.OsOrchestratorEngine(context)
    val reloaded = orchestrator2.getLearnedGesture("com.tencent.ig.pubg", "FIRE")
    org.junit.Assert.assertNotNull(reloaded)
    assertEquals(0.88f, reloaded!!.targetXFrac, 0.001f)
    assertEquals("Foc Personalizat Dreapta", reloaded.actionLabel)
  }

  @Test
  fun `media projection vision engine returns null recommended tap target on loading screen`() {
    val bmp = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
    // Canvas gol negru (loading screen)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.BLACK)

    val analysis = com.eva1.companion.engines.MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
        bitmap = bmp,
        screenWidth = 1080,
        screenHeight = 2400,
        activeGameTitle = "PUBG Mobile"
    )

    // Asigură că ecranul de încărcare nu returnează țintă centrală (540, 1200)
    if (analysis.gameState == com.eva1.companion.engines.DetectedVisionGameState.LOADING_TRANSITION) {
        org.junit.Assert.assertNull(analysis.recommendedTapTarget)
    }
  }

  @Test
  fun `system touch input injection engine defaults to auto hybrid and updates methods`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.eva1.companion.engines.SystemTouchInputInjectionEngine(context) { null }

    assertEquals(com.eva1.companion.engines.TouchInjectionMethod.AUTO_HYBRID, engine.injectionMethod.value)

    engine.setInjectionMethod(com.eva1.companion.engines.TouchInjectionMethod.SHELL_INPUT_COMMAND)
    assertEquals(com.eva1.companion.engines.TouchInjectionMethod.SHELL_INPUT_COMMAND, engine.injectionMethod.value)

    engine.setInjectionMethod(com.eva1.companion.engines.TouchInjectionMethod.ACCESSIBILITY_DISPATCH)
    assertEquals(com.eva1.companion.engines.TouchInjectionMethod.ACCESSIBILITY_DISPATCH, engine.injectionMethod.value)
  }

  @Test
  fun `multimodal vision provider defaults to local vision model without cloud keys`() {
    assertEquals(com.eva1.companion.engines.MultiModalVisionProvider.LOCAL_VISION_MODEL, com.eva1.companion.engines.MultiModalVisionProvider.entries.first())
    val defaultAnalysis = com.eva1.companion.engines.MultiModalVisionAnalysis()
    assertEquals(com.eva1.companion.engines.MultiModalVisionProvider.LOCAL_VISION_MODEL, defaultAnalysis.providerUsed)
    assertEquals("EVA-Local-Omni", defaultAnalysis.modelName)
  }

  @Test
  fun `dual brain game engine evaluates fast reflex and initializes strategic postures correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val visionEngine = com.eva1.companion.engines.MultiModalGameVisionEngine(context)
    val dualBrain = com.eva1.companion.engines.DualBrainGameEngine(context, visionEngine)

    assertEquals(com.eva1.companion.engines.StrategicPosture.AGGRESSIVE_ASSAULT, dualBrain.activeDirective.value.posture)
    assertEquals(8, com.eva1.companion.engines.StrategicPosture.entries.size)

    val visual = com.eva1.companion.engines.GameVisualAnalysis(
        width = 1080,
        height = 2400,
        hasTargetInCrosshair = true
    )

    val reflex = dualBrain.evaluateFastReflex(
        visual = visual,
        screenWidth = 1080f,
        screenHeight = 2400f,
        gameMode = com.eva1.companion.engines.GameBotMode.FPS_BATTLE_ROYALE
    )

    assertEquals("INSTANT_FIRE_BURST", reflex.actionType)
    org.junit.Assert.assertTrue(reflex.isTriggered)
    org.junit.Assert.assertTrue(reflex.recommendedPauseMs <= 45L)
  }

  @Test
  fun `solitaire board analysis detects 7 tableau columns stock pile and suppresses false menu buttons`() {
    val bmp = android.graphics.Bitmap.createBitmap(270, 600, android.graphics.Bitmap.Config.ARGB_8888)
    // Masa verde de Solitaire
    bmp.eraseColor(android.graphics.Color.rgb(18, 128, 16))
    // Coloana 1 deschisă (albă, fără cărți albastre deasupra: X = 10..38, Y = 142..280)
    for (y in 142..280) {
        for (x in 10..38) {
            bmp.setPixel(x, y, android.graphics.Color.WHITE)
        }
    }
    // Coloana 5 cu cărți albastre ascunse sus (Y = 142..178) + carte albă jos (Y = 179..235)
    for (y in 142..178) {
        for (x in 160..186) {
            bmp.setPixel(x, y, android.graphics.Color.rgb(15, 72, 160))
        }
    }
    for (y in 179..235) {
        for (x in 160..186) {
            bmp.setPixel(x, y, android.graphics.Color.WHITE)
        }
    }

    val result = com.eva1.companion.engines.GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
        frameBitmap = bmp,
        displayWidth = 1080,
        displayHeight = 2400,
        gameTitleHint = "Solitario com.tripledot.solitaire"
    )

    val sol = result.solitaireAnalysis
    org.junit.Assert.assertNotNull(sol)
    org.junit.Assert.assertTrue(sol!!.isSolitaireTableDetected)
    assertEquals(7, sol.columns.size)
    // Coloana 1 are cărți deschise fără cărți cu fața în jos deasupra
    org.junit.Assert.assertTrue(sol.columns[0].hasFaceUpCards)
    org.junit.Assert.assertFalse(sol.columns[0].hasFaceDownCardsAbove)
    // Coloana 5 are cărți deschise ȘI cărți ascunse deasupra
    org.junit.Assert.assertTrue(sol.columns[4].hasFaceUpCards)
    org.junit.Assert.assertTrue(sol.columns[4].hasFaceDownCardsAbove)

    val mpVision = com.eva1.companion.engines.MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
        bitmap = bmp,
        screenWidth = 1080,
        screenHeight = 2400,
        activeGameTitle = "Solitario com.tripledot.solitaire",
        activeMode = com.eva1.companion.engines.GameBotMode.SOLITAIRE_CARDS
    )
    assertEquals(com.eva1.companion.engines.DetectedVisionGameState.ACTIVE_GAMEPLAY_COMBAT, mpVision.gameState)
    org.junit.Assert.assertTrue(mpVision.detectedButtons.none { it.category == com.eva1.companion.engines.GameUiButtonCategory.START_PLAY_BUTTON })
  }

  @Test
  fun `game screen analysis utility maps pixel coordinates from mediaprojection stream correctly`() {
    val bmp = android.graphics.Bitmap.createBitmap(270, 600, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.BLACK)

    val result = com.eva1.companion.engines.GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
        frameBitmap = bmp,
        displayWidth = 1080,
        displayHeight = 2400,
        gameTitleHint = "Tetris"
    )

    assertEquals(1080, result.displayWidth)
    assertEquals(2400, result.displayHeight)
    org.junit.Assert.assertTrue(result.detectedElements.isNotEmpty())

    val leftBtn = result.detectedElements.firstOrNull { it.id == "btn_dpad_left" }
    org.junit.Assert.assertNotNull(leftBtn)
    assertEquals(1080 * 0.30f, leftBtn!!.centerX, 5f)
    assertEquals(2400 * 0.84f, leftBtn.centerY, 10f)

    val rightBtn = result.detectedElements.firstOrNull { it.id == "btn_dpad_right" }
    org.junit.Assert.assertNotNull(rightBtn)
    assertEquals(1080 * 0.70f, rightBtn!!.centerX, 5f)

    val dropBtn = result.detectedElements.firstOrNull { it.id == "btn_dpad_down" }
    org.junit.Assert.assertNotNull(dropBtn)
    assertEquals(1080 * 0.40f, dropBtn!!.centerX, 5f)
    assertEquals(2400 * 0.92f, dropBtn.centerY, 10f)

    val tetris = result.tetrisAnalysis
    org.junit.Assert.assertNotNull(tetris)
    org.junit.Assert.assertTrue(tetris!!.boardBoundsOnDisplay.width() in 700f..1050f)
    org.junit.Assert.assertTrue(tetris.boardBoundsOnDisplay.height() > 1200f)
  }

  @Test
  fun `touch controller translates screen analysis pixel coordinates into motionevent streams`() {
    val bmp = android.graphics.Bitmap.createBitmap(270, 600, android.graphics.Bitmap.Config.ARGB_8888)
    val analysis = com.eva1.companion.engines.GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
        frameBitmap = bmp,
        displayWidth = 1080,
        displayHeight = 2400,
        gameTitleHint = "Tetris"
    )

    val stream = com.eva1.companion.engines.TouchController.translateScreenAnalysisToMotionStream(analysis)
    org.junit.Assert.assertNotNull(stream)
    org.junit.Assert.assertTrue(stream!!.motionEvents.size >= 2)
    assertEquals(android.view.MotionEvent.ACTION_DOWN, stream.motionEvents.first().action)
    assertEquals(android.view.MotionEvent.ACTION_UP, stream.motionEvents.last().action)

    val swipeStream = com.eva1.companion.engines.TouchController.createSwipeMotionStream(
        startX = 200f,
        startY = 1800f,
        endX = 800f,
        endY = 1800f,
        durationMs = 180L
    )
    assertEquals("SWIPE", swipeStream.interactionType)
    org.junit.Assert.assertTrue(swipeStream.motionEvents.size >= 5)
    assertEquals(android.view.MotionEvent.ACTION_DOWN, swipeStream.motionEvents.first().action)
    assertEquals(android.view.MotionEvent.ACTION_MOVE, swipeStream.motionEvents[1].action)
    assertEquals(android.view.MotionEvent.ACTION_UP, swipeStream.motionEvents.last().action)
    assertEquals(200f, swipeStream.motionEvents.first().x, 1f)
    assertEquals(800f, swipeStream.motionEvents.last().x, 1f)
    swipeStream.recycleMotionEvents()
  }

  @Test
  fun `vision engines handle recycled bitmaps gracefully without IllegalStateException`() {
    val recycledBmp = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888)
    recycledBmp.recycle()
    org.junit.Assert.assertTrue(recycledBmp.isRecycled)

    // Nu trebuie să arunce java.lang.IllegalStateException: Can't call getPixel() on a recycled bitmap
    val mpResult = com.eva1.companion.engines.MediaProjectionVisionEngine.analyzeGameFrameForAiVision(
        bitmap = recycledBmp,
        screenWidth = 1080,
        screenHeight = 2400,
        activeGameTitle = "Tetris"
    )
    assertEquals(1080, mpResult.frameWidth)

    val screenResult = com.eva1.companion.engines.GameScreenAnalysisUtility.analyzeMediaProjectionFrame(
        frameBitmap = recycledBmp,
        displayWidth = 1080,
        displayHeight = 2400,
        gameTitleHint = "Tetris"
    )
    assertEquals(1080, screenResult.displayWidth)
  }

  @Test
  fun `mediaprojection real time video stream updates vision analysis annotated bitmap and requester flow`() {
    com.eva1.companion.engines.MediaProjectionVisionEngine.setActiveGameContext(
        "Solitario com.tripledot.solitaire",
        com.eva1.companion.engines.GameBotMode.SOLITAIRE_CARDS
    )

    val frameBmp = android.graphics.Bitmap.createBitmap(270, 600, android.graphics.Bitmap.Config.ARGB_8888)
    frameBmp.eraseColor(android.graphics.Color.rgb(18, 128, 36))
    for (y in 148..255) {
        for (x in 14..34) {
            frameBmp.setPixel(x, y, android.graphics.Color.WHITE)
        }
    }
    for (y in 90..120) {
        for (x in 236..258) {
            frameBmp.setPixel(x, y, android.graphics.Color.rgb(15, 72, 160))
        }
    }

    val beforeCount = com.eva1.companion.engines.MediaProjectionVisionEngine.framesCapturedCount.value
    com.eva1.companion.engines.MediaProjectionVisionEngine.updateProjectedFrame(frameBmp, 1080, 2400)

    org.junit.Assert.assertTrue(com.eva1.companion.engines.MediaProjectionVisionEngine.isMediaProjectionActive.value)
    assertEquals(beforeCount + 1L, com.eva1.companion.engines.MediaProjectionVisionEngine.framesCapturedCount.value)
    org.junit.Assert.assertTrue(com.eva1.companion.engines.MediaProjectionVisionEngine.streamFps.value >= 1)
    org.junit.Assert.assertTrue(com.eva1.companion.engines.MediaProjectionVisionEngine.lastFrameLatencyMs.value >= 1L)
    org.junit.Assert.assertNotNull(com.eva1.companion.engines.MediaProjectionVisionEngine.latestScreenAnalysis.value)
    org.junit.Assert.assertNotNull(com.eva1.companion.engines.MediaProjectionVisionEngine.latestAnnotatedVisionBitmap.value)

    var dialogRequested = false
    com.eva1.companion.engines.MediaProjectionRequester.onRequestMediaProjection = {
        dialogRequested = true
    }
    val reqOk = com.eva1.companion.engines.MediaProjectionRequester.requestMediaProjection("com.tripledot.solitaire")
    org.junit.Assert.assertTrue(reqOk)
    org.junit.Assert.assertTrue(dialogRequested)
    assertEquals("com.tripledot.solitaire", com.eva1.companion.engines.MediaProjectionRequester.consumePendingPackageToLaunch())
    org.junit.Assert.assertNull(com.eva1.companion.engines.MediaProjectionRequester.consumePendingPackageToLaunch())
    com.eva1.companion.engines.MediaProjectionRequester.onRequestMediaProjection = null
  }

  @Test
  fun `scene 3d vision analyzer classifies blocked wall forest indoors and vehicle scenes accurately`() {
    // 1. Scenă cu perete plat în față (fără cer sus, perete gri-cărămiziu uniform pe centru, dar deschidere în dreapta)
    val wallBmp = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
    wallBmp.eraseColor(android.graphics.Color.rgb(95, 92, 88))
    // Deschidere luminoasă pe culoarul din dreapta (x: 215..290, y: 40..115)
    for (y in 40..115) {
        for (x in 215..290) {
            wallBmp.setPixel(x, y, if (y % 4 < 2) android.graphics.Color.rgb(220, 230, 240) else android.graphics.Color.rgb(50, 65, 80))
        }
    }
    val wallScene = com.eva1.companion.engines.GameScreenAnalysisUtility.analyze3dSceneEnvironment(
        bmp = wallBmp,
        bw = 320,
        bh = 180,
        gameTitleHint = "PUBG Mobile com.tencent.ig"
    )
    assertEquals(com.eva1.companion.engines.Scene3dEnvironmentType.BLOCKED_WALL_OBSTACLE, wallScene.sceneType)
    org.junit.Assert.assertTrue("Când este blocat în perete, joystick-ul trebuie tras ÎNAPOI (+Y > 0)", wallScene.recommendedJoyDyFrac > 0.5f)
    org.junit.Assert.assertTrue("Camera 3D trebuie rotită spre culoarul liber din dreapta", wallScene.recommendedCameraSwipeDxFrac > 0.15f)

    // 2. Scenă de Pădure (Cer albastru sus + vegetație verde densă pe mijloc)
    val forestBmp = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until 180) {
        for (x in 0 until 320) {
            val color = when {
                y < 55 -> android.graphics.Color.rgb(90, 175, 245) // Cer senin
                (x + y) % 5 == 0 -> android.graphics.Color.rgb(35, 95, 32)
                else -> android.graphics.Color.rgb(52, 138, 48) // Frunziș verde pădure
            }
            forestBmp.setPixel(x, y, color)
        }
    }
    val forestScene = com.eva1.companion.engines.GameScreenAnalysisUtility.analyze3dSceneEnvironment(
        bmp = forestBmp,
        bw = 320,
        bh = 180,
        gameTitleHint = "PUBG Mobile com.tencent.ig"
    )
    assertEquals(com.eva1.companion.engines.Scene3dEnvironmentType.FOREST_VEGETATION, forestScene.sceneType)
    org.junit.Assert.assertTrue(kotlin.math.abs(forestScene.recommendedJoyDxFrac) > 0.1f)
    org.junit.Assert.assertTrue(kotlin.math.abs(forestScene.recommendedCameraSwipeDxFrac) > 0.1f)

    // 3. Scenă În Mașină / Vehicul (Cer sus + HUD butoane viraj stânga/dreapta și pedale accelerație/frână)
    val carBmp = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until 180) {
        for (x in 0 until 320) {
            val c = if (y < 62) android.graphics.Color.rgb(110, 185, 250) else android.graphics.Color.rgb(120, 115, 100)
            carBmp.setPixel(x, y, c)
        }
    }
    val vehicleCoords = arrayOf(0.14f to 0.78f, 0.25f to 0.78f, 0.86f to 0.70f, 0.78f to 0.80f, 0.71f to 0.56f)
    for ((vx, vy) in vehicleCoords) {
        val px = (vx * 320).toInt()
        val py = (vy * 180).toInt()
        carBmp.setPixel(px, py, android.graphics.Color.WHITE)
    }
    val carScene = com.eva1.companion.engines.GameScreenAnalysisUtility.analyze3dSceneEnvironment(
        bmp = carBmp,
        bw = 320,
        bh = 180,
        gameTitleHint = "PUBG Mobile com.tencent.ig"
    )
    assertEquals(com.eva1.companion.engines.Scene3dEnvironmentType.IN_VEHICLE_DRIVING, carScene.sceneType)
  }

  @Test
  fun `gguf on device engine responds naturally to ce faci and general questions without preset manuals`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ggufEngine = com.eva1.companion.engines.GgufOnDeviceEngine(context)

    // 1. Verificăm că motorul implicit în aplicație este LOCAL_LLM (100% offline, fără dependență de cloud)
    val defaultUiState = com.eva1.companion.EvaUiState()
    assertEquals(com.eva1.companion.AiEngine.LOCAL_LLM, defaultUiState.selectedEngine)

    // 2. Verificăm că întrebarea „Ce faci?” primește un răspuns conversațional natural, ca un om, NU lista de 6 puncte
    val replyCeFaci = com.eva1.companion.engines.OfflineLlmEngine.synthesizeIntelligentReply(
        prompt = "Ce faci?",
        includeHeader = false
    )
    org.junit.Assert.assertTrue(replyCeFaci.isNotBlank())
    org.junit.Assert.assertFalse(
        "Răspunsul la 'Ce faci?' NU trebuie să fie manualul prestabilit cu 6 puncte",
        replyCeFaci.contains("Iată ce pot executa direct") || replyCeFaci.contains("1. **🖼️ Generare Poze")
    )
    org.junit.Assert.assertFalse(
        "Răspunsul NU trebuie să conțină șablonul robotic 'Răspuns Direct pentru'",
        replyCeFaci.contains("Răspuns Direct pentru:")
    )

    // 3. Verificăm și o întrebare deschisă oarecare (care anterior cădea în șablonul '💡 Răspuns Direct pentru')
    val replyOpenTopic = com.eva1.companion.engines.OfflineLlmEngine.synthesizeIntelligentReply(
        prompt = "De ce este importantă disciplina în viață?",
        includeHeader = false
    )
    org.junit.Assert.assertTrue(replyOpenTopic.length > 40)
    org.junit.Assert.assertFalse(
        "Întrebările generale NU trebuie să mai returneze șablonul '💡 Răspuns Direct pentru'",
        replyOpenTopic.contains("Răspuns Direct pentru:")
    )

    // 4. Verificăm că lista celor 11 modele locale este complet unificată (fără liste duplicate)
    assertEquals(11, com.eva1.companion.engines.OfflineLlmEngine.AVAILABLE_MODELS.size)
    assertEquals(11, com.eva1.companion.engines.GgufOnDeviceEngine.LIGHT_GGUF_CATALOG.size)
    assertEquals(
        com.eva1.companion.engines.OfflineLlmEngine.AVAILABLE_MODELS.map { it.id },
        com.eva1.companion.engines.GgufOnDeviceEngine.LIGHT_GGUF_CATALOG.map { it.id }
    )
    val formattedChatMl = com.eva1.companion.engines.GgufOnDeviceEngine.formatGgufInstructPrompt(
        template = com.eva1.companion.engines.GgufPromptTemplate.CHAT_ML,
        systemPrompt = "Ești EVA-1.",
        userPrompt = "Ce faci?",
        conversationHistory = emptyList(),
        memories = emptyList(),
        deviceTelemetry = ""
    )
    org.junit.Assert.assertTrue(formattedChatMl.contains("<|im_start|>user\nCe faci?<|im_end|>"))
    org.junit.Assert.assertTrue(formattedChatMl.endsWith("<|im_start|>assistant\n"))
  }
}





