package com.joebad.fastbreak.ui.bracket

import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android implementation of the 3D bracket view using Google Filament.
 * Renders bracket matchups as colored quads arranged in 3D space.
 */
@Composable
actual fun FilamentBracketView(
    modifier: Modifier,
    bracketData: BracketData
) {
    val context = LocalContext.current

    val filamentState = remember {
        FilamentBracketState(bracketData)
    }

    DisposableEffect(Unit) {
        onDispose {
            filamentState.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { surfaceView ->
                filamentState.initialize(ctx, surfaceView)
            }
        }
    )
}

/**
 * Manages all Filament resources for the bracket visualization.
 */
private class FilamentBracketState(
    private val bracketData: BracketData
) {
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: com.google.android.filament.View
    private lateinit var camera: Camera
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private lateinit var choreographer: Choreographer
    private lateinit var material: Material

    private var swapChain: SwapChain? = null
    private val renderables = mutableListOf<Int>()
    private val vertexBuffers = mutableListOf<VertexBuffer>()
    private val indexBuffers = mutableListOf<IndexBuffer>()
    private var isInitialized = false
    private var isDestroyed = false

    // Animation angle
    private var angle = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isDestroyed) return
            choreographer.postFrameCallback(this)

            // Gentle rotation animation
            angle += 0.15f
            if (angle >= 360f) angle -= 360f

            if (uiHelper.isReadyToRender) {
                if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    fun initialize(context: android.content.Context, surfaceView: SurfaceView) {
        if (isInitialized) return
        isInitialized = true

        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(context)

        // Initialize Filament engine
        engine = Engine.Builder().build()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        // Set up UiHelper for surface management
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(
                    45.0, aspect, 0.1, 100.0
                )
                view.viewport = Viewport(0, 0, width, height)
                FilamentHelper.synchronizePendingFrames(engine)
            }
        }
        uiHelper.attachTo(surfaceView)

        // Configure view
        scene.skybox = Skybox.Builder()
            .color(0.1f, 0.1f, 0.15f, 1.0f) // Dark blue-gray background
            .build(engine)
        view.camera = camera
        view.scene = scene

        // Position camera to look at the bracket
        camera.lookAt(
            0.0, 0.5, 6.0,   // eye position
            0.0, 0.0, 0.0,   // target (center of bracket)
            0.0, 1.0, 0.0    // up vector
        )

        // Load material and create bracket geometry
        loadMaterial(context)
        createBracketGeometry()

        // Start render loop
        choreographer.postFrameCallback(frameCallback)
    }

    private fun loadMaterial(context: android.content.Context) {
        val buffer = readAsset(context, "materials/bracket_material.filamat")
        material = Material.Builder()
            .payload(buffer, buffer.remaining())
            .build(engine)
    }

    private fun readAsset(context: android.content.Context, assetName: String): ByteBuffer {
        context.assets.openFd(assetName).use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())
            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()
            return dst.apply { rewind() }
        }
    }

    /**
     * Creates 3D geometry for the tournament bracket.
     * Each matchup is a colored quad, positioned by round and slot.
     * Rounds are staggered in Z-depth to create a 3D effect.
     */
    private fun createBracketGeometry() {
        val cardWidth = 1.4f
        val cardHeight = 0.35f
        val roundSpacingX = 2.0f
        val matchupSpacingY = 1.0f
        val roundDepthZ = 0.8f // Z offset per round for 3D depth

        // Colors for each round (ABGR format for Filament vertex colors)
        val roundColors = listOf(
            0xFF4CAF50.toInt(), // Green - Round of 64
            0xFF2196F3.toInt(), // Blue - Round of 32
            0xFFFF9800.toInt()  // Orange - Sweet 16
        )

        val winnerColor = 0xFFFFD700.toInt() // Gold

        bracketData.rounds.forEachIndexed { roundIndex, round ->
            val matchupCount = round.matchups.size
            val totalHeight = (matchupCount - 1) * matchupSpacingY

            round.matchups.forEachIndexed { matchupIndex, matchup ->
                val x = (roundIndex - (bracketData.rounds.size - 1) / 2.0f) * roundSpacingX
                val y = matchupIndex * matchupSpacingY - totalHeight / 2.0f
                val z = -roundIndex * roundDepthZ

                val color = roundColors.getOrElse(roundIndex) { roundColors.last() }

                // Create quad for this matchup
                createQuad(
                    centerX = x,
                    centerY = y,
                    centerZ = z,
                    width = cardWidth,
                    height = cardHeight,
                    color = color
                )
            }
        }

        // Create connecting lines between rounds as thin quads
        for (roundIndex in 0 until bracketData.rounds.size - 1) {
            val currentRound = bracketData.rounds[roundIndex]
            val nextRound = bracketData.rounds[roundIndex + 1]

            val currentMatchupCount = currentRound.matchups.size
            val nextMatchupCount = nextRound.matchups.size
            val currentTotalHeight = (currentMatchupCount - 1) * matchupSpacingY
            val nextTotalHeight = (nextMatchupCount - 1) * matchupSpacingY

            for (nextIdx in nextRound.matchups.indices) {
                val sourceIdx1 = nextIdx * 2
                val sourceIdx2 = nextIdx * 2 + 1

                if (sourceIdx1 < currentMatchupCount) {
                    val fromX = (roundIndex - (bracketData.rounds.size - 1) / 2.0f) * roundSpacingX + cardWidth / 2f
                    val fromY = sourceIdx1 * matchupSpacingY - currentTotalHeight / 2.0f
                    val fromZ = -roundIndex * roundDepthZ

                    val toX = ((roundIndex + 1) - (bracketData.rounds.size - 1) / 2.0f) * roundSpacingX - cardWidth / 2f
                    val toY = nextIdx * matchupSpacingY - nextTotalHeight / 2.0f
                    val toZ = -(roundIndex + 1) * roundDepthZ

                    createLine(fromX, fromY, fromZ, toX, toY, toZ, 0xFFCCCCCC.toInt())
                }
                if (sourceIdx2 < currentMatchupCount) {
                    val fromX = (roundIndex - (bracketData.rounds.size - 1) / 2.0f) * roundSpacingX + cardWidth / 2f
                    val fromY = sourceIdx2 * matchupSpacingY - currentTotalHeight / 2.0f
                    val fromZ = -roundIndex * roundDepthZ

                    val toX = ((roundIndex + 1) - (bracketData.rounds.size - 1) / 2.0f) * roundSpacingX - cardWidth / 2f
                    val toY = nextIdx * matchupSpacingY - nextTotalHeight / 2.0f
                    val toZ = -(roundIndex + 1) * roundDepthZ

                    createLine(fromX, fromY, fromZ, toX, toY, toZ, 0xFFCCCCCC.toInt())
                }
            }
        }
    }

    /**
     * Creates a single colored quad (two triangles) at the given position.
     */
    private fun createQuad(
        centerX: Float, centerY: Float, centerZ: Float,
        width: Float, height: Float,
        color: Int
    ) {
        val halfW = width / 2f
        val halfH = height / 2f

        // Convert ARGB color to individual bytes (Filament expects RGBA order in vertex data)
        val r = ((color shr 16) and 0xFF).toByte()
        val g = ((color shr 8) and 0xFF).toByte()
        val b = (color and 0xFF).toByte()
        val a = ((color shr 24) and 0xFF).toByte()

        val floatSize = 4
        val colorSize = 4 // RGBA bytes
        val vertexSize = 3 * floatSize + colorSize
        val vertexCount = 4

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            .order(ByteOrder.nativeOrder())
        // Bottom-left
        vertexData.putFloat(centerX - halfW).putFloat(centerY - halfH).putFloat(centerZ)
        vertexData.put(r).put(g).put(b).put(a)
        // Bottom-right
        vertexData.putFloat(centerX + halfW).putFloat(centerY - halfH).putFloat(centerZ)
        vertexData.put(r).put(g).put(b).put(a)
        // Top-right
        vertexData.putFloat(centerX + halfW).putFloat(centerY + halfH).putFloat(centerZ)
        vertexData.put(r).put(g).put(b).put(a)
        // Top-left
        vertexData.putFloat(centerX - halfW).putFloat(centerY + halfH).putFloat(centerZ)
        vertexData.put(r).put(g).put(b).put(a)
        vertexData.flip()

        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.UBYTE4, 3 * floatSize, vertexSize)
            .normalized(VertexBuffer.VertexAttribute.COLOR)
            .build(engine)
        vb.setBufferAt(engine, 0, vertexData)
        vertexBuffers.add(vb)

        val indexData = ByteBuffer.allocate(6 * 2)
            .order(ByteOrder.nativeOrder())
        indexData.putShort(0).putShort(1).putShort(2) // Triangle 1
        indexData.putShort(0).putShort(2).putShort(3) // Triangle 2
        indexData.flip()

        val ib = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, indexData)
        indexBuffers.add(ib)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(centerX, centerY, centerZ, halfW, halfH, 0.01f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, 6)
            .material(0, material.defaultInstance)
            .build(engine, entity)

        scene.addEntity(entity)
        renderables.add(entity)
    }

    /**
     * Creates a thin quad representing a connecting line between bracket matchups.
     */
    private fun createLine(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        color: Int
    ) {
        val lineThickness = 0.02f

        val r = ((color shr 16) and 0xFF).toByte()
        val g = ((color shr 8) and 0xFF).toByte()
        val b = (color and 0xFF).toByte()
        val a = ((color shr 24) and 0xFF).toByte()

        val floatSize = 4
        val colorSize = 4
        val vertexSize = 3 * floatSize + colorSize
        val vertexCount = 4

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            .order(ByteOrder.nativeOrder())
        // Offset perpendicular to line in Y direction for thickness
        vertexData.putFloat(x1).putFloat(y1 - lineThickness).putFloat(z1)
        vertexData.put(r).put(g).put(b).put(a)
        vertexData.putFloat(x2).putFloat(y2 - lineThickness).putFloat(z2)
        vertexData.put(r).put(g).put(b).put(a)
        vertexData.putFloat(x2).putFloat(y2 + lineThickness).putFloat(z2)
        vertexData.put(r).put(g).put(b).put(a)
        vertexData.putFloat(x1).putFloat(y1 + lineThickness).putFloat(z1)
        vertexData.put(r).put(g).put(b).put(a)
        vertexData.flip()

        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.UBYTE4, 3 * floatSize, vertexSize)
            .normalized(VertexBuffer.VertexAttribute.COLOR)
            .build(engine)
        vb.setBufferAt(engine, 0, vertexData)
        vertexBuffers.add(vb)

        val indexData = ByteBuffer.allocate(6 * 2)
            .order(ByteOrder.nativeOrder())
        indexData.putShort(0).putShort(1).putShort(2)
        indexData.putShort(0).putShort(2).putShort(3)
        indexData.flip()

        val ib = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, indexData)
        indexBuffers.add(ib)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(
                (x1 + x2) / 2f, (y1 + y2) / 2f, (z1 + z2) / 2f,
                kotlin.math.abs(x2 - x1) / 2f + 0.1f,
                kotlin.math.abs(y2 - y1) / 2f + lineThickness,
                kotlin.math.abs(z2 - z1) / 2f + 0.1f
            ))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, 6)
            .material(0, material.defaultInstance)
            .build(engine, entity)

        scene.addEntity(entity)
        renderables.add(entity)
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true

        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()

        renderables.forEach { entity ->
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
        }
        vertexBuffers.forEach { engine.destroyVertexBuffer(it) }
        indexBuffers.forEach { engine.destroyIndexBuffer(it) }
        engine.destroyMaterial(material)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        EntityManager.get().destroy(camera.entity)
        engine.destroy()
    }
}
