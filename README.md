# SimpleStream

A small personal Android streaming client built with Kotlin, Jetpack Compose Material 3 and AndroidX Media3.

## Features

- Material 3 interface
- TMDB API key and access-token settings
- Search movies and TV metadata through TMDB
- Add multiple remote JSON source manifests with the + button
- Search loaded source items
- Paste and play a direct HTTP(S) media URL
- Media3 player for supported streaming formats
- Local settings storage
- GitHub Actions debug APK build

## Source manifest

SimpleStream accepts declarative JSON manifests:

{
  "name": "My Source",
  "items": [
    {
      "id": "demo-1",
      "title": "My Video",
      "type": "Video",
      "year": "2026",
      "poster": "https://example.com/poster.jpg",
      "description": "Description",
      "streamUrl": "https://example.com/video.m3u8"
    }
  ]
}

Remote manifests are data only. The app does not download or execute arbitrary native plugin code.

## Important

The player can open URLs supported by AndroidX Media3 and accessible to the user. DRM, authentication flows, unsupported protocols and provider-specific resolvers require dedicated implementations.
