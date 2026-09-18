<h1 align="center">💡 LowCore</h1>

<p align="center">
  A lightweight, modular, and performance-focused Minecraft utility plugin  
  by <a href="https://github.com/jalikdev">jalikdev</a>.
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
- **Anti-Freecam** — Loading-screen checks for Freecam and Meteor Client, Geyser-safe with persistent GUI logs
- **Crystal Cooldown** — Configure a tick-accurate End Crystal placement delay per player

---

## 🔧 Admin & Utility Commands

| Command        | Description                       | Permission                             |
|----------------|-----------------------------------|----------------------------------------|
| `/lowcore`     | Plugin info, reload, debug tools  | `lowcore.command`                      |
| `/lowcore dimension <nether\|end> <lock\|unlock\|status>` | Manage dimension locks | `lowcore.dimensions` |
| `/lock-dimension <nether\|end> [duration\|lock\|unlock\|status]` | Permanent or timed dimension locks | `lowcore.dimensions` |
| `/crystal-cooldown <ticks\|off\|status>` | Configure Crystal placement speed | `lowcore.crystal-cooldown` |
| `/anti-freecam [on\|off\|status]` | Open the GUI or toggle client-mod detection | `lowcore.antifreecam.admin` |
| `/anti-freecam punishment <notify\|kick\|ban>` | Select the action after a confirmed match | `lowcore.antifreecam.admin` |
| `/anti-freecam check <player>` | Manually check an online player | `lowcore.antifreecam.admin` |
| `/anti-freecam logs` | Open the persistent detection log GUI | `lowcore.antifreecam.admin` |
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

## 🕵️ Anti-Freecam

Run `/anti-freecam` to open the settings GUI. The feature is disabled by
default. It probes the Freecam keys `key.freecam.toggle` and
`freecam.config.gui.title`, plus Meteor Client's
`key.meteor-client.open-gui`, through Paper's virtual-sign API. Confirmed
matches can notify staff, kick, or permanently ban. A second probe is enabled
by default, and blocked or timed-out responses never cause punishment. Automatic
checks start during the terrain-loading screen and retry once if the client's
initial chunk packets swallow the first probe. The temporary client-side sign
is replaced with the real block immediately and its editor is closed after one tick, so
the check does not leave or visibly flash a sign during normal gameplay.

The check detects matching client translations, not whether Freecam was
actively used. Players with `lowcore.antifreecam.bypass` are skipped; staff
with `lowcore.antifreecam.alerts` receive results. Players connected through
Geyser or Floodgate are detected through their APIs and skipped completely.
Results are stored in SQLite and can be viewed through the GUI or with
`/anti-freecam logs`.

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
- Freecam/Meteor detection and punishment (`anti-freecam.*`)


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
