# My Music

A personal, offline Spotify-style player for the audio files on your phone. Built with Kotlin,
Jetpack Compose (Material 3) and Media3. No accounts, no network, no third-party libraries.

## Install on your phone

Enable USB debugging, plug the phone in, then:

```bash
./gradlew installRelease
```

The release build is minified (~4 MB) and signed with your local debug key, so it installs
directly. Use `installDebug` while developing.

## Features

- **Library from MediaStore**: every music file (20 s or longer) on the device, grouped into
  albums and artists, with embedded cover art. Rescans automatically when files change.
- **Home**: All / Music / Artists filters, 2-column shortcut grid (recently played first),
  "Jump back in", "Recently added", "Your artists", and a daily-shuffled "Albums for you".
- **Search**: "Browse all" artist cards with tilted covers; ranked results with filters.
- **Your Library**: pinned Liked Songs and Local Files, filter chips with the animated ✕,
  Recents / Alphabetical / Creator sort, list and grid views.
- **Album, playlist and artist pages**: gradient tinted from the cover, cover parallax and fade,
  top bar that fades in its title, and a play button that pins under the top bar.
- **Player**: mini player tinted from the cover (swipe sideways to skip, drag up to open),
  full-screen Now Playing (drag down to close, swipe the cover to skip), shuffle and repeat
  modes, queue, liking with the pop animation, share.
- **Autoplay**: when the queue runs out (for example after a single), similar songs keep playing,
  shown under "Autoplay" in the queue.
- **Smart Shuffle**: the shuffle button cycles off → shuffle → Smart Shuffle (sparkle icon), which
  mixes recommended songs into the queue (marked with a sparkle) and orders it by what you're
  likely to enjoy.
- **Learns as you listen**: an on-device contextual bandit (Bayesian probit regression with
  Thompson sampling) predicts "will you finish this song?" from its artist, album, decade, length,
  time of day and the current session. Finishing or liking a song is a reward, skipping it in the
  first 30 s is a penalty; the model updates instantly, and after a skip the upcoming
  recommendations are re-ranked so similar songs move down. Stored in `files/taste_model.json`.
- **Background playback** with a media notification, lock-screen and Bluetooth controls, and
  pausing when headphones are unplugged.

## Structure

```
app/src/main/java/dev/novqigarrix/mymusic/
├── data/         MediaStore scanner, repositories (library, likes, recents), models
├── playback/     PlaybackService (ExoPlayer + MediaSession), PlayerConnection (MediaController),
│                 QueueDirector (autoplay, Smart Shuffle, skip tracking), QueueShuffleOrder
├── recommend/    TasteModel (online Bayesian bandit), features, Recommender
└── ui/
    ├── app/        root shell, permission screen, song options sheet
    ├── navigation/ type-safe routes, NavHost with Spotify-like transitions, bottom bar
    ├── home/ search/ library/ collection/ artist/ player/   features (ViewModel + screens)
    ├── components/ shared pieces (bounceClick Modifier.Node, rows, chips, collapsing header)
    ├── artwork/    MediaStore thumbnail loader + Palette tints
    └── theme/      colors, Figtree typography, spacing/motion tokens
```

Spotify's own font (Circular / Spotify Mix) is proprietary, so the app ships Figtree (SIL Open
Font License), which has very similar proportions.
