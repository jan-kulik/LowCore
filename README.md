<h1 align="center">💡 LowCore</h1>

<p align="center">
  A lightweight, modular, and performance-focused Minecraft utility plugin  
  by <a href="https://github.com/jan-kulik">jan-kulik</a>.
</p>

---

## 🚀 Overview
LowCore is a modern utility & admin plugin designed for **Paper 26.2** servers.
It includes high-quality commands, performance tools, GUI utilities, a database module,  
and multiple clean, well-structured systems.

---

## ✨ Features

### 🔹 Core Systems
- **Cleanup GUI** — Remove items, mobs, vehicles & XP orbs safely
- **Performance Monitor** — Live TPS, MSPT, RAM, chunks, players
- **Vanish System** — Fully invisible, with fake join/leave messages
- **Join/Quit Messages** — Fully configurable
- **MOTD System** — Two-line MOTD with placeholders
- **Logout Tracking System** — Stores player logout positions using SQLite
- **Dimension Locks** — Lock the Nether or End, including portal creation and travel
- **Timed Dimension Locks** — Automatically unlock dimensions after durations such as `30m`, `2h`, or `1d12h`
- **Anti-Mods** — Loading-screen checks for Freecam, Meteor, Wurst, LiquidBounce and ThunderHack, with per-client rules
- **Crystal Cooldown** — Configure a tick-accurate End Crystal placement delay per player
- **Control Center** — Configure all major LowCore systems from one `/lowcore` GUI
- **Admin & Utility GUI** — Open inventories, Ender Chests, crafting and anvils from the control center
- **Admin Audit Log** — Persistent, paginated history for commands and GUI setting changes

---

## 🔧 Admin & Utility Commands

| Command        | Description                       | Permission                             |
|----------------|-----------------------------------|----------------------------------------|
| `/lowcore`     | Open the central settings GUI     | `lowcore.command`                      |
| `/lock-dimension <nether\|end> [duration\|lock\|unlock\|status]` | Permanent or timed dimension locks | `lowcore.dimensions` |
| `/crystal-cooldown <ticks\|off\|status>` | Configure Crystal placement speed | `lowcore.crystal-cooldown` |
| `/anti-mods [on\|off\|status]` | Open the GUI or toggle client-mod detection | `lowcore.antimods.admin` |
| `/anti-mods punishment <notify\|kick\|ban\|custom>` | Select the action after a confirmed match | `lowcore.antimods.admin` |
| `/anti-mods command <set\|add\|list\|clear> [command]` | Configure custom console punishment commands | `lowcore.antimods.admin` |
| `/anti-mods <allow\|block> <client>` | Configure each supported client separately | `lowcore.antimods.admin` |
| `/anti-mods check <player>` | Manually check an online player | `lowcore.antimods.admin` |
| `/anti-mods logs [player]` | Open filtered persistent detection logs | `lowcore.antimods.admin` |
| `/gm`          | Change gamemode                   | `lowcore.gm`                           |
| `/fly`         | Toggle flight                     | `lowcore.fly`                          |
| `/ec`          | Open own/others ender chest       | `lowcore.ec` / `lowcore.ec.others`     |
| `/invsee`      | Live inventory view               | `lowcore.invsee`                       |
| `/hat`         | Put item on head                  | `lowcore.hat` / `lowcore.hat.others`   |
| `/heal`        | Heal players                      | `lowcore.heal` / `lowcore.heal.others` |
| `/feed`        | Feed players                      | `lowcore.feed` / `lowcore.feed.others` |
| `/craft`       | Open crafting table               | `lowcore.craft`                        |
| `/anvil`       | Open anvil GUI                    | `lowcore.anvil`                        |
| `/repair`      | Repair held/all items             | `lowcore.repair`                       |
| `/spawnmob`    | Spawn mobs                        | `lowcore.spawnmob`                     |
| `/killall`     | Kill mobs globally/by type/radius | `lowcore.killall`                      |
| `/god`         | Toggle invincibility              | `lowcore.god`                          |
| `/speed`       | Set walk/fly speed                | `lowcore.speed`                        |
| `/cleanup`     | Cleanup GUI                       | `lowcore.cleanup`                      |
| `/performance` | Show TPS/MSPT/RAM/chunks          | `lowcore.performance`                  |
| `/log`         | Show recent admin actions         | `lowcore.log`                          |
| `/vanish`      | Vanish mode                       | `lowcore.vanish`                       |
| `/lastlogout`  | Show last logout location         | `lowcore.lastlogout`                   |
| `/sudo`        | Sudo someone to do something      | `lowcore.sudo` / `lowcore.sudo.op`     |

---

## 🌍 Dimension Locks

Players can run `/lock-dimension` without arguments to open the dimension lock
GUI. The available timer buttons can be customized under
`dimensions.gui.durations` in `config.yml`.

```text
/lock-dimension nether          # lock permanently
/lock-dimension end 2h          # lock for two hours
/lock-dimension end 1d12h       # combined durations are supported
/lock-dimension end status      # show current state and remaining time
/lock-dimension end unlock      # unlock immediately
```

Supported duration units are `s`, `m`, `h`, `d`, and `w`, up to 365 days. While
the End is locked, Ender Eyes cannot be inserted into End Portal Frames. Players
already inside a locked dimension can always leave it.

---

## 💎 Crystal Cooldown

```text
/crystal-cooldown 10       # one Crystal every 10 ticks
/crystal-cooldown status   # show the current setting
/crystal-cooldown off      # disable the cooldown
```

The cooldown is tracked separately for every player. `20` ticks are approximately
one second. Players with `lowcore.crystal-cooldown.bypass` are not limited.

## 🕵️ Anti-Mods

Run `/anti-mods` to open the settings GUI. The feature is disabled by default.
It checks client translation/keybind resources for Freecam, Meteor Client,
Wurst Client, LiquidBounce and ThunderHack through Paper's virtual-sign API.
Each client can be allowed or blocked independently. Allowed matches are only
logged; blocked matches use the selected notify, kick, ban, or custom console-command action. A second
probe is enabled by default, and protected, failed or timed-out responses never
cause punishment. Automatic checks start during the terrain-loading screen and
retry once if the client's initial packets swallow the first probe. The virtual
sign is placed inside the best enclosed block available, restored immediately,
and restored again over the following ticks to prevent a visible leftover sign.

The check detects exposed client resources, not whether a cheat was actively
used, and cannot detect every modified or disguised client. OP bypass can be
toggled, and a custom bypass permission can be entered with
`/anti-mods bypass-permission <permission|off>`. Staff with
`lowcore.antimods.alerts` receive results. Players connected through Geyser or
Floodgate are skipped completely. Results are stored in SQLite; the GUI can
filter by result, client, or player, limit retained pages, and clear them.
Manual checks have a configurable per-target cooldown.

## 🧾 Admin Audit Log

`/log` opens a persistent, paginated audit GUI containing LowCore commands and
important GUI setting changes. The log can be cleared through a confirmation
screen. Its retention limit is configured at `audit-log.max-entries`.

---

## ⚙️ Config
LowCore provides a clean and fully documented `config.yml` including:

- Custom prefixes
- Join/Quit messages
- Vanish messages
- MOTD system
- Cleanup GUI settings
- Performance monitor settings
- Debug settings
- Nether and End access locks (`dimensions.nether-locked` / `dimensions.end-locked`)
- Per-client Anti-Mod detection, punishment, bypass and log retention (`anti-mods.*`)
- Persistent admin audit retention (`audit-log.max-entries`)


---

## 🔧 Installation

1. Download the latest release
2. Place the `.jar` in your server's `/plugins/` folder
3. Restart the server
4. Configure everything in `config.yml`

---

## 🛠 Requirements
- **Java 25+**
- **Paper 26.2**
- Fully compatible with **LuckPerms**, **Vault**, and all major permission plugins

---

## 🧾 License
This project is licensed under the **MIT License**.  
You are free to use, modify, and contribute.  
