# Test inputs: Task 1 planned path (PATH message)

`path_sample.txt` is a single-line `PATH|...` message, the kind the RPi would send once
the planner has written `mission_stm.txt`. Paste it into AMD Tool and send it to the
tablet to draw and replay the planned path without a live run.

It was built from `visualise/visualise/mission_stm.txt` the same way as the RPi snippet in
`RPI_PATH_ANALYSIS.md`: `HELLO` and the `# --- leg ---` headers are dropped, and every TURN /
STRAIGHT line plus the `# RECOGNISE obstacle <id>` markers are kept in order, joined with `|`.
That gives 35 entries (29 motion commands and 6 photo stops) in 729 characters plus the
terminating newline.

**One change from `mission_stm.txt`: each RECOGNISE id is +1.** The sample `scenario.json`
numbers obstacles from 0, but the tablet numbers them 1, 2, 3… in the order you place them.
A real RPi plans from our `OBSTACLE,<id>,...` messages, so it would use our ids too. With the
placement order below, planner obstacle *k* is tablet obstacle *k + 1*, and the shifted ids make
each photo stop pulse the right obstacle. The motion commands are byte-for-byte unchanged.

## Arena setup (same scenario as `scenario.json`)

**Quick way:** send `obstacles_sample.txt` from AMD Tool:

```
DEBUG:OBS|9,17,S|17,18,S|10,11,S|13,7,E|15,2,W|2,6,N
```

It replaces every obstacle on the tablet with these six, numbered 1–6 in the order listed, and
the log shows `Debug: placed 6 obstacles`. Each entry is `x,y,face`, with the face as N/E/S/W
or a full word. `DEBUG:OBS` on its own clears the obstacles. If any entry is malformed, off the
arena or overlapping, nothing changes and the log says which entry was wrong. The robot and any
loaded path stay as they are. Obstacles placed this way are not sent back as `OBSTACLE`
messages; press **Send arena** to forward them.

**By hand:** start from an empty arena: open the app fresh, or press **Reset Arena** (it is only enabled
while connected). Leave the robot where Reset puts it: **(0, 0) facing North**, the default.

Place the obstacles **in this order** so the tablet ids come out as listed. (x, y) is the
obstacle's cell with the origin at the bottom-left, as shown by the axis ticks and the
coordinate badge while you hold to place. The face is the side carrying the image.

| Place # | Tablet id | Planner id (`scenario.json`) | Cell (x, y) | Image face |
|---|---|---|---|---|
| 1st | 1 | 0 | (9, 17) | S |
| 2nd | 2 | 1 | (17, 18) | S |
| 3rd | 3 | 2 | (10, 11) | S |
| 4th | 4 | 3 | (13, 7) | E |
| 5th | 5 | 4 | (15, 2) | W |
| 6th | 6 | 5 | (2, 6) | N |

The robot visits them in this order: **6 → 1 → 3 → 5 → 4 → 2**.

## Sending it

1. Connect AMD Tool to the tablet (Wheel tab → Bluetooth → "Wait for incoming connection").
2. Paste the whole line from `path_sample.txt` into AMD Tool's send box and send it. The
   tablet loads a PATH when its terminating newline arrives. If AMD Tool sends no newline,
   the line is still loaded after 2 s of silence.
3. The Wheel tab's log shows `Path loaded: 29 commands, 6 photo stops`.

## What you should see

- A dashed red path from a green dot at the robot's rear axle, (9.5, 4.0) cm, to a
  chequered finish near obstacle 2. There's a numbered gold dot at each photo stop. Like the
  path itself, the dot marks the car's rear axle; the camera is 19 cm further forward, 20–40 cm
  from the image.
- **Play** (next to the path toggle) runs the robot along it in about 19.5 s: 659 cm at
  2.5 s/m, plus a 0.5 s stop at each of the 6 photo stops, where that obstacle pulses gold.
  The part already driven turns solid. At the end the robot stays put until **Stop**.
