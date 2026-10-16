# Android Remote Controller: Notes for the Video Report

Written for teammates who don't work on the Android side. Every claim below points to a file
(paths are relative to `app/src/main/`). Line numbers refer to the `improve-UI` branch at
commit `6712c8f`.

Quick orientation: the app is a single activity with a bottom navigation bar and five tabs:
**Terminal, Control, Wheel, Arena, Task 2** (`res/menu/bottom_nav_menu.xml`,
`res/navigation/nav_graph.xml`). It is built with classic Android Views (XML layouts plus
ViewBinding), not Jetpack Compose (`app/build.gradle.kts:256-258`). The arena and the Task 2
track are custom-drawn views.

---

## 1. Theme

- **A "games console" look, built on Material 3.** The base theme is `Theme.Material3.Light.NoActionBar`
  with a Material3 Dark variant (`res/values/themes.xml:3`, `res/values-night/themes.xml:3`). The
  colours are deliberately Nintendo-Switch inspired (`res/values/colors.xml:6-8`):
  - Primary `#FF3C28`, "Joy-Con neon red": the main buttons, the sent-message bubbles and the Begin Explore button.
  - Secondary `#0AB9E6`, "Joy-Con neon blue": the robot on the arena (`arena_robot_fill`, `colors.xml:84`).
  - Tertiary `#F5C400`, gold: the Begin Fastest button, the obstacle target-face stripe and the "connecting" status dot.
  - Background `#F4F4F7` (light) and `#151316` charcoal "console body" (dark), `colors.xml:21,48`.
- **Full light/dark mode support.** Every Material role has a hand-tuned dark value
  (`colors.xml:34-61`). The arena grid also changes with the theme through custom attributes
  `arenaCellFill`/`arenaGridLine`: light blue `#DCF4FF` with white lines in light mode, deep teal
  `#0F2A33` in dark mode (`res/values/attrs.xml`, `colors.xml:76-79`). In dark mode the error
  colour was shifted toward rose so it doesn't look like the primary red (`colors.xml:56-58`).
- **Some parts are themed after real objects and stay the same in both modes.** The Control tab is an
  Xbox-style gamepad with Y/X/B/A colours (`colors.xml:110-118`, `res/layout/fragment_control.xml:68-72`).
  The Wheel tab is an F1 steering wheel with a dashboard "screen" (`res/layout/fragment_wheel.xml:22-25`).
  The Task 2 tab is a racetrack with asphalt, grass and red/white curbs, glowing pill buttons and a
  chrome-bezel "ENGINE LOG" (`colors.xml:93-104`, `res/drawable/glow_*.xml`, `res/drawable/bg_engine_log_frame.xml`).
- **Typography and icons:** default Roboto with Material3 text styles throughout (e.g.
  `TextAppearance.Material3.TitleSmall`). There is no custom font, but the message log uses
  `monospace` for a terminal feel (`res/layout/item_message_sent.xml:29`, `item_message_received.xml:29`).
  The icons are custom vector drawables (`res/drawable/ic_*.xml`).
- **How the arena is drawn** (`java/.../ui/arena/ArenaView.kt`): a 20×20 grid with ruler-style axis
  numbers 0–20, where 0 is the bottom-left corner (`:228-251`). Obstacles are dark rounded squares
  `#2B2E38` showing their obstacle number in white. Once the robot recognises the image, the number
  is replaced by the image ID **in green** (`:344-351`). A thick **gold stripe** marks which side
  carries the image (`:358-363`). The robot is a 3×3 translucent blue footprint with a blue
  **triangle pointing the way it faces** (`:378-408`).

## 2. Distinguishing features

| Feature | What it does | File |
|---|---|---|
| Connection status strip | A bar on every tab with a coloured dot (grey = off, yellow = connecting/reconnecting, green = connected), the device name, a spinner, and a Connect button or a Disconnect menu | `ui/main/MainActivity.kt:116-167`, `res/layout/activity_main.xml:12-150` |
| Robot status chip | Shows what the robot says it is doing ("Exploring", "Turning Left", …) with a matching icon. It is fed by `{"status":"..."}` messages from the RPi | `ui/main/MainActivity.kt:169-183`, `bluetooth/BluetoothModels.kt:26-37`, `bluetooth/AndroidBluetoothController.kt:723-731` |
| Device picker | A bottom sheet listing paired and nearby devices with a live scan. It has its own screens for "Bluetooth off" and "permission missing", each with a fix-it button | `ui/devicepicker/DeviceListBottomSheet.kt:82-173` |
| Listen mode | "Wait for incoming connection": the tablet becomes the server so AMD Tool can connect to it. The sheet closes by itself once a connection arrives | `DeviceListBottomSheet.kt:74-76,105-110`, `AndroidBluetoothController.kt:234-294` |
| Connect mode | The tablet connects out to the RPi. It tries three socket types in turn, each with a 15-second time limit | `AndroidBluetoothController.kt:218-231,524-579` |
| Auto-reconnect | If the link drops (not a user disconnect), it retries with backoff (1, 2, 4, 8, 16 s, then every 16 s). The status strip shows "Reconnecting (attempt N)" | `AndroidBluetoothController.kt:639-673` |
| "Reconnect to last device" | Remembers the last device across app restarts and offers it at the top of the picker if it isn't already listed as paired | `AndroidBluetoothController.kt:765-777`, `DeviceListBottomSheet.kt:150-155` |
| Stays connected in background | Keeps a foreground service and notification running while connected, so Android doesn't kill the app and drop the link | `bluetooth/BluetoothForegroundService.kt`, `AndroidManifest.xml:54-57` |
| Terminal / message log | Chat-style bubbles (sent in red, received in grey, system lines in the middle) with HH:mm:ss timestamps, plus free-text send. The same log appears on the Terminal, Wheel and Task 2 tabs | `ui/terminal/TerminalFragment.kt`, `ui/terminal/MessageAdapter.kt` |
| Gamepad controls (Control tab) | Xbox-style pad: D-pad for forward, reverse and rotate; X/B for strafe. Each press also moves the robot one cell on the map. Shows the "Last command" sent | `ui/control/ControlFragment.kt:61-84,154-163`, `res/layout/fragment_control.xml` |
| Mission buttons | Begin Explore and Begin Fastest (each asks for confirmation), Send Arena, Send Robot Location, Calibrate Straight, Reset Arena (asks for confirmation) | `ControlFragment.kt:86-137` |
| Controls lock when offline | Buttons fade out and stop working while disconnected, and a "Not connected" banner appears | `ControlFragment.kt:144-179` |
| Wheel tab (all-in-one cockpit) | Arena, terminal and controls on one screen, styled as an F1 wheel. It reuses the real Arena screen instead of copying it | `ui/wheel/WheelFragment.kt:66-84,105-117`, `res/layout/fragment_wheel.xml` |
| Press-and-hold obstacle placement | Hold on an empty cell, slide to line it up using **guide lines, highlighted axis numbers and an "(x, y)" badge**, then release to place. The phone gives a small vibration each time it snaps to a new cell | `ArenaView.kt:256-309,436-441,489-502` |
| Image-face picker | After placing, a compass-style dialog (N/E/S/W buttons) asks which side has the image | `ui/arena/ArenaFragment.kt:93-133`, `res/layout/dialog_target_face.xml` |
| Drag to move / drag off to delete | Drag an obstacle to move it. A green outline means the spot is valid; red means it overlaps another obstacle. Dropping it outside the grid deletes it and sends `OBSTACLE_REMOVE` | `ArenaView.kt:366-376,445-466`, `viewmodel/ArenaViewModel.kt:59-74` |
| Tap to change image face | Tapping an obstacle (without dragging) reopens the face picker and re-sends `OBSTACLE,…` | `ArenaView.kt:527-532`, `ArenaFragment.kt:103-111` |
| Remove tool / Robot tool | Remove deletes an obstacle on tap. Robot moves the robot to the tapped cell. Tapping the robot itself rotates it. There are also rotate buttons under the grid | `ArenaView.kt:528-539`, `ArenaFragment.kt:84-88` |
| Live image-recognition results | `TARGET,<obstacle>,<imageId>` from the RPi turns that obstacle's label green with the recognised ID | `BluetoothModels.kt:48-59`, `ArenaFragment.kt:153-157`, `ArenaViewModel.kt:89-94` |
| Live robot position | `ROBOT,<x>,<y>,<N/E/S/W>` from the RPi moves and turns the robot on the map. Positions that would put it out of bounds are ignored | `BluetoothModels.kt:97-108`, `ArenaFragment.kt:158-162`, `ArenaViewModel.kt:139-143` |
| Send Arena (whole map) | Sends a `ROBOT,…` line followed by one `OBSTACLE,…` line per obstacle, one at a time, in order | `arena/ArenaProtocol.kt:38-42`, `AndroidBluetoothController.kt:358-402` |
| Task 2 track view | A carpark and two obstacles drawn as a racetrack. Each obstacle shows "?" until `TARGET,1/2,LEFT/RIGHT` arrives, then a big arrow appears and a dashed route loops around it. The two loops together form a figure-8 | `ui/task2/Task2ArenaView.kt`, `ui/task2/Task2Fragment.kt` |
| Task 2 reveal animation | When a direction arrives, a little robot drives along the newly drawn loop (0.9 s, one time only) | `Task2ArenaView.kt:155-170,413-439` |
| Task 2 run buttons | Glowing CALIBRATE (`calibStraight`), GYRO INIT/CALIB, START RACE (sends exactly the 5 bytes `START`), and Reset (asks for confirmation; only clears the screen) | `Task2Fragment.kt:79-121`, `res/layout/fragment_task2.xml` |
| Task 2 run time | When the RPi sends `DONE,TASK2`, the log shows "Task 2 time: X.XXX s", measured from the `START` the RPi echoed back. There is **no live on-screen stopwatch** | `AndroidBluetoothController.kt:713-720`, `viewmodel/Task2ViewModel.kt:48-51,71-72` |
| Error feedback | Bluetooth errors (send failed, permission denied, BT off, connect failed) appear as Snackbars or Toasts | `MainActivity.kt:107-111`, `TerminalFragment.kt:90-94` |

**Persistence:** the only thing saved is the last connected device (SharedPreferences,
`AndroidBluetoothController.kt:765-777`). There are **no saved arenas or presets**. The arena
survives tab switches and screen rotation because it lives in a ViewModel, but it is lost if the
app is killed.

## 3. What makes it stand out (ranked by how good it looks on video)

1. **Polished arena editing.** The baseline (C.6/C.7) only needs "place an obstacle and set its face".
   Ours adds press-and-hold placement with crosshair guide lines, highlighted axis numbers, a floating
   "(x, y)" badge, a small vibration on every cell snap, green/red valid-position outlines,
   drag-off-to-delete, and tap-to-edit-face that works whatever tool is selected
   (`ArenaView.kt:256-309,500-546`). This looks great slowed down on video.
2. **Three themed control screens.** The Xbox-style gamepad (Control), the F1-wheel cockpit that puts
   arena, log and controls on one screen (Wheel), and the racetrack Task 2 screen with glowing buttons,
   an animated route reveal and an "ENGINE LOG". A baseline would have plain buttons. The Wheel tab
   embeds the actual Arena screen rather than a copy, so behaviour is identical (`WheelFragment.kt:66-84`).
3. **Both Bluetooth directions.** The tablet can connect **out** to the RPi (`connect()`) or **listen**
   so AMD Tool can connect in (`startListening()`), using the same code to send and receive afterwards
   (`AndroidBluetoothController.kt:218-294`). The outgoing connection tries three socket methods
   (secure, then insecure, then a reflection "channel 1" fallback), each with a time limit, so a hung
   attempt can't block the others (`:524-579`).
4. **Resilient link.** It reconnects automatically with backoff (`:649-673`), runs a foreground service
   so Android doesn't kill the connection in the background (`BluetoothForegroundService.kt:19-26`), and
   a read loop that still shows messages without a trailing newline (150 ms idle flush, `:589-637`). A
   locked "send in order" path waits for the peer's echo, so multi-line map uploads don't merge into one
   message (`:358-424`). There is also a global crash guard for Bluetooth stacks from phone makers that
   throw unexpected exceptions (`:63-78`).
5. **Clean architecture.** Each layer has its own job.
   - The Bluetooth layer is an interface (`bluetooth/BluetoothController.kt`) that publishes Kotlin `StateFlow`/`SharedFlow` streams.
   - Activity-wide ViewModels (`BluetoothViewModel`, `ArenaViewModel`, `Task2ViewModel`) keep the connection and map alive across tabs.
   - `ArenaView` only draws and reports gestures. `ArenaViewModel` holds the map state with no Bluetooth knowledge. `ArenaFragment` is the only part that turns changes into protocol messages (`ArenaFragment.kt:24-30`).
   - Task 1 and Task 2 share one `TARGET` parser that decides which task a line belongs to (`BluetoothModels.kt:44-60`).
6. **Live status everywhere.** The connection state (four states, colour-coded) and the robot's
   reported activity show in a strip on every tab (`MainActivity.kt:116-183`). The Task 2 tab has its
   own compact connection pill (`Task2Fragment.kt:160-183`).

**Honest caveats (please don't oversell these):**
- "Tablet-optimised" mostly means sized by hand. Only the Task 2 layout mentions the Galaxy Tab A7 Lite
  (`res/layout/fragment_task2.xml:2-4`). There are no tablet- or landscape-specific resource folders,
  and the gamepad uses fixed dp sizes (`fragment_control.xml:74-77`).
- The only animation is the Task 2 route reveal. The arena feedback is haptic and visual, not animated.
- Unit tests cover only the `TARGET` parser and the Task 2 ViewModel, 5 tests each
  (`src/test/.../TargetMessageTest.kt`, `Task2ViewModelTest.kt`).
- Control and Wheel repeat the same button-handling code instead of sharing it (`ControlFragment.kt` vs `WheelFragment.kt`).
- On the gamepad, Y, A, both sticks and the view/home/menu dots are decorative and do nothing (`colors.xml:106-109`).

## 4. Challenges & solutions

| Challenge | Root cause | How we fixed it | Status |
|---|---|---|---|
| AMD Tool couldn't connect: the tablet was only a client | AMD Tool's "As client" mode connects **to** the tablet, so the tablet has to act as a server | Added Listen mode (`listenUsingRfcommWithServiceRecord` + `accept()`) and kept outgoing `connect()` for the RPi. Both lead into the same read loop (`AndroidBluetoothController.kt:218-294`; picker row `DeviceListBottomSheet.kt:74-76`). Commit `384417b` | **Fixed** |
| AMD Tool received commands but didn't recognise them | `sendMessage()` added `"\n"`, and AMD Tool matches command text exactly | Removed the newline in `27468dd`. Today `sendMessage()` writes the bytes exactly as given (`:321-356`), and the command strings match AMD Tool's defaults (`bluetooth/RobotCommands.kt:3-9`) | **Fixed** |
| Incoming messages without `\n` never showed up | The read loop only split on newlines, so a message without one sat in the buffer | A watchdog flushes leftover bytes after 150 ms of silence, and anything left is flushed on disconnect (`:589-637`, constants `:790-791`). Commit `c250a39` | **Fixed** |
| Android 12+ Bluetooth runtime permissions | API 31+ needs `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` granted at runtime; older versions need legacy perms + location | Manifest declares both sets (`AndroidManifest.xml:9-26`, `maxSdkVersion=30` on legacy, `neverForLocation` on scan). `BluetoothPermissions.required()` picks the right set (`bluetooth/BluetoothPermissions.kt:12-23`). The picker shows a "Grant permission" screen (`DeviceListBottomSheet.kt:38-40,71-73,131-132`). Every call checks first (`:733-753`) | **Fixed** (in place since the initial commit) |
| Android 13/14 receiver export flag | Newer Android requires an explicit export flag on receivers registered at runtime | `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (`:171`). **Note:** the code uses NOT_EXPORTED, not EXPORTED. That's the right choice for system Bluetooth broadcasts, so describe it as "we set the export flag", not "RECEIVER_EXPORTED" | **Fixed** (differs from the description; in place since the initial commit) |
| Socket leaks in the fallback connection chain | A failed or hung `connect()` attempt left its socket open | Each attempt is raced against a 15 s timer. On failure or timeout, the socket is force-closed, which also unblocks a stuck `connect()` (`:553-579`). `close()` errors are caught as `Exception`, because some vendor stacks throw non-IOExceptions. Commit `384417b` | **Fixed** for the fallback chain (a smaller leak remains elsewhere; see Gaps) |
| ArenaView aspect ratio after `setState()` | The claim: `setState()` calls only `invalidate()`, not `requestLayout()` | **Not changed.** `setState()` still calls only `invalidate()` (`ArenaView.kt:177-181`), and `requestLayout` appears nowhere in the git history. In practice it's harmless: the arena is always 20×20, so the size set in `onMeasure` (`:183-188`) never needs recomputing | **Not in code** (no visible bug today) |
| Tap flicker from the drag ghost | The ghost was drawn because `dragState` was set on finger-down | Guide lines and the badge only appear once `isDragging` is true (`:256-264`), and finger-down no longer redraws. **But** the ghost outline and the faded obstacle are still drawn whenever `dragState` exists, without checking `isDragging` (`:203-207`). They can still flash if a redraw happens mid-tap | **Partial** |
| Connecting hung against AMD Tool's Windows stack | `BluetoothSocket.connect()` has no timeout, and the secure handshake to a .NET SPP server can hang, so the fallbacks never ran | A 15 s time limit per attempt, then move on to the next method (`:514-579`). Commit `384417b` | **Fixed** |
| Link "timed out" when the app sat in the background | Android killed the background process, taking the socket with it | A foreground service of type `connectedDevice` with an ongoing notification while connected (`BluetoothForegroundService.kt`, `:290,508`). Commit `097e89b` | **Fixed** |
| Multi-message uploads merged into one line | Messages have no separator, so back-to-back writes ran together on the receiving side. Separately, concurrent writes could mix their bytes | A `Mutex` around every write (`b51e9ae`, `:79-84`). `sendSequence()` holds the lock for the whole batch, waits up to 1 s for the peer to echo each message, and leaves 300 ms between messages (`79186ae`, `:358-424`) | **Fixed** |
| Drag didn't work unless no tool was selected; couldn't add after removing | Gestures were tied to whichever tool was selected, and the toggle group fired stale "unchecked" events | Drag and tap now work whatever the tool. The tool is read from the group's `checkedButtonId` instead of single events (`ArenaFragment.kt:57-68`, `ArenaView.kt:513-532`). Commit `9634606` | **Fixed** |
| Task 1 and Task 2 both use `TARGET,…` | The same prefix carries different meanings | One parser: `LEFT`/`RIGHT` on obstacle 1 or 2 means Task 2; anything else is a Task 1 image ID; malformed lines are ignored and logged (`BluetoothModels.kt:44-60`, `:705-711`). Unit-tested. Commit `1278619` | **Fixed** |
| Task 2 `START` had to be exactly 5 bytes | The RPi matches the raw payload byte-for-byte | `sendRaw()` sends exact bytes and logs control characters visibly, e.g. `[START\n]` (`:326-332`, `RobotCommands.kt:26-28`) | **Fixed** |
| Task 2 arrows lost when rotating the screen | Collecting in the fragment's view lifecycle drops events during rotation | Collection moved into the ViewModel's own lifetime (`Task2ViewModel.kt:38-41`) | **Fixed** |
| Coordinate convention changes | Obstacle size went from 4×4 to 1×1, and the robot origin moved from its centre to its bottom-left cell, to match the protocol | Commits `38b0122`, `96e7c45`, `922a866`; `ArenaModels.kt:44-46,80-82` | **Fixed** (some comments are now out of date; see Gaps) |

## 5. Suggested shots for the video

| Point to show | Shot |
|---|---|
| Theme | Flip the tablet between light and dark mode on the Arena tab (the grid recolours), then swipe through Control (gamepad), Wheel (F1) and Task 2 (racetrack) |
| Connecting (both modes) | Open the picker from the status strip. (a) Tap the RPi and watch the dot go yellow, then green, with "Connected to … (via secure)" in the log. (b) Tap "Wait for incoming connection", connect from AMD Tool, and the sheet closes by itself |
| Auto-reconnect | Power-cycle or walk out of range of the RPi. The strip shows "Reconnecting (attempt 1, 2…)", then goes green again without anyone touching the tablet |
| Arena editing | Close-up, ideally slowed down: press-and-hold an empty cell and slide it around (crosshair, highlighted axes, "(x, y)" badge), release, pick a face, and show `OBSTACLE,1,x,y,N` in the log. Drag it onto another obstacle (red outline), then off the grid (it's deleted, `OBSTACLE_REMOVE,1`). Tap an obstacle to change its face |
| Live updates (C.9/C.10) | Send `TARGET,1,11` then `ROBOT,5,5,E` from AMD Tool or the RPi. The obstacle label turns green "11" and the robot jumps and turns to face east |
| Manual control | On Control, press the D-pad and X/B. The robot steps on the Arena/Wheel map and "Last command: …" updates. Then disconnect to show the buttons fading out |
| Wheel cockpit | One continuous take driving a run from the Wheel tab only: arena, log and controls together |
| Send Arena | Place 3–4 obstacles, tap Send Arena, and show the lines arriving one at a time in the log and on the receiver |
| Task 2 | Tap START RACE. When `TARGET,1,LEFT` arrives, the arrow appears and the robot animates around obstacle 1. Then `TARGET,2,RIGHT`, then `DONE,TASK2`, and the log shows "Task 2 time: …" |
| Status chip | Have the RPi send `{"status":"turning left"}`. The chip in the strip changes icon and text |

## 6. Gaps (challenges or claims the code doesn't fully back up)

1. **`requestLayout()` in `ArenaView.setState()`: not in the code**, and never was (`ArenaView.kt:177-181`).
   It isn't causing a bug, because the arena size is fixed at 20×20. Either drop this from the challenges list or call it a non-issue.
2. **Tap-flicker fix: only partial.** The guide lines and badge wait for `isDragging`
   (`ArenaView.kt:260`). The ghost outline and the faded obstacle do not (`:203-207`; `dragState` is set on finger-down at `:428-435`).
3. **"RECEIVER_EXPORTED": the code uses `RECEIVER_NOT_EXPORTED`** (`AndroidBluetoothController.kt:171`).
   That's correct, but word the video accordingly. The permissions and receiver flag were in place from
   the initial commit (`87779f4`), so they're more "handled from day one" than "a bug we hit".
4. **A smaller socket leak remains.** `connect()`, `startListening()` and an accepted incoming connection
   all replace `socket` without closing the old one (`:218-231,286,503`), and the crash guard sets it to
   null without closing it (`:75`). The risk is low because the picker can only be opened while
   disconnected (`MainActivity.kt:72-78`).
5. **Auto-reconnect after a Listen-mode drop dials out instead of listening again.** `scheduleReconnect()`
   always calls `tryConnectOnce()` using the remote address (`:287,649-668`). If AMD Tool was the one
   connecting, the tablet keeps retrying an outgoing connection until someone taps Disconnect.
6. **`ROBOT` direction format differs by direction.** Outgoing messages use full names (`ROBOT,0,0,NORTH`,
   `ArenaProtocol.kt:27-29`). Incoming parsing accepts only single letters N/E/S/W (`BluetoothModels.kt:104`,
   `ArenaModels.kt:19`). Confirm with the RPi/algorithm team which format each side expects.
7. **Out-of-date comments that could mislead the algorithm team.**
   - The controller header says "newline-delimited framing" (`AndroidBluetoothController.kt:48`), but sends no longer add `\n`.
   - `ArenaModels.kt:24-26` and `ArenaProtocol.kt:17-18` say the origin is top-left with y increasing downward. In fact touch input and drawing make (0,0) the bottom-left cell (`ArenaView.kt:415`, `:331`).
   - `ArenaViewModel.kt:35-37` still says "4x4 footprint".
8. **Android 13+ notification permission is declared but never requested** (`AndroidManifest.xml:32`;
   no runtime request anywhere). The foreground service still runs, but its notification may be hidden.
9. **Not built:** saved arenas or presets, an on-screen Task 2 timer (the time only appears in the log),
   and a "clear log" button (`clearMessages()` exists but nothing calls it).
