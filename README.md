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

---

## 🔧 Admin & Utility Commands

| Command        | Description                       | Permission                             |
|----------------|-----------------------------------|----------------------------------------|
| `/lowcore`     | Plugin info, reload, debug tools  | `lowcore.command`                      |
| `/lowcore dimension <nether\|end> <lock\|unlock\|status>` | Manage dimension locks | `lowcore.dimensions` |
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
