package es.maciptv.android

object DemoContent {
    private const val stream = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    val channels = listOf(
        Channel("demo-nature", "Naturaleza · Demo", stream, "Demostración", tvgId = "demo.nature"),
        Channel("demo-news", "Noticias · Demo", stream, "Demostración", tvgId = "demo.news", order = 1),
        Channel("demo-cinema", "Cine · Demo", stream, "Cine de prueba", tvgId = "demo.cinema", order = 2)
    )
    fun guide(): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val anchor = now - now % 2_700_000L
        val titles = listOf("Reportajes de demostración", "Informativo ficticio", "Película de prueba", "Documental simulado")
        return channels.associate { channel -> channel.tvgId to (-8..12).map { offset ->
            val start = anchor + offset * 2_700_000L
            Programme(channel.tvgId, titles[kotlin.math.abs(offset) % titles.size] + " · demo",
                "Programación ficticia de demostración.", start, start + 2_700_000L)
        } }
    }
}
