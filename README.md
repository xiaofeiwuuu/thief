# Thief · 小偷

[English](README.md) · [简体中文](README.zh-CN.md)

![Thief](docs/modrinth/images/01_title.png)

*(Artwork made from the mod's own textures; not an in-game screenshot.)*

**Thieves visit at night. Catch them. Tie them up. Collect the bounty.**

A Minecraft **Forge 1.20.1** mod. Each dusk, a thief may come for your chests, barrels and ripe crops. It grabs what it can, shines for a few seconds, and runs. Hang a bell, keep dogs, shut an iron door — or chase it down, tie it with a rope, and drag it to a bounty board for emeralds.

Some nights it is a whole **crew**, with a lookout who whistles when you come near. And now and then the crew has a **magician**.

| | |
|---|---|
| Minecraft | 1.20.1 |
| Loader | Forge 47.x (no other mod needed) |
| Sides | Required on both client and server |
| License | MIT |
| Version | 0.1.0 — **early release** |

> **Status.** It has been played through and is covered by automated tests (headless GameTests), but it is young. Please open an issue for anything odd.
>
> **AI disclosure.** The code, text and artwork of this project were made with the help of AI (Claude).

## Contents

- [The thieves](#the-thieves) · [Catching them](#catching-them) · [Tools](#tools) · [Commands](#commands) · [Settings](#settings) · [Install](#install) · [Build from source](#build-from-source) · [Known limits](#known-limits) · [License](#license)

## The thieves

| | |
|---|---|
| **Thief** | Each dusk every player has a 35 % chance of one (25 % of the time a whole crew). It takes up to 3 stacks (16 of each) from chests and barrels, and up to 8 ripe crops (replanted), then **shines for 20 seconds** and runs. It leaves trapped chests, shulker boxes and other mods' storage alone, and steals nothing if `mobGriefing` is off. It does nothing unless a player is within 128 blocks. |
| **Fears** | You within 7 blocks; wolves and dogs (tame or wild) within 12; an **iron door** stops it (a wooden one does not); a **bell** within 5 blocks of the chest rings when it is robbed, tells players within 48 blocks, and limits the thief to one stack. |
| **Crew** | Two robbers and a lookout. The **lookout** whistles when you are within 24 blocks and the crew draws back 10 seconds. A chased **robber** hands all the loot to a mate and keeps shining as a decoy: look at what it holds, or use the tracker. They flee as fast as you walk (4.3 blocks/s); sprint to catch them. |
| **Ransom & rescue** | 10 to 20 seconds after you catch a crew member, a mate comes out holding up a sign: let your captive go (empty-handed, not sneaking) and 70 % of the loot comes back, for 60 seconds. A blow to it ends the talk. Otherwise mates come to cut the captive loose; they run if you are within 7 blocks. |
| **Magician** | A crew of three or more has one 40 % of the time. It does not steal; 40 health and a purple boss bar. Within 24 blocks it makes up to 3 **copies** (10 s each; they turn to smoke on a touch — the real one is the one that hurts and has the bar), **reaches into chests** within 14 blocks (a stack a second, and leaves a prop in its place), **throws cards** (2 damage, a shield stops them), drops **smoke** (blind and slow for 3 s) and swaps places. It carries two potions and **disguises** itself for a minute as an animal, a villager or a person-shaped creature. The rope will not hold it until it is worn down to a third of its health. Drops a badge and 3 emeralds. |

## Catching them

- **Tie it** — use the **rope**. It stops running and stealing and follows you; no need to fight. Empty hand, not sneaking, lets it go (the rope comes back).
- **Lead it** and use the rope again on: the side of a log, fence, wall or pillar (**tied to a tree**; one person to a side); the underside of a block (**hung up**; needs 3 free blocks below); or a **rack** (spread out). Break the block above, or any block of the rack, and it is free.
- **Search it** (sneak + use), one thing at a time, or **lash** it with the whip: it cannot be killed, and 35 % of the time it tells where the other free thieves are (they shine for 30 s).
- **Resentment.** The longer it is tied, the more it hates you: +1 a second at a post, +0.5 led, +2 hung or on a rack, +12 a lash, +3 searched. Smoke from 30, angry sparks from 60. Let go with 30 or more and it either attacks whoever tied it for 30 seconds or runs faster for a minute.
- **Bounty board.** Lead a tied thief to it and use it: 3 emeralds for a lone thief, 5 for a crew member, 10 and a badge for a magician; plus experience, the rope back, and any loot it still carries.
- Zombies, husks, skeletons, villagers, pillagers, witches, piglins and the like can be tied as well (never players); the list is `bindableTypes`.

## Tools

| Item | Recipe |
|---|---|
| **Rope** | 6 string in a diagonal → 2 |
| **Whip** | stick + 2 string + leather |
| **Binding rack** | 4 logs + 2 sticks + 1 rope; place on flat ground, 3 wide and 3 high |
| **Bounty board** | 3 planks on top; plank, paper, plank; a stick below (a 2 × 2 poster) |
| **Thief tracker** | compass + spyglass: direction, distance and last place of the nearest thief carrying loot (it follows the loot when it is passed on) |
| **Thief guide** | book + string; one comes with you the first time you enter a world (`giveGuide`) |
| **Magician's badge** | dropped by a magician, or given for one brought in alive; 8 emeralds at the board |

## Commands

`/thief ledger` — recent thefts (everyone). Operators: `/thief visit` (send a thief to rob what is near you), `/thief crew`, `/thief magician`, `/thief spawn`, `/thief debug` (why a thief does nothing, why no ransom was offered).

## Settings

`config/thief-common.toml`, 41 settings, each with a note. A few:

| Setting | Default | |
|---|---|---|
| `nightlyChance` | `0.35` | Chance of a thief each dusk, per player |
| `crewChance` | `0.25` | Chance it is a crew |
| `magicianChance` | `0.4` | Chance a crew has a magician |
| `stacksPerTheft` / `maxItemsPerStack` / `cropsPerTheft` | `3` / `16` / `8` | How much it takes |
| `bellRadius` | `5` | How near a bell must be |
| `crewSpeed` | `0.275` | The crew's speed |
| `ransomChance` / `ransomShare` | `0.7` / `0.7` | Ransom offered, and the share of the loot |
| `bountyEmeralds` / `bountyCrewBonus` / `bountyMagician` / `tokenEmeralds` | `3` / `2` / `10` / `8` | Rewards |
| `magicianCopySeconds` / `magicianCastSeconds` / `magicianTieHealth` | `10` / `20` / `0.35` | The magician's copies and when the rope holds |
| `disguiseEnabled` / `potions` / `disguiseSeconds` | `true` / `2` / `60` | The magician's disguise |
| `giveGuide` | `true` | Whether the guide book is given |

## Install

1. Install **Minecraft 1.20.1** and **Forge 47.x**.
2. Put `thief-forge-1.20.1-0.1.0.jar` in the `mods` folder (the server too).
3. Try `/thief visit` in a world with a chest nearby.

## Build from source

Needs Java 17.

```sh
./gradlew build                  # build/libs/thief-forge-1.20.1-<version>.jar
./gradlew runGameTestServer      # the automated in-game tests (headless)
./gradlew runClient
```

> `gradle.properties` contains `org.gradle.java.home=...` pointing at a Homebrew Java 17 on the author's Mac. Change it, or remove the line, if your Java lives elsewhere.

The code is in `src/main/java/com/xiaofeiwu/thief` (the thief is `ThiefEntity`, the night visits `NightVisits`, the loot `StealGoal`); the tests are `ThiefGameTests`.

## Known limits

- Not tested with other mods that add their own thieves or chest-stealing.
- A few of the automated tests have failed now and then, rarely, for reasons not found yet.

## License

[MIT](LICENSE). Minecraft and Forge are the property of their owners; this is an unofficial mod, not affiliated with Mojang or Microsoft.
