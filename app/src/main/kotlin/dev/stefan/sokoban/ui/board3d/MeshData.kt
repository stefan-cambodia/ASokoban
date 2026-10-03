package dev.stefan.sokoban.ui.board3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A small 3D vector for building geometry and placing the camera. */
internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3 = length().let { if (it == 0f) this else this * (1f / it) }

    companion object {
        val UP = Vec3(0f, 1f, 0f)
    }
}

/** A linear-space RGBA colour; shading is done on vertex colours and tinted per material. */
internal class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    fun scaled(s: Float) = Rgba(r * s, g * s, b * s, a)

    companion object {
        val WHITE = Rgba(1f, 1f, 1f)

        /** From an sRGB colour, as the palette stores them. */
        fun srgb(r: Float, g: Float, b: Float): Rgba = Rgba(toLinear(r), toLinear(g), toLinear(b))

        private fun toLinear(c: Float): Float =
            if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
}

/**
 * Triangle soup with per-vertex position, normal and colour, built on the CPU
 * and uploaded once. Faces are given with the normal they should face; the
 * winding is worked out here, so a face can never end up culled by mistake.
 */
internal class MeshData {

    private var positions = FloatArray(1024)
    private var normals = FloatArray(1024)
    private var colors = FloatArray(1024)
    private var indices = IntArray(1024)

    var vertexCount = 0
        private set
    var indexCount = 0
        private set

    fun positions(): FloatArray = positions.copyOf(vertexCount * 3)
    fun normals(): FloatArray = normals.copyOf(vertexCount * 3)
    fun colors(): FloatArray = colors.copyOf(vertexCount * 4)
    fun indices(): IntArray = indices.copyOf(indexCount)

    fun vertex(p: Vec3, n: Vec3, c: Rgba): Int {
        if ((vertexCount + 1) * 4 > colors.size) {
            positions = positions.copyOf(positions.size * 2)
            normals = normals.copyOf(normals.size * 2)
            colors = colors.copyOf(colors.size * 2)
        }
        val i = vertexCount
        positions[i * 3] = p.x; positions[i * 3 + 1] = p.y; positions[i * 3 + 2] = p.z
        normals[i * 3] = n.x; normals[i * 3 + 1] = n.y; normals[i * 3 + 2] = n.z
        colors[i * 4] = c.r; colors[i * 4 + 1] = c.g; colors[i * 4 + 2] = c.b; colors[i * 4 + 3] = c.a
        return vertexCount++
    }

    private fun index(i: Int) {
        if (indexCount == indices.size) indices = indices.copyOf(indices.size * 2)
        indices[indexCount++] = i
    }

    /** One triangle of existing vertices, wound to face [normal]. */
    fun triangle(a: Int, b: Int, c: Int, normal: Vec3) {
        val pa = at(a)
        val facing = (at(b) - pa) cross (at(c) - pa)
        if (facing dot normal >= 0f) {
            index(a); index(b); index(c)
        } else {
            index(a); index(c); index(b)
        }
    }

    private fun at(i: Int) = Vec3(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])

    /** A flat-shaded triangle facing [normal]. */
    fun flatTriangle(a: Vec3, b: Vec3, c: Vec3, normal: Vec3, color: Rgba) {
        triangle(vertex(a, normal, color), vertex(b, normal, color), vertex(c, normal, color), normal)
    }

    /** A flat-shaded quad facing [normal]; corners in order around its edge. */
    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, normal: Vec3, color: Rgba) {
        val ia = vertex(a, normal, color)
        val ib = vertex(b, normal, color)
        val ic = vertex(c, normal, color)
        val id = vertex(d, normal, color)
        triangle(ia, ib, ic, normal)
        triangle(ia, ic, id, normal)
    }

    /**
     * An axis-aligned box from [min] to [max] with flat faces. [skip] leaves
     * out faces nobody can see (bit order: -x, +x, -y, +y, -z, +z).
     */
    fun box(min: Vec3, max: Vec3, color: (face: Int) -> Rgba, skip: Int = 0) {
        val (x0, y0, z0) = min
        val (x1, y1, z1) = max
        val faces = listOf(
            listOf(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0)) to Vec3(-1f, 0f, 0f),
            listOf(Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x1, y0, z1)) to Vec3(1f, 0f, 0f),
            listOf(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1)) to Vec3(0f, -1f, 0f),
            listOf(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0)) to Vec3(0f, 1f, 0f),
            listOf(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y0, z0)) to Vec3(0f, 0f, -1f),
            listOf(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)) to Vec3(0f, 0f, 1f),
        )
        faces.forEachIndexed { face, (corners, normal) ->
            if (skip and (1 shl face) == 0) quad(corners[0], corners[1], corners[2], corners[3], normal, color(face))
        }
    }

    /**
     * A box with chamfered edges around [center]: flat faces in [face]
     * colour, bevels in [edge] colour, so light catches every edge.
     */
    fun bevelBox(center: Vec3, half: Vec3, bevel: Float, face: (axis: Int, sign: Int) -> Rgba, edge: Rgba, skipBottom: Boolean = true) {
        val h = floatArrayOf(half.x, half.y, half.z)
        fun point(values: FloatArray) = center + Vec3(values[0], values[1], values[2])
        fun axis(i: Int, s: Float) = FloatArray(3).also { it[i] = s }.let { Vec3(it[0], it[1], it[2]) }
        // Faces.
        for (a in 0..2) for (s in intArrayOf(-1, 1)) {
            if (skipBottom && a == 1 && s == -1) continue
            val u = (a + 1) % 3
            val v = (a + 2) % 3
            val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (su, sv) ->
                FloatArray(3).also {
                    it[a] = s * h[a]
                    it[u] = su * (h[u] - bevel)
                    it[v] = sv * (h[v] - bevel)
                }.let(::point)
            }
            quad(corners[0], corners[1], corners[2], corners[3], axis(a, s.toFloat()), face(a, s))
        }
        // Edges: between face (a, sa) and face (b, sb), running along the third axis.
        for (a in 0..2) for (b in a + 1..2) for (sa in intArrayOf(-1, 1)) for (sb in intArrayOf(-1, 1)) {
            if (skipBottom && ((a == 1 && sa == -1) || (b == 1 && sb == -1))) continue
            val c = 3 - a - b
            val normal = (axis(a, sa.toFloat()) + axis(b, sb.toFloat())).normalized()
            val corners = listOf(Triple(h[a], h[b] - bevel, -1), Triple(h[a], h[b] - bevel, 1), Triple(h[a] - bevel, h[b], 1), Triple(h[a] - bevel, h[b], -1))
                .map { (va, vb, sc) ->
                    FloatArray(3).also {
                        it[a] = sa * va
                        it[b] = sb * vb
                        it[c] = sc * (h[c] - bevel)
                    }.let(::point)
                }
            quad(corners[0], corners[1], corners[2], corners[3], normal, edge)
        }
        // Corners.
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            if (skipBottom && sy == -1) continue
            val s = floatArrayOf(sx.toFloat(), sy.toFloat(), sz.toFloat())
            val corners = (0..2).map { i ->
                FloatArray(3) { j -> s[j] * (if (j == i) h[j] else h[j] - bevel) }.let(::point)
            }
            flatTriangle(corners[0], corners[1], corners[2], Vec3(s[0], s[1], s[2]).normalized(), edge)
        }
    }

    /** A smooth ellipsoid around [center]. */
    fun sphere(center: Vec3, radii: Vec3, color: Rgba, slices: Int = 24, stacks: Int = 16) {
        val first = vertexCount
        for (i in 0..stacks) {
            val phi = PI * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI * j / slices
                val n = Vec3((sin(phi) * cos(theta)).toFloat(), cos(phi).toFloat(), (sin(phi) * sin(theta)).toFloat())
                val p = center + Vec3(n.x * radii.x, n.y * radii.y, n.z * radii.z)
                // The normal of an ellipsoid scales inversely with its radii.
                vertex(p, Vec3(n.x / radii.x, n.y / radii.y, n.z / radii.z).normalized(), color)
            }
        }
        val row = slices + 1
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = first + i * row + j
            val b = a + row
            val outward = (at(a) - center).normalized()
            if (i != 0) triangle(a, b, a + 1, outward)
            if (i != stacks - 1) triangle(a + 1, b, b + 1, outward)
        }
    }

    /** A flat ring lying on the ground, [height] thick, between radii [inner] and [outer]. */
    fun ring(center: Vec3, inner: Float, outer: Float, height: Float, color: Rgba, side: Rgba = color, segments: Int = 40) {
        for (k in 0 until segments) {
            val t0 = 2 * PI * k / segments
            val t1 = 2 * PI * (k + 1) / segments
            fun p(r: Float, t: Double, y: Float) = center + Vec3((r * cos(t)).toFloat(), y, (r * sin(t)).toFloat())
            quad(p(inner, t0, height), p(outer, t0, height), p(outer, t1, height), p(inner, t1, height), Vec3.UP, color)
            val out = Vec3(cos((t0 + t1) / 2).toFloat(), 0f, sin((t0 + t1) / 2).toFloat())
            quad(p(outer, t0, 0f), p(outer, t1, 0f), p(outer, t1, height), p(outer, t0, height), out, side)
            quad(p(inner, t0, 0f), p(inner, t0, height), p(inner, t1, height), p(inner, t1, 0f), out * -1f, side)
        }
    }

    /** An upright cylinder from [base], capped on top. */
    fun cylinder(base: Vec3, radius: Float, height: Float, color: Rgba, segments: Int = 12) {
        val top = base + Vec3(0f, height, 0f)
        for (k in 0 until segments) {
            val t0 = 2 * PI * k / segments
            val t1 = 2 * PI * (k + 1) / segments
            fun p(t: Double, y: Float) = base + Vec3((radius * cos(t)).toFloat(), y, (radius * sin(t)).toFloat())
            val out = Vec3(cos((t0 + t1) / 2).toFloat(), 0f, sin((t0 + t1) / 2).toFloat())
            quad(p(t0, 0f), p(t1, 0f), p(t1, height), p(t0, height), out, color)
            flatTriangle(top, p(t0, height), p(t1, height), Vec3.UP, color)
        }
    }

    /** Smallest box holding every vertex: min and max corners. */
    fun bounds(): Pair<Vec3, Vec3> {
        var min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in 0 until vertexCount) {
            val p = at(i)
            min = Vec3(minOf(min.x, p.x), minOf(min.y, p.y), minOf(min.z, p.z))
            max = Vec3(maxOf(max.x, p.x), maxOf(max.y, p.y), maxOf(max.z, p.z))
        }
        return min to max
    }
}
