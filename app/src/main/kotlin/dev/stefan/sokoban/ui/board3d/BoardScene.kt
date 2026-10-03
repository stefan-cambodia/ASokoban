package dev.stefan.sokoban.ui.board3d

import androidx.compose.ui.graphics.Color
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.Tile
import dev.stefan.sokoban.ui.board.BoardMotion
import dev.stefan.sokoban.ui.board.easeOutBack
import dev.stefan.sokoban.ui.board.easeOutBounce
import dev.stefan.sokoban.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A level in 3D inside a [FilamentHost]: the board as one static mesh, a
 * renderable per crate and per target so each can be tinted and lit on its
 * own, and the hero, all following [BoardMotion] frame by frame.
 */
internal class BoardScene(private val host: FilamentHost, private val level: Level, palette: Palette, crateCount: Int) {

    val rig = CameraRig(level.width, level.height, WALL_HEIGHT)

    private val engine = host.engine
    private val transforms = engine.transformManager
    private val materials = host.materials
    private val entities = mutableListOf<Int>()
    private val meshes = mutableListOf<GpuMesh>()

    private val crateColor = palette.crate.linear()
    private val crateDoneColor = palette.crateDone.linear()
    private val dangerColor = palette.danger.linear()
    private val glowColor = palette.goalGlow.linear()

    private val root = entity(renderable = false)
    private val crates: List<Pair<Int, MaterialInstance>>
    private val goals: Map<Position, Pair<Int, MaterialInstance>>
    private val hero = entity(renderable = false)
    private val sun = EntityManager.get().create()
    private val ambient: IndirectLight

    private var heroAngle = 0f
    private var lastFrameNanos = 0L
    private var clock = 0f

    init {
        transforms.create(root)

        val board = MeshData().also { buildBoard(it, palette) }
        val boardEntity = entity()
        upload(board).renderable(boardEntity, materials.lit("board", roughness = 0.85f))
        transforms.create(boardEntity, transforms.getInstance(root), Mat4.identity())

        val goalMesh = upload(MeshData().also { buildGoal(it) })
        goals = level.goals.associateWith { cell ->
            val entity = entity()
            val material = materials.lit("goal", roughness = 0.5f, tint = palette.goal.linear())
            goalMesh.renderable(entity, material, castShadows = false)
            transforms.create(entity, transforms.getInstance(root), Mat4.translation(cell.x + 0.5f, 0f, cell.y + 0.5f))
            entity to material
        }

        val crateMesh = upload(MeshData().also { buildCrate(it) })
        crates = List(crateCount) {
            val entity = entity()
            val material = materials.lit("crate", roughness = 0.62f, tint = crateColor)
            crateMesh.renderable(entity, material)
            transforms.create(entity, transforms.getInstance(root), Mat4.identity())
            entity to material
        }

        transforms.create(hero, transforms.getInstance(root), Mat4.identity())
        val body = entity()
        upload(MeshData().also { it.sphere(Vec3(0f, 0.33f, 0f), Vec3(0.34f, 0.33f, 0.33f), Rgba.WHITE) })
            .renderable(body, materials.lit("hero", roughness = 0.38f, tint = palette.player.linear()))
        transforms.create(body, transforms.getInstance(hero), Mat4.identity())
        val face = entity()
        upload(MeshData().also { buildFace(it, palette) }).renderable(face, materials.lit("face", roughness = 0.3f))
        transforms.create(face, transforms.getInstance(hero), Mat4.identity())

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.96f, 0.9f)
            .intensity(if (palette.isDark) 3.0f else 3.1f)
            .direction(0.42f, -1f, -0.5f)
            .castShadows(true)
            .shadowOptions(LightManager.ShadowOptions().apply { mapSize = 2048 })
            .build(engine, sun)
        host.scene.addEntity(sun)
        val sky = if (palette.isDark) floatArrayOf(0.28f, 0.3f, 0.36f) else floatArrayOf(0.46f, 0.46f, 0.48f)
        ambient = IndirectLight.Builder().irradiance(1, sky).intensity(1f).build(engine)
        host.scene.indirectLight = ambient
    }

    private fun entity(renderable: Boolean = true): Int {
        val entity = EntityManager.get().create()
        entities += entity
        if (renderable) host.scene.addEntity(entity)
        return entity
    }

    private fun upload(data: MeshData) = GpuMesh(engine, data).also { meshes += it }

    private fun buildBoard(mesh: MeshData, palette: Palette) {
        // On the dark theme the floor is the card's own tone: lift it a little.
        val floorA = palette.floor.lifted(palette)
        val floorB = palette.floorAlt.lifted(palette)
        val grout = palette.floorEdge.linear()
        val wallTop = palette.wallTop.linear()
        val wallSide = palette.wallFront.linear()
        val plinth = palette.wallFront.linear().scaled(0.62f)
        fun inside(x: Int, y: Int) = level.tileAt(x, y) != Tile.VOID
        for (y in 0 until level.height) for (x in 0 until level.width) {
            val tile = level.tileAt(x, y)
            if (tile == Tile.VOID) continue
            val fx = x.toFloat()
            val fz = y.toFloat()
            // The plinth: the board's edge shows as a slab under the outer cells.
            var skip = (1 shl 2) or (1 shl 3)
            if (inside(x - 1, y)) skip = skip or 1
            if (inside(x + 1, y)) skip = skip or (1 shl 1)
            if (inside(x, y - 1)) skip = skip or (1 shl 4)
            if (inside(x, y + 1)) skip = skip or (1 shl 5)
            mesh.box(Vec3(fx, -PLINTH, fz), Vec3(fx + 1, 0f, fz + 1), { plinth }, skip)
            mesh.quad(Vec3(fx, 0f, fz), Vec3(fx + 1, 0f, fz), Vec3(fx + 1, 0f, fz + 1), Vec3(fx, 0f, fz + 1), Vec3.UP, if (tile == Tile.WALL) plinth else grout)
            if (tile == Tile.WALL) {
                // Stones vary a little in tone, like the 2D masonry.
                val shade = 1f + (hash(x, y) - 0.5f) * 0.14f
                mesh.bevelBox(
                    center = Vec3(fx + 0.5f, WALL_HEIGHT / 2f, fz + 0.5f),
                    half = Vec3(0.488f, WALL_HEIGHT / 2f, 0.488f),
                    bevel = 0.06f,
                    face = { axis, _ -> if (axis == 1) wallTop.scaled(shade) else wallSide.scaled(shade) },
                    edge = wallTop.scaled(shade * 0.92f),
                )
            } else {
                val tone = if ((x + y) % 2 == 0) floorA else floorB
                mesh.bevelBox(
                    center = Vec3(fx + 0.5f, TILE_HEIGHT / 2f, fz + 0.5f),
                    half = Vec3(0.47f, TILE_HEIGHT / 2f, 0.47f),
                    bevel = 0.018f,
                    face = { _, _ -> tone },
                    edge = tone.scaled(0.93f),
                )
            }
        }
    }

    private fun buildGoal(mesh: MeshData) {
        mesh.ring(Vec3(0f, TILE_HEIGHT, 0f), inner = 0.17f, outer = 0.28f, height = 0.022f, color = Rgba.WHITE, side = Rgba.WHITE.scaled(0.8f))
        mesh.cylinder(Vec3(0f, TILE_HEIGHT, 0f), radius = 0.075f, height = 0.022f, color = Rgba.WHITE)
    }

    private fun buildCrate(mesh: MeshData) {
        val s = CRATE_HALF
        mesh.bevelBox(Vec3(0f, s, 0f), Vec3(s, s, s), bevel = 0.07f, face = { _, _ -> Rgba.WHITE }, edge = Rgba.WHITE.scaled(0.7f))
        // A strap around the sides and a cross of planks on the lid.
        val strap = Rgba.WHITE.scaled(0.62f)
        mesh.box(Vec3(-s - 0.012f, s - 0.055f, -s - 0.012f), Vec3(s + 0.012f, s + 0.055f, s + 0.012f), { strap }, skip = (1 shl 2) or (1 shl 3))
        mesh.box(Vec3(-s + 0.06f, 2 * s, -0.05f), Vec3(s - 0.06f, 2 * s + 0.014f, 0.05f), { strap }, skip = 1 shl 2)
        mesh.box(Vec3(-0.05f, 2 * s, -s + 0.06f), Vec3(0.05f, 2 * s + 0.014f, s - 0.06f), { strap }, skip = 1 shl 2)
    }

    private fun buildFace(mesh: MeshData, palette: Palette) {
        val eye = palette.eye.linear()
        val pupil = palette.pupil.linear()
        for (side in floatArrayOf(-1f, 1f)) {
            mesh.sphere(Vec3(side * 0.12f, 0.41f, 0.265f), Vec3(0.09f, 0.095f, 0.08f), eye, slices = 16, stacks = 10)
            mesh.sphere(Vec3(side * 0.12f, 0.412f, 0.332f), Vec3(0.047f, 0.052f, 0.034f), pupil, slices = 12, stacks = 8)
        }
        val leaf = palette.goal.linear()
        mesh.cylinder(Vec3(0f, 0.63f, 0f), radius = 0.024f, height = 0.09f, color = leaf.scaled(0.55f))
        mesh.sphere(Vec3(0.075f, 0.71f, 0f), Vec3(0.085f, 0.024f, 0.045f), leaf, slices = 12, stacks = 6)
    }

    /** Moves everything to where [motion] has it now. */
    fun update(motion: BoardMotion, frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos
        clock += dt

        val entrance = motion.entrance.value
        val celebration = motion.celebration.value
        val grow = 0.92f + 0.08f * entrance.coerceIn(0f, 1f)
        transforms.setTransform(
            transforms.getInstance(root),
            Mat4.translation(level.width / 2f, 0f, level.height / 2f)
                .times(Mat4.scale(grow, grow, grow))
                .times(Mat4.translation(-level.width / 2f, 0f, -level.height / 2f)),
        )

        for ((index, crate) in crates.withIndex()) {
            val (entity, material) = crate
            val position = motion.crates[index].value
            val appear = ((entrance - 0.2f - index * 0.05f) / 0.45f).coerceIn(0f, 1f)
            val scale = easeOutBack(appear)
            val squash = motion.crateSquash[index].value
            val hop = sin((celebration * 1.8f - index * 0.12f).coerceIn(0f, 1f) * PI.toFloat()) * 0.3f
            val wide = scale * (1f + 0.1f * squash)
            transforms.setTransform(
                transforms.getInstance(entity),
                Mat4.translation(position.x + 0.5f, TILE_HEIGHT + hop, position.y + 0.5f).times(Mat4.scale(wide, scale * (1f - 0.18f * squash), wide)),
            )
            val done = motion.crateDone[index].value
            val stuck = motion.crateStuck[index].value
            materials.tint(material, crateColor.mix(crateDoneColor, done).mix(dangerColor, stuck * 0.35f))
            materials.glow(material, glowColor, motion.crateGlow[index].value * 0.5f)
        }

        val pulse = (sin(clock * PI.toFloat()) + 1f) / 2f
        for ((cell, goal) in goals) {
            val lit = motion.goalLit[cell]?.value ?: 0f
            materials.glow(goal.second, glowColor, 0.08f + 0.12f * pulse * (1f - lit) + 0.7f * lit)
        }

        // The hero turns towards where it walks, leans into a push, breathes.
        val target = when (motion.facing) {
            Direction.DOWN -> 0f
            Direction.RIGHT -> 90f
            Direction.UP -> 180f
            Direction.LEFT -> -90f
        }
        var delta = (target - heroAngle) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        heroAngle += delta * (dt * 16f).coerceAtMost(1f)
        val position = motion.hero.value
        val bump = motion.bump.value * 0.14f
        val direction = motion.bumpDirection
        val appear = ((entrance - 0.35f) / 0.55f).coerceIn(0f, 1f)
        val drop = (1f - easeOutBounce(appear)) * 1.4f
        val joy = abs(sin(celebration * 3f * PI.toFloat())) * 0.35f * (if (celebration in 0.001f..0.999f) 1f else 0f)
        val breath = sin(clock * 2f * PI.toFloat() / 2f) * 0.025f
        val heroScale = if (appear > 0f) 1f else 0f
        transforms.setTransform(
            transforms.getInstance(hero),
            Mat4.translation(position.x + 0.5f + direction.dx * bump, TILE_HEIGHT + drop + joy, position.y + 0.5f + direction.dy * bump)
                .times(Mat4.rotationY(Math.toRadians(heroAngle.toDouble()).toFloat()))
                .times(Mat4.rotationX(motion.lean.value * 0.28f))
                .times(Mat4.scale(heroScale * (1f - breath / 2f), heroScale * (1f + breath), heroScale * (1f - breath / 2f))),
        )
    }

    fun destroy() {
        host.scene.removeEntity(sun)
        engine.destroyEntity(sun)
        EntityManager.get().destroy(sun)
        host.scene.indirectLight = null
        engine.destroyIndirectLight(ambient)
        for (entity in entities) {
            host.scene.removeEntity(entity)
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
        }
        meshes.forEach(GpuMesh::destroy)
    }

    companion object {
        /** Low enough that a wall never hides the cell behind it. */
        const val WALL_HEIGHT = 0.55f
        const val TILE_HEIGHT = 0.045f
        const val PLINTH = 0.32f
        const val CRATE_HALF = 0.37f

        private fun hash(x: Int, y: Int): Float {
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0xffff) / 65535f
        }
    }
}

private fun Color.linear() = Rgba.srgb(red, green, blue)

private fun Color.lifted(palette: Palette): Rgba =
    if (palette.isDark) androidx.compose.ui.graphics.lerp(this, palette.wallTop, 0.22f).linear() else linear()

private fun Rgba.mix(other: Rgba, t: Float) = Rgba(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t, a + (other.a - a) * t)

/** Column-major 4x4 matrices, as Filament takes them. */
internal object Mat4 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    fun translation(x: Float, y: Float, z: Float) = identity().also {
        it[12] = x; it[13] = y; it[14] = z
    }

    fun scale(x: Float, y: Float, z: Float) = identity().also {
        it[0] = x; it[5] = y; it[10] = z
    }

    fun rotationY(radians: Float) = identity().also {
        val c = cos(radians)
        val s = sin(radians)
        it[0] = c; it[2] = -s; it[8] = s; it[10] = c
    }

    fun rotationX(radians: Float) = identity().also {
        val c = cos(radians)
        val s = sin(radians)
        it[5] = c; it[6] = s; it[9] = -s; it[10] = c
    }
}

/** `this * other`, both column-major. */
internal fun FloatArray.times(other: FloatArray): FloatArray {
    val result = FloatArray(16)
    for (col in 0..3) for (row in 0..3) {
        var sum = 0f
        for (k in 0..3) sum += this[k * 4 + row] * other[col * 4 + k]
        result[col * 4 + row] = sum
    }
    return result
}
