package dev.tinycast.android

/** Non-snapshot bookkeeping: reading scroll layout must not cause a recompose loop. */
internal class PaletteScrollHistory {
    private var query: String? = null
    private var category: String? = null
    private var ids = emptyList<String>()
    private var selected = -1

    fun update(query: String, category: String, ids: List<String>, selected: Int, fullyVisible: Boolean): Boolean {
        val dataChanged = this.query != query || this.category != category || this.ids != ids
        val moved = this.selected != selected
        this.query = query; this.category = category; this.ids = ids; this.selected = selected
        return dataChanged || (moved && !fullyVisible)
    }
}
