package com.qtekfun.ultimatephone.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSimilarityTest {
    @Test
    fun `case accents order and punctuation do not matter`() {
        assertTrue(NameSimilarity.areSimilar("Ana García", "García, Ana"))
        assertTrue(NameSimilarity.areSimilar("Ana García", "ana garcia"))
        assertTrue(NameSimilarity.areSimilar("ANA  GARCÍA", "garcia.ana"))
        assertTrue(NameSimilarity.areSimilar("José Ñandú", "Nandu, Jose"))
        assertEquals(1.0, NameSimilarity.similarity("Ana García", "García, Ana"), 0.0)
    }

    @Test
    fun `different people are not similar`() {
        assertFalse(NameSimilarity.areSimilar("Ana García", "Ana Gómez"))
        assertFalse(NameSimilarity.areSimilar("Ana García", "Pedro García"))
        assertFalse(NameSimilarity.areSimilar("Luis Martín", "Luisa Martínez"))
        assertTrue(NameSimilarity.similarity("Ana García", "Ana Gómez") < 0.6)
    }

    @Test
    fun `one letter typo and swapped letters are tolerated`() {
        assertTrue(NameSimilarity.areSimilar("Ana García", "Ana Garcia"))
        assertTrue(NameSimilarity.areSimilar("Ana García", "Ana Garica")) // adjacent swap counts once
        assertTrue(NameSimilarity.areSimilar("Maria Fernandez", "Maria Fernandes"))
    }

    @Test
    fun `a name with an extra word is not the same`() {
        assertFalse(NameSimilarity.areSimilar("Ana García", "Ana García López"))
    }

    @Test
    fun `single words need an almost exact match`() {
        assertTrue(NameSimilarity.areSimilar("Mamá", "mama"))
        assertTrue(NameSimilarity.areSimilar("Alejandro", "alejandro"))
        assertFalse(NameSimilarity.areSimilar("Alejandro", "Alejandra")) // 1 edit of 9 letters = 0.89, below 0.90
        assertFalse(NameSimilarity.areSimilar("Ana", "Anna")) // too short to fuzzy match
        assertTrue(NameSimilarity.areSimilar("Ana", "ana")) // but equal is equal
    }

    @Test
    fun `names without letters never match`() {
        assertFalse(NameSimilarity.areSimilar("", ""))
        assertFalse(NameSimilarity.areSimilar("+34 600 123 456", "+34 600 123 456"))
        assertEquals("", NameSimilarity.canonical("600123456"))
        assertEquals(0.0, NameSimilarity.similarity("   ", "Ana"), 0.0)
    }

    @Test
    fun `canonical form sorts folded words`() {
        assertEquals("ana garcia", NameSimilarity.canonical("García, Ana"))
        assertEquals("a b c", NameSimilarity.canonical("c  B-a"))
    }

    @Test
    fun `distance counts edits and swaps`() {
        assertEquals(0, NameSimilarity.distance("abc", "abc"))
        assertEquals(3, NameSimilarity.distance("", "abc"))
        assertEquals(3, NameSimilarity.distance("abc", ""))
        assertEquals(1, NameSimilarity.distance("abc", "abd"))
        assertEquals(1, NameSimilarity.distance("abc", "acb"))
        assertEquals(3, NameSimilarity.distance("kitten", "sitting"))
    }
}
