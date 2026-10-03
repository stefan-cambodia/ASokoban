package dev.stefan.sokoban.ui.board3d

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View as AndroidView
import com.google.android.filament.Box
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * One Filament engine drawing into a [TextureView]: renderer, view, camera,
 * materials and the frame loop. A [BoardScene] fills it; [onFrame] runs
 * before each frame so the scene can follow the board's animations.
 *
 * Everything here runs on the main thread, which is where the engine is made.
 */
internal class FilamentHost(context: Context) {

    init {
        Filament.init()
        Gltfio.init()
    }

    val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    val scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    val materials = MaterialLibrary(engine)
    private val colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutralToneMapper()).build(engine)

    val textureView = TextureView(context)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null

    var width = 0
        private set
    var height = 0
        private set

    /** Frames the camera; set by the scene being shown. */
    var rig: CameraRig? = null
        set(value) {
            field = value
            frameCamera()
        }

    var onFrame: ((frameTimeNanos: Long) -> Unit)? = null

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            val chain = swapChain ?: return
            if (!uiHelper.isReadyToRender) return
            onFrame?.invoke(frameTimeNanos)
            if (renderer.beginFrame(chain, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    init {
        view.scene = scene
        view.camera = camera
        // The board floats over the app's own background.
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        view.colorGrading = colorGrading
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.35f
            intensity = 0.9f
        }
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 4
        }
        // Soft, filtered shadow edges: hard ones show their pixels along the walls.
        view.setShadowType(View.ShadowType.DPCF)
        view.softShadowOptions = View.SoftShadowOptions().apply { penumbraScale = 1.6f }
        camera.setExposure(1f)

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                this@FilamentHost.width = width
                this@FilamentHost.height = height
                view.viewport = Viewport(0, 0, width, height)
                frameCamera()
            }
        }
        uiHelper.attachTo(textureView)
        textureView.addOnAttachStateChangeListener(object : AndroidView.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: AndroidView) = start()
            override fun onViewDetachedFromWindow(v: AndroidView) = stop()
        })
    }

    private fun frameCamera() {
        val rig = rig ?: return
        if (width <= 0 || height <= 0) return
        rig.fit(width, height)
        camera.setProjection(rig.fovDegrees.toDouble(), width.toDouble() / height, 0.1, 200.0, com.google.android.filament.Camera.Fov.VERTICAL)
        val (eye, target) = rig.eye to rig.target
        camera.lookAt(eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(), target.x.toDouble(), target.y.toDouble(), target.z.toDouble(), 0.0, 1.0, 0.0)
    }

    private fun start() {
        if (running) return
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        stop()
        onFrame = null
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        materials.destroy()
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }
}

/**
 * Lit materials from gltfio's ubershaders: no material compiler needed. Every
 * instance multiplies vertex colours by its base colour, so one mesh can be
 * tinted per crate, per goal, per theme.
 */
internal class MaterialLibrary(private val engine: Engine) {

    private val provider = UbershaderProvider(engine)
    private val instances = mutableListOf<MaterialInstance>()
    private val sampler = TextureSampler()

    // Unused texture slots still get a texture bound, for strict drivers.
    private val blank: Texture = Texture.Builder()
        .width(1).height(1).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.RGBA8)
        .build(engine)
        .also { texture ->
            val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).put(byteArrayOf(-1, -1, -1, -1))
            pixel.flip()
            texture.setImage(engine, 0, Texture.PixelBufferDescriptor(pixel, Texture.Format.RGBA, Texture.Type.UBYTE))
        }

    fun lit(label: String, roughness: Float, tint: Rgba = Rgba.WHITE): MaterialInstance {
        val key = MaterialProvider.MaterialKey().apply {
            hasVertexColors = true
            alphaMode = 0
        }
        val instance = requireNotNull(provider.createMaterialInstance(key, IntArray(8), label, null)) { "No ubershader for $label" }
        for (parameter in instance.material.parameters) {
            if (parameter.type == com.google.android.filament.Material.Parameter.Type.SAMPLER_2D) {
                instance.setParameter(parameter.name, blank, sampler)
            }
        }
        instance.setFloat("metallicFactor", 0f)
        instance.setFloat("roughnessFactor", roughness)
        tint(instance, tint)
        instances += instance
        return instance
    }

    fun tint(instance: MaterialInstance, color: Rgba) {
        if (instance.material.hasParameter("baseColorFactor")) instance.setParameter("baseColorFactor", color.r, color.g, color.b, color.a)
    }

    fun glow(instance: MaterialInstance, color: Rgba, strength: Float) {
        if (instance.material.hasParameter("emissiveFactor")) {
            instance.setParameter("emissiveFactor", color.r * strength, color.g * strength, color.b * strength)
        }
    }

    private fun MaterialInstance.setFloat(name: String, value: Float) {
        if (material.hasParameter(name)) setParameter(name, value)
    }

    fun destroy() {
        instances.forEach(engine::destroyMaterialInstance)
        instances.clear()
        provider.destroyMaterials()
        provider.destroy()
        engine.destroyTexture(blank)
    }
}

/** A [MeshData] uploaded to the GPU, with the attributes the ubershaders read. */
internal class GpuMesh(private val engine: Engine, data: MeshData) {

    val indexCount = data.indexCount
    val box: Box
    private val vertices: VertexBuffer
    private val indices: IndexBuffer

    init {
        val count = data.vertexCount
        val normals = floats(data.normals())
        val tangents = floats(FloatArray(count * 4))
        SurfaceOrientation.Builder().vertexCount(count).normals(normals).build().run {
            getQuatsAsFloat(tangents)
            destroy()
        }
        vertices = VertexBuffer.Builder()
            .bufferCount(5)
            .vertexCount(count)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 2, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 3, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .attribute(VertexBuffer.VertexAttribute.UV1, 4, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        vertices.setBufferAt(engine, 0, floats(data.positions()))
        vertices.setBufferAt(engine, 1, tangents.also { it.rewind() })
        vertices.setBufferAt(engine, 2, floats(data.colors()))
        vertices.setBufferAt(engine, 3, floats(FloatArray(count * 2)))
        vertices.setBufferAt(engine, 4, floats(FloatArray(count * 2)))
        indices = IndexBuffer.Builder()
            .indexCount(data.indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indices.setBuffer(engine, ints(data.indices()))
        val (min, max) = data.bounds()
        box = Box(
            (min.x + max.x) / 2f, (min.y + max.y) / 2f, (min.z + max.z) / 2f,
            (max.x - min.x) / 2f, (max.y - min.y) / 2f, (max.z - min.z) / 2f,
        )
    }

    fun renderable(entity: Int, material: MaterialInstance, castShadows: Boolean = true) {
        com.google.android.filament.RenderableManager.Builder(1)
            .boundingBox(box)
            .geometry(0, com.google.android.filament.RenderableManager.PrimitiveType.TRIANGLES, vertices, indices, 0, indexCount)
            .material(0, material)
            .castShadows(castShadows)
            .receiveShadows(true)
            .build(engine, entity)
    }

    fun destroy() {
        engine.destroyVertexBuffer(vertices)
        engine.destroyIndexBuffer(indices)
    }

    private companion object {
        fun floats(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                rewind()
            }

        fun ints(values: IntArray): IntBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
                put(values)
                rewind()
            }
    }
}
