package com.qtekfun.ultimatephone.feature.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class DialerInputTest {
    @Test
    fun appendsDialableKeysOnly() {
        assertEquals("12*#", "".let { "12*#".fold(it) { acc, c -> DialerInput.append(acc, c) } })
        assertEquals("1", DialerInput.append("1", 'x'))
        assertEquals("1", DialerInput.append("1", ' '))
    }

    @Test
    fun plusIsOnlyAcceptedAtTheStart() {
        assertEquals("+", DialerInput.append("", '+'))
        assertEquals("12", DialerInput.append("12", '+'))
    }

    @Test
    fun longPressOnZeroTurnsAFirstZeroIntoPlus() {
        assertEquals("+", DialerInput.plusInsteadOfZero("0"))
        assertEquals("120", DialerInput.plusInsteadOfZero("120"))
        assertEquals("", DialerInput.plusInsteadOfZero(""))
    }

    @Test
    fun backspaceRemovesTheLastCharacter() {
        assertEquals("12", DialerInput.backspace("123"))
        assertEquals("", DialerInput.backspace(""))
    }

    @Test
    fun lengthIsCapped() {
        val full = "1".repeat(DialerInput.MAX_LENGTH)
        assertEquals(full, DialerInput.append(full, '2'))
    }

    @Test
    fun pasteKeepsOnlyWhatCanBeDialled() {
        assertEquals("+34612345678", DialerInput.sanitizePaste(" +34 (612) 345-678 "))
        assertEquals("612345678", DialerInput.sanitizePaste("tel: 612.345.678"))
        assertEquals("+3456", DialerInput.sanitizePaste("+34+56"))
        assertEquals("", DialerInput.sanitizePaste("hello"))
    }

    @Test
    fun searchUsesDigitsOnly() {
        assertEquals("34612", DialerInput.searchDigits("+34 612"))
        assertEquals("", DialerInput.searchDigits("*#"))
    }
}
