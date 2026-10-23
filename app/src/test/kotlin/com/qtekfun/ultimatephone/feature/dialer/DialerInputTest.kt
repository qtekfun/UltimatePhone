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
    fun longPressOnZeroGivesPlusAtTheStartAndZeroElsewhere() {
        // A long press does not type the digit first: from an empty number it must give "+" on its own.
        assertEquals("+", DialerInput.longPress("", '0'))
        assertEquals("120", DialerInput.longPress("12", '0'))
    }

    @Test
    fun longPressOnStarAndPoundAddsAPauseAndAWait() {
        assertEquals("912345678,", DialerInput.longPress("912345678", '*'))
        assertEquals("912345678;", DialerInput.longPress("912345678", '#'))
        assertEquals("", DialerInput.longPress("", '*'))
        assertEquals("", DialerInput.longPress("", '#'))
        assertEquals("125", DialerInput.longPress("12", '5'))
    }

    @Test
    fun pausesNeedANumberBeforeThem() {
        assertEquals("", DialerInput.append("", ','))
        assertEquals("1;", DialerInput.append("1", ';'))
    }

    @Test
    fun secretCodesAreRecognised() {
        assertEquals("4636", DialerInput.secretCode("*#*#4636#*#*"))
        assertEquals(null, DialerInput.secretCode("*#06#"))
        assertEquals(null, DialerInput.secretCode("*#*#4636#*#"))
        assertEquals(null, DialerInput.secretCode("1*#*#4636#*#*"))
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
        assertEquals("912345678,22;5", DialerInput.sanitizePaste("912 345 678,22;5"))
        assertEquals("612", DialerInput.sanitizePaste(", 612"))
    }

    @Test
    fun searchUsesDigitsOnly() {
        assertEquals("34612", DialerInput.searchDigits("+34 612"))
        assertEquals("", DialerInput.searchDigits("*#"))
        assertEquals("912345678", DialerInput.searchDigits("912345678,22"))
    }
}
