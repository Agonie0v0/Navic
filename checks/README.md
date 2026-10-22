# Android feature checks

Build the Android debug app, then run the dependency-free queue regression check:

```powershell
.\gradlew.bat :androidApp:assembleDebug
python checks/check_queue_search.py
python .github/scripts/check-zh-coverage.py
```

The queue check compiles the real filtering function with the Kotlin compiler already
cached by the Android build. It checks Chinese text, case-insensitive title/artist/album
matching, empty queries, absent metadata, duplicate occurrences, deletion and reordering.
It requires Python 3.11+ and the same Java used for Gradle.

Device acceptance (requires a connected Android device and a test music account):

- Select several songs, select/deselect all, cancel, and use Back to exit selection.
  Ordinary taps, long-press menus and swipe actions must still work outside selection.
- Add a selection to the queue in displayed order; cancel and confirm duplicate warnings.
- Add a selection to existing playlists and create a new playlist containing that selection.
  Cancel or fail a request and verify the selection remains available.
- Search a queue containing a repeated song; play and remove the second occurrence.
  Check paused playback, shuffle, an empty queue, no matches and clearing the query.
- Check keyboard, large text and landscape layouts. Reordering is available only after
  clearing the search; active playback and offline markers remain correct.

Use a test account for write operations. These checks do not imply that device or server
integration has been exercised by the command-line regression check.
