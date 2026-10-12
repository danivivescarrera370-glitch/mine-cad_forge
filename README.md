# MineCad

MineCad is a Fabric mod that turns Minecraft into a lightweight in-game CAD workflow.
It lets you select blocks, preview them in a 3D viewport, and export selections to common 3D formats.

## Features

- Block selection with wand-style workflows and selection commands
- In-inventory 3D preview tab with camera controls
- Selection tools for save, import, fill, points, and info
- Export support for `STL`, `OBJ`, and `FBX`
- Configurable settings UI (including keybind capture fields)
- Scale control for export units (`selection scale`)

## Requirements

- Minecraft: `~26.2` (as configured in this project)
- Fabric Loader: `>=0.19.5`
- Fabric API: `0.159.0+26.2`
- Java: `25+`

## Getting Started (Dev)

Clone and run the client from source:

```powershell
Set-Location "C:\Java Projects\mine-cad"
.\gradlew.bat runClient
```

Build the mod JAR:

```powershell
Set-Location "C:\Java Projects\mine-cad"
.\gradlew.bat build
```

## In-Game UI

MineCad is integrated into the player inventory via a custom tab.

- **3D tab**: inspect selected blocks in a preview scene
- **Settings tab**: tweak mod behavior and keybind strings
- **Saves tab**: currently placeholder text
- **Help / Info tabs**: quick usage notes and project info

The side "Selection Settings" pane includes quick actions for:

- save/import/export
- block scale
- selection fill/list/delete
- point-based tools (`selection points ...`)

## Commands

Main command root:

- `/minecad selection ...`
- `/minecad settings ...`
- `/minecad wand ...`
- `/minecad help`
- `/minecad info`

Examples:

```text
/minecad selection save my_build
/minecad selection import my_build
/minecad selection export stl my_build
/minecad selection points add 0 64 0
/minecad selection scale 0.1
/minecad settings show
```

## Export Notes

- Export uses the configured block scale (`meters per block`)
- STL output reports units in millimeters in command feedback
- Output files are written under the mod data/export paths used by the command system

## Project Layout

- `src/main/java` - common/server-safe logic (commands, settings model, selection/export pipeline)
- `src/client/java` - client UI, tabs, 3D viewport, client-only input/render behavior
- `src/client/resources` - bundled default settings JSON and client assets

## Status

This is an active development project and some metadata placeholders still exist (for example in `fabric.mod.json`).
Expect iteration on UI polish, settings UX, and docs over time.

## License

This repository is licensed under **CC0 1.0 Universal**.
See `LICENSE` for full text.
