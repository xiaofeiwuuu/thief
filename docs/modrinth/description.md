# Thief · 小偷

**Thieves visit at night. Catch them. Tie them up. Collect the bounty.**

Each dusk, a thief may come for your chests, barrels and ripe crops. It grabs what it can, shines for a few seconds, and runs. You can hang a bell, keep dogs, and shut an iron door — or you can chase it down, tie it with a rope, and drag it to a bounty board for emeralds.

Some nights it is not one thief but a whole **crew**, with a lookout who whistles when you come near, and a mate who passes the loot on while the one you are chasing keeps shining as a decoy. And now and then the crew has a **magician**.

> **Status: early release (0.1.0).** It has been played through and is covered by automated tests, but it is young. Please report anything odd on the issue tracker.

Made with the help of AI (Claude): code, text and artwork.

本项目的代码、文案和图片由 AI（Claude）协助制作。

## The thieves

- **The thief.** Comes at night: each dusk every player has a 35 % chance of one (25 % of the time it is a whole crew). It looks for chests, barrels and ripe crops nearby and takes up to 3 stacks (16 of each) and 8 crops (replanted), then **shines for 20 seconds** and runs. It leaves trapped chests, shulker boxes and other mods' storage alone, and steals nothing if `mobGriefing` is off.
- **What it fears.** You, within 7 blocks. Wolves and dogs (tame or wild) within 12. An iron door stops it (a wooden one does not). A **bell** within 5 blocks of the chest rings when it is robbed, tells players within 48 blocks, and limits the thief to one stack — the cheapest defence there is.
- **The crew.** Two robbers and a lookout. The **lookout** whistles when you are within 24 blocks and the whole crew draws back for 10 seconds, so deal with it first. A chased **robber** hands all the loot to a mate and keeps shining as a decoy — look at what it holds, or use the tracker.
- **Ransom and rescue.** 10 to 20 seconds after you catch one of a crew, a mate comes out holding up a sign: let your captive go (empty-handed, not sneaking) and 70 % of the loot comes back. Strike the mate and the talk is off. Without a deal, mates try to cut the captive loose.
- **The magician.** A crew of three or more has one 40 % of the time. It does not steal; it has 40 health and a purple boss bar. Near you it makes up to three **copies** (each looks exactly like it and turns to smoke on a touch), **reaches into chests** for a stack a second from 14 blocks away and leaves a prop in its place, **throws cards**, drops **smoke**, swaps places — and drinks a potion to **disguise** itself as an animal, a villager or something person-shaped. The rope will not hold it until it is worn down to a third of its health. It drops a badge and emeralds.

## Catching them

- **Tie it** — use the **rope** on it. It stops running and stealing, and follows you. No need to fight. Empty hand, not sneaking, lets it go; the rope comes back.
- **Lead it** and use the rope again on the side of a log, fence, wall or pillar (**tied to a tree**), on the underside of a block (**hung up**, needs 3 free blocks below), or on a **rack** (spread out). Break the block above, or any block of the rack, and it falls free.
- **Search it** (sneak + use) one thing at a time, or **lash** it with the **whip**: it cannot be killed, and 35 % of the time it tells where the other free thieves are, and they shine for 30 seconds.
- **Resentment.** The longer a captive is tied, the more it hates you (more if hung or on a rack, and for every lash). Let it go with a lot of resentment and it may attack whoever tied it, or run faster for a minute.
- **The bounty board.** Lead a tied thief to it and use it: 3 emeralds for a lone thief, 5 for a crew member, 10 and a badge for a magician — plus experience, your rope back, and any loot it still carries.
- Zombies, skeletons, villagers, pillagers, witches, piglins and other person-shaped creatures can be tied too (never players) — the list is in the settings.

## Tools

| Item | Recipe |
|---|---|
| **Rope** | 6 string in a diagonal → 2 ropes |
| **Whip** | stick + 2 string + leather |
| **Binding rack** | 4 logs + 2 sticks + 1 rope; place it on flat ground 3 wide and 3 high |
| **Bounty board** | 3 planks on top; plank, paper, plank; a stick below (a 2 × 2 poster) |
| **Thief tracker** | compass + spyglass — shows the direction, distance and last place of the nearest thief that carries loot |
| **Thief guide** | book + string — an in-game book with everything here, and a copy comes with you the first time you enter a world |
| **Magician's badge** | dropped by a magician, or given for one brought in alive; 8 emeralds at the board |

## Commands

`/thief ledger` — the record of recent thefts (everyone). Operators: `/thief visit` (send a thief to rob what is near you), `/thief crew`, `/thief magician`, `/thief spawn`, `/thief debug`.

## Settings

`config/thief-common.toml`, every setting with a note: how often a thief comes at night, how often it is a crew, how often a crew has a magician, how much it takes, the bell's reach, the crew's speed, the ransom, the bounty, what can be tied, and each of the magician's tricks.

## Compatibility

- Minecraft **1.20.1**, **Forge** 47+. No other mod is needed. Install it on both sides.
- Not tested with other mods that add their own thieves or chest-stealing.

---

# 中文说明

**夜里有小偷来偷东西：抓住它，绑起来，领赏金。**

每到黄昏，可能有小偷来偷你的箱子、木桶和成熟的庄稼。它抓了就跑，发光几秒。你可以挂钟、养狗、装铁门，也可以追上去，用绳子绑住，牵到悬赏榜换绿宝石。

有时来的不是一个小偷，而是一整个**团伙**：有望风的，你一靠近就吹口哨；有人被追时把赃物交给同伙，自己继续发光当诱饵。偶尔团伙里还有一个**魔术师**。

> **状态：早期版本（0.1.0）**。已经完整玩过，有自动测试覆盖，但还年轻，遇到奇怪的地方请在问题页反馈。

本项目的代码、文案和图片由 AI（Claude）协助制作。

- **小偷**：每个黄昏每位玩家有 35% 的概率来一个（其中 25% 是团伙）。偷箱子和木桶里最多 3 组（每组最多 16 个）、成熟庄稼最多 8 棵（补种回去），然后**发光 20 秒**逃跑。上了陷阱的箱子、潜影盒和别的模组的存储它不碰。
- **它怕什么**：你靠近 7 格内；狼和狗（驯服的也算）12 格内；铁门拦得住它，木门拦不住；箱子 5 格内挂**钟**，被偷时会响，48 格内的玩家都会收到消息，小偷只能偷 1 组。
- **团伙**：两个盗贼加一个望风的。**望风的**在你 24 格内会吹口哨，整个团伙撤退 10 秒；被追的**盗贼**会把赃物交给同伙，自己发光当诱饵。
- **赎金与解救**：抓到团伙里一个人后 10 到 20 秒，会有同伙举牌出来谈：放人，归还赃物的 70%。
- **魔术师**：三人以上的团伙有 40% 带一个。它不偷东西，40 点血，紫色血条。会**分身**、**隔空取箱子里的东西**并换成道具、**扔扑克牌**、放**烟雾**、互换位置，还会喝药水**伪装**成动物、村民或人形生物。血量被打到三分之一以下，绳子才绑得住它。
- **抓人**：用**绳子**右键绑住；牵着走，再对着树干、栅栏、墙、柱子的侧面绑（**绑在树上**），对着方块底面（**吊起来**），或者对着**刑架**（**绑在架子上**）；潜行右键**搜身**，用**皮鞭**抽（35% 招供）；被绑越久越记仇。
- **悬赏榜**：牵着被绑的小偷右键榜：单个 3 绿宝石，团伙成员 5，魔术师 10 加徽章，还有经验和剩下的赃物。
- **工具**：绳子（6 根线斜着摆得 2 根）、皮鞭（木棍 + 2 线 + 皮革）、刑架（4 原木 + 2 木棍 + 1 绳子）、悬赏榜（3 木板在上；木板、纸、木板；下面一根木棍）、小偷追踪器（指南针 + 望远镜）、小偷指南（书 + 线，第一次进世界会送一本）、魔术师徽章（魔术师掉落，榜上换 8 绿宝石）。
- **命令**：`/thief ledger`（失窃记录，人人可用）；管理员：`visit`、`crew`、`magician`、`spawn`、`debug`。
- **配置**：`config/thief-common.toml`，每一项都有注释。
- **版本**：Minecraft 1.20.1，Forge 47+，两端都要装，不需要别的模组。

## License / 许可

Code and original textures: MIT. Some of the artwork on this page is made from the mod's own textures and from the game's item textures; none of it is an in-game screenshot.
