package com.simplestream.app

object DefaultMovieLibrary {
    val items: List<MediaItem> = listOf(
        MediaItem(
            id = "movie_bbb",
            title = "Big Buck Bunny",
            type = "Movie",
            year = "2008",
            genre = "Animation, Comedy",
            runtime = "10 min",
            description = "A large and lovable rabbit deals with bullying forest creatures in this iconic open-movie classic produced by the Blender Foundation.",
            poster = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1280&q=80",
            provider = "Blender Foundation",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("1080p MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "video/mp4"),
                StreamVariant("Auto HLS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "application/x-mpegURL")
            ),
            subtitles = listOf(
                SubtitleTrack("English", "https://bitmovin-a.akamaihd.net/content/sintel/subtitles/subtitles_en.vtt", "en", "text/vtt")
            )
        ),
        MediaItem(
            id = "movie_tears_of_steel",
            title = "Tears of Steel",
            type = "Movie",
            year = "2012",
            genre = "Sci-Fi, Action",
            runtime = "12 min",
            description = "In a dystopian future, a group of warriors and scientists gather at the Oude Kerk in Amsterdam to stage a crucial event from the past to save the world from destructive robots.",
            poster = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&q=80",
            provider = "Blender Foundation",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("1080p MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "video/mp4"),
                StreamVariant("HLS Stream", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "application/x-mpegURL")
            )
        ),
        MediaItem(
            id = "movie_sintel",
            title = "Sintel",
            type = "Movie",
            year = "2010",
            genre = "Animation, Fantasy, Adventure",
            runtime = "15 min",
            description = "A lonely young woman searches the world for a baby dragon she befriended and nursed back to health, leading to an emotional confrontation.",
            poster = "https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?w=1280&q=80",
            provider = "Blender Foundation",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("1080p MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "video/mp4"),
                StreamVariant("Multi-Audio HLS", "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8", "application/x-mpegURL")
            ),
            subtitles = listOf(
                SubtitleTrack("English", "https://bitmovin-a.akamaihd.net/content/sintel/subtitles/subtitles_en.vtt", "en", "text/vtt")
            )
        ),
        MediaItem(
            id = "movie_elephants_dream",
            title = "Elephants Dream",
            type = "Movie",
            year = "2006",
            genre = "Sci-Fi, Animation",
            runtime = "11 min",
            description = "Proog and Emo explore the surreal, mechanical labyrinth of an immense machine that embodies the infinite creation and complex mechanics of mind.",
            poster = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1280&q=80",
            provider = "Orange Open Movie Project",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("1080p MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "video/mp4")
            )
        ),
        MediaItem(
            id = "movie_cosmos_laundromat",
            title = "Cosmos Laundromat",
            type = "Movie",
            year = "2015",
            genre = "Animation, Fantasy, Drama",
            runtime = "12 min",
            description = "On a desolate island, a suicidal sheep named Franck meets a quirky salesman who offers him the adventure of a lifetime.",
            poster = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=1280&q=80",
            provider = "Blender Animation Studio",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("1080p MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "video/mp4")
            )
        ),
        MediaItem(
            id = "movie_night_living_dead",
            title = "Night of the Living Dead",
            type = "Movie",
            year = "1968",
            genre = "Horror, Mystery, Classic",
            runtime = "96 min",
            description = "A disparate group of individuals takes refuge in an abandoned farmhouse when corpses begin to leave the graveyard in search of fresh human bodies.",
            poster = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=1280&q=80",
            provider = "Public Domain Classic",
            streamUrl = "https://archive.org/download/night_of_the_living_dead/night_of_the_living_dead_512kb.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("Direct MP4", "https://archive.org/download/night_of_the_living_dead/night_of_the_living_dead_512kb.mp4", "video/mp4")
            )
        ),
        MediaItem(
            id = "movie_carnival_of_souls",
            title = "Carnival of Souls",
            type = "Movie",
            year = "1962",
            genre = "Horror, Mystery, Cult",
            runtime = "78 min",
            description = "After a traumatic car accident, a church organist finds herself drawn to a mysterious, abandoned pavilion on the shores of the Great Salt Lake.",
            poster = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=1280&q=80",
            provider = "Public Domain Classic",
            streamUrl = "https://archive.org/download/CarnivalOfSouls_524/CarnivalOfSouls_512kb.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("Direct MP4", "https://archive.org/download/CarnivalOfSouls_524/CarnivalOfSouls_512kb.mp4", "video/mp4")
            )
        ),
        MediaItem(
            id = "movie_charade",
            title = "Charade",
            type = "Movie",
            year = "1963",
            genre = "Mystery, Romance, Thriller",
            runtime = "113 min",
            description = "A woman is pursued by several men who want a fortune her murdered husband had stolen. Whom can she trust in the City of Lights?",
            poster = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80",
            backdrop = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1280&q=80",
            provider = "Public Domain Classic",
            streamUrl = "https://archive.org/download/charade_1963/charade_512kb.mp4",
            mimeType = "video/mp4",
            streams = listOf(
                StreamVariant("Direct MP4", "https://archive.org/download/charade_1963/charade_512kb.mp4", "video/mp4")
            )
        )
    )
}
