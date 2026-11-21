package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ModernAccountPlaylistTest {
    private val video = """{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Account like"}}}"""
    private fun modern(canonical: String? = null, selected: Boolean = true): JsonObject = Json.parseToJsonElement("""{
        ${canonical?.let { "\"microformat\":{\"microformatDataRenderer\":{\"urlCanonical\":\"https://www.youtube.com/playlist?list=$it\"}}," }.orEmpty()}
        "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":$selected,"content":{"richGridRenderer":{"contents":[$video]}}}}]},"secondaryContents":$video}}
    """).jsonObject
    @Test fun authenticatedLikedBrowseAcceptsModernPrimaryInsteadOfThrowing() {
        val primary = PlaylistContents.primary(modern(), "LL", requestBound = true)!!
        assertEquals(listOf("abcdefghijk"), YouTubeAccountLibraryParser.parse(primary, AccountEntryKind.VIDEO).items.map { it.id })
    }
    @Test fun canonicalBoundHtmlWorksWithoutAnExtraBrowseRequest() {
        assertNotNull(PlaylistContents.primary(modern("LL"), "LL"))
    }
    @Test fun unboundHtmlAndUnselectedTabsCannotBecomeTheLikedList() {
        assertNull(PlaylistContents.primary(modern(), "LL"))
        assertNull(PlaylistContents.primary(modern(selected = false), "LL", requestBound = true))
    }
    @Test fun explicitWrongIdentityIsRejectedEvenForBoundRequest() {
        try { PlaylistContents.primary(modern("PL_wrong"), "LL", requestBound = true); fail("Wrong identity accepted") }
        catch (_: IOException) { }
    }
    @Test fun signedOutBrowseIsNotShownAsAccountLikes() {
        val root = JsonObject(modern() + ("responseContext" to Json.parseToJsonElement("""{"mainAppWebResponseContext":{"loggedOut":true}}""")))
        try { PlaylistContents.primary(root, "LL", requestBound = true); fail("Signed-out response accepted") }
        catch (_: IOException) { }
    }
}
