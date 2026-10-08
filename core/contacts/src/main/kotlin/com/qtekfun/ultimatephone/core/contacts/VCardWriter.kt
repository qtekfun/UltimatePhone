package com.qtekfun.ultimatephone.core.contacts

import java.util.Base64

/**
 * Writes vCard 3.0 (RFC 2426): CRLF line ends, text escaped, lines folded at 75 octets, photo as base64.
 * Custom phone/e-mail labels use the widely understood `itemN.X-ABLabel` group form. Pure.
 */
object VCardWriter {
    private const val EOL = "\r\n"
    private const val MAX_OCTETS = 75

    fun write(contacts: Iterable<VCardContact>, out: Appendable) {
        for (contact in contacts) write(contact, out)
    }

    fun write(contact: VCardContact, out: Appendable) {
        val lines = ArrayList<String>()
        lines += "BEGIN:VCARD"
        lines += "VERSION:3.0"
        val name = contact.name
        lines += "N:" + listOf(name.family, name.given, name.middle, name.prefix, name.suffix).joinToString(";") { escape(it) }
        lines += "FN:" + escape(formattedName(contact))
        if (contact.nicknames.isNotEmpty()) lines += "NICKNAME:" + contact.nicknames.joinToString(",") { escape(it) }
        if (contact.organization.isNotBlank()) lines += "ORG:" + escape(contact.organization)
        if (contact.title.isNotBlank()) lines += "TITLE:" + escape(contact.title)
        var group = 0
        for (phone in contact.phones) {
            val custom = customLabel(phone)
            val prefix = if (custom != null) "item${++group}." else ""
            lines += "${prefix}TEL;TYPE=${phoneType(phone.type)}:${escape(phone.value)}"
            if (custom != null) lines += "item$group.X-ABLabel:${escape(custom)}"
        }
        for (email in contact.emails) {
            val custom = customLabel(email)
            val prefix = if (custom != null) "item${++group}." else ""
            lines += "${prefix}EMAIL;TYPE=INTERNET${emailType(email.type)}:${escape(email.value)}"
            if (custom != null) lines += "item$group.X-ABLabel:${escape(custom)}"
        }
        for (address in contact.addresses) lines += "ADR;TYPE=${addressType(address.type)}:;;${escape(address.formatted)};;;;"
        contact.websites.forEach { lines += "URL:" + escape(it) }
        contact.birthday?.let { lines += "BDAY:" + it.format() }
        if (contact.note.isNotBlank()) lines += "NOTE:" + escape(contact.note)
        contact.photo?.let { lines += "PHOTO;ENCODING=b;TYPE=${photoType(it)}:" + Base64.getEncoder().encodeToString(it.bytes) }
        lines += "END:VCARD"
        for (line in lines) {
            fold(line, out)
        }
    }

    fun toString(contacts: Iterable<VCardContact>): String = StringBuilder().also { write(contacts, it) }.toString()

    private fun formattedName(contact: VCardContact): String = contact.displayName.ifBlank {
        contact.organization.ifBlank { contact.phones.firstOrNull()?.value ?: contact.emails.firstOrNull()?.value.orEmpty() }
    }

    private fun customLabel(value: LabeledValue): String? = value.customLabel?.takeIf { value.type == LabelTypes.CUSTOM && it.isNotBlank() }

    private fun phoneType(type: Int) = when (type) {
        LabelTypes.MOBILE -> "CELL"
        LabelTypes.HOME -> "HOME"
        LabelTypes.WORK -> "WORK"
        LabelTypes.MAIN -> "MAIN"
        else -> "VOICE"
    }

    private fun emailType(type: Int) = when (type) {
        LabelTypes.EMAIL_HOME -> ",HOME"
        LabelTypes.EMAIL_WORK -> ",WORK"
        LabelTypes.EMAIL_MOBILE -> ",CELL"
        else -> ""
    }

    private fun addressType(type: Int) = when (type) {
        VCardAddress.ADDRESS_HOME -> "HOME"
        VCardAddress.ADDRESS_WORK -> "WORK"
        else -> "POSTAL"
    }

    private fun photoType(photo: VCardPhoto) = photo.mimeType.substringAfter('/').uppercase()

    /** Escapes the characters that have a meaning in vCard text. */
    fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        for (c in text) {
            when (c) {
                '\\' -> out.append("\\\\")
                ';' -> out.append("\\;")
                ',' -> out.append("\\,")
                '\n' -> out.append("\\n")
                '\r' -> Unit
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** Appends [line] folded so no physical line passes 75 UTF-8 octets; a character is never split. */
    private fun fold(line: String, out: Appendable) {
        var octets = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val size = utf8Size(cp)
            if (octets + size > MAX_OCTETS) {
                out.append(EOL).append(' ')
                octets = 1
            }
            out.appendCodePoint(cp)
            octets += size
            i += Character.charCount(cp)
        }
        out.append(EOL)
    }

    private fun utf8Size(cp: Int) = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    private fun Appendable.appendCodePoint(cp: Int): Appendable {
        if (Character.isBmpCodePoint(cp)) append(cp.toChar()) else append(Character.highSurrogate(cp)).append(Character.lowSurrogate(cp))
        return this
    }
}
