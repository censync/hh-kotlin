package io.github.censync.hh

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The shape of the API as a caller written in Java sees it: static factories, constant fields, overloads. */
class JavaApiTest {
    private val bytes = ByteArray::class.java
    private val int = Int::class.javaPrimitiveType!!

    private fun assertStatic(owner: Class<*>, name: String, vararg parameters: Class<*>) {
        val method = owner.getMethod(name, *parameters)
        assertTrue(Modifier.isStatic(method.modifiers), "${owner.simpleName}.$name is not static")
    }

    @Test
    fun companionFunctionsAreStaticMethods() {
        val digest = BaseDigest::class.java
        for (name in listOf("of", "ofUtf8", "fromBytes", "ofOrNull", "ofUtf8OrNull", "fromBytesOrNull")) {
            assertStatic(digest, name, bytes)
        }
        for (name in listOf("ofHex", "ofText", "ofHexOrNull", "ofTextOrNull")) {
            assertStatic(digest, name, String::class.java)
        }
        assertStatic(SecretKey::class.java, "of", bytes)
        assertStatic(SecretKey::class.java, "ofOrNull", bytes)
        val fingerprint = Fingerprint::class.java
        assertStatic(fingerprint, "universal", digest)
        assertStatic(fingerprint, "keyed", digest, SecretKey::class.java)
        assertStatic(fingerprint, "keyedOrNull", digest, SecretKey::class.java)
        assertStatic(fingerprint, "fromBytes", bytes, Mode::class.java)
        assertStatic(fingerprint, "fromBytesOrNull", bytes, Mode::class.java)
        assertStatic(HhImage::class.java, "ofRgba", int, int, bytes)
        assertStatic(HhImage::class.java, "ofRgbaOrNull", int, int, bytes)
    }

    @Test
    fun constantsAreStaticFields() {
        val options = RenderOptions::class.java
        for (name in listOf("DEFAULT", "TRANSPARENT")) {
            val field = options.getField(name)
            assertTrue(Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers), name)
        }
        assertSame(RenderOptions.DEFAULT, options.getField("DEFAULT").get(null))
        assertSame(RenderOptions.TRANSPARENT, options.getField("TRANSPARENT").get(null))
        assertEquals(32, BaseDigest::class.java.getField("SIZE").get(null))
        assertEquals(32, Fingerprint::class.java.getField("SIZE").get(null))
        assertEquals(32, SecretKey::class.java.getField("SIZE").get(null))
        assertEquals(92, HhImage::class.java.getField("DEFAULT_JPEG_QUALITY").get(null))
        assertEquals(4096, HhImage::class.java.getField("MAX_DIMENSION").get(null))
    }

    @Test
    fun defaultArgumentsHaveOverloads() {
        val options = RenderOptions::class.java
        val fingerprint = Fingerprint::class.java
        val image = HhImage::class.java
        // Beside them the compiler keeps a synthetic constructor for Kotlin's own default arguments.
        val constructors = options.constructors.filter { !it.isSynthetic }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), constructors.map { it.parameterCount }.sorted())
        fingerprint.getMethod("render", int)
        fingerprint.getMethod("render", int, options)
        fingerprint.getMethod("renderOrNull", int)
        fingerprint.getMethod("renderOrNull", int, options)
        options.getMethod("measureContrast")
        options.getMethod("measureContrast", int)
        image.getMethod("encodeBmp")
        image.getMethod("encodeBmp", int)
        image.getMethod("encodeJpeg")
        image.getMethod("encodeJpeg", int)
        image.getMethod("encodeJpeg", int, int)
    }
}
