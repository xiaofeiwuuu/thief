package com.xiaofeiwu.thief.client;

import com.xiaofeiwu.thief.ModItems;
import com.xiaofeiwu.thief.ThiefEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The guide: a book open at two pages, with three tabs along the top. People: a list on the left, and on the right the figure drawn (as it is in
 * the game, turning to follow the mouse) with what it does. Tools: the same, with the recipe drawn as a crafting grid. How to play: sections, and
 * their text. The text scrolls with the wheel. In a text, what is between double braces is something to copy (a command, the name of a setting):
 * it is drawn blue and underlined, never broken across lines, and a click puts it on the clipboard. The words are in the language files
 * ({@code thief.guide.people.N.n} and {@code .t}, and so on).
 */
public class GuideScreen extends Screen {

    private static final int BOOK_W = 380;
    private static final int BOOK_H = 230;
    private static final int INK = 0xFF3B2A1A;
    private static final int SOFT = 0xFF7A6548;
    private static final int PAGE = 0xFFF3E9CD;
    private static final int PAGE_DARK = 0xFFE3D2A6;
    private static final int EDGE = 0xFFA8946A;
    private static final int LEATHER = 0xFF4A3220;
    private static final int LEATHER_LIGHT = 0xFF6B4A2B;
    private static final int GOLD = 0xFFD4AF5A;
    private static final int LINK = 0xFF2F5D8A;
    private static final int ROW = 15;

    private enum Tab {
        PEOPLE("people", 10), TOOLS("tools", 8), PLAY("play", 13);

        final String key;
        final int count;

        Tab(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    /** What to draw for each person: the look given to {@link ThiefEntity#forGuide}. */
    private static final String[] LOOKS = {"thief", "robber", "lookout", "sign", "magician", "copy", "led", "posted", "hung", "racked"};

    private static Tab tab = Tab.PEOPLE;
    private static final int[] SELECTED = new int[Tab.values().length];

    private final Map<String, ThiefEntity> figures = new HashMap<>();
    private final List<TextLine> lines = new ArrayList<>();
    private int x0;
    private int y0;
    /** The book is drawn at this scale, so that the whole of it fits in the window; every coordinate here is in the book's own units. */
    private float scale = 1.0F;
    private int listScroll;
    private int textScroll;
    private int textHeight;
    private int textTop;
    private int textBottom;
    private String copied = "";
    private long copiedAt;

    private record TextLine(FormattedCharSequence text, int y) {
    }

    public GuideScreen() {
        super(Component.translatable("thief.guide.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // the book, with its tabs above and its cover round it, is about BOOK_W + 16 wide and BOOK_H + 40 high: as large as will fit, but neither tiny nor huge
        scale = Math.max(0.5F, Math.min(1.5F, Math.min((width - 12.0F) / (BOOK_W + 16.0F), (height - 8.0F) / (BOOK_H + 40.0F))));
        x0 = (int) ((width / scale - BOOK_W) / 2.0F);
        y0 = (int) ((height / scale - BOOK_H) / 2.0F) + 10;
    }

    private int leftX() {
        return x0 + 14;
    }

    private int rightX() {
        return x0 + BOOK_W / 2 + 10;
    }

    private int pageW() {
        return BOOK_W / 2 - 26;
    }

    private int selected() {
        return SELECTED[tab.ordinal()];
    }

    // ------------------------------------------------------------------ the book itself ------------------------------------------------------------------

    private void drawBook(GuiGraphics g) {
        int mid = x0 + BOOK_W / 2;
        // the cover, with a lighter border inside it, and studs at the corners
        g.fill(x0 - 7, y0 - 7, x0 + BOOK_W + 7, y0 + BOOK_H + 7, LEATHER);
        g.fill(x0 - 6, y0 - 6, x0 + BOOK_W + 6, y0 + BOOK_H + 6, LEATHER_LIGHT);
        g.fill(x0 - 5, y0 - 5, x0 + BOOK_W + 5, y0 + BOOK_H + 5, LEATHER);
        for (int[] c : new int[][]{{x0 - 5, y0 - 5}, {x0 + BOOK_W + 2, y0 - 5}, {x0 - 5, y0 + BOOK_H + 2}, {x0 + BOOK_W + 2, y0 + BOOK_H + 2}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, GOLD);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 2, c[1] + 2, 0xFFF1D98C);
        }
        // the edges of the pages, a few leaves showing under the two pages
        for (int k = 1; k <= 3; k++) {
            int shade = 0xFF000000 | (0xE9 - k * 14) << 16 | (0xDC - k * 16) << 8 | (0xB5 - k * 20);
            g.fill(x0 + k, y0 + BOOK_H + k - 1, x0 + BOOK_W - k, y0 + BOOK_H + k, shade);
        }
        g.fill(x0, y0, mid - 2, y0 + BOOK_H, PAGE);
        g.fill(mid + 2, y0, x0 + BOOK_W, y0 + BOOK_H, PAGE);
        // the pages shade toward the spine, and the spine has its stitches
        for (int i = 0; i < 12; i++) {
            int alpha = (12 - i) * 6;
            g.fill(mid - 2 - i - 1, y0, mid - 2 - i, y0 + BOOK_H, alpha << 24 | 0x5A4020);
            g.fill(mid + 2 + i, y0, mid + 2 + i + 1, y0 + BOOK_H, alpha << 24 | 0x5A4020);
        }
        g.fill(mid - 2, y0, mid + 2, y0 + BOOK_H, 0xFF8A7048);
        g.fill(mid - 1, y0, mid, y0 + BOOK_H, 0xFF5A4020);
        for (int y = y0 + 6; y < y0 + BOOK_H - 4; y += 10) {
            g.fill(mid - 2, y, mid + 2, y + 1, 0xFFB89A62);
        }
        // a thin frame inside each page
        g.renderOutline(x0 + 5, y0 + 5, BOOK_W / 2 - 14, BOOK_H - 10, 0x33A8946A);
        g.renderOutline(mid + 9, y0 + 5, BOOK_W / 2 - 14, BOOK_H - 10, 0x33A8946A);
        // a red ribbon marking the place, with a notch cut out of its end
        int rbx = mid + 26;
        g.fill(rbx, y0 - 7, rbx + 9, y0 + 40, 0xFFA82828);
        g.fill(rbx + 1, y0 - 7, rbx + 2, y0 + 40, 0xFFC84040);
        for (int i = 0; i < 5; i++) {
            g.fill(rbx + 4 - i, y0 + 36 + i, rbx + 5 + i, y0 + 37 + i, PAGE);
        }
    }

    private void drawTabs(GuiGraphics g) {
        int i = 0;
        for (Tab t : Tab.values()) {
            int tx = x0 + 4 + i * 80;
            boolean on = t == tab;
            // a tab like a folder's: the one that is open stands taller, and is of the colour of the page, so as to be part of it
            int top = on ? y0 - 20 : y0 - 17;
            g.fill(tx + 1, top, tx + 77, y0 + 1, on ? LEATHER : 0xFF3A2818);
            g.fill(tx + 2, top + 1, tx + 76, y0 + 1, on ? PAGE : 0xFFC9B88A);
            g.fill(tx + 2, top + 1, tx + 76, top + 2, on ? 0xFFFFF6DC : 0xFFD9CA9C);
            g.pose().pushPose();
            g.pose().translate(tx + 6, top + (on ? 3 : 2), 0);
            g.pose().scale(0.8F, 0.8F, 1.0F);
            g.renderItem(tabIcon(t), 0, 0);
            g.pose().popPose();
            g.drawString(font, Component.translatable("thief.guide.tab." + t.key), tx + 22, top + (on ? 6 : 5), on ? INK : SOFT, false);
            i++;
        }
        g.drawString(font, Component.translatable("thief.guide.title").withStyle(ChatFormatting.BOLD), x0 + BOOK_W - 8 - font.width(Component.translatable("thief.guide.title")) - 1, y0 - 14, 0xFFE8D08A, true);
    }

    private ItemStack tabIcon(Tab t) {
        return new ItemStack(switch (t) {
            case PEOPLE -> ModItems.THIEF_SPAWN_EGG.get();
            case TOOLS -> ModItems.ROPE.get();
            case PLAY -> Items.COMPASS;
        });
    }

    /** A heading with a line and a diamond under it. */
    private void heading(GuiGraphics g, Component text, int x, int y, int w) {
        g.drawString(font, text.copy().withStyle(ChatFormatting.BOLD), x, y, INK, false);
        int ly = y + 11;
        g.fill(x, ly, x + w, ly + 1, EDGE);
        int cx = x + font.width(text) + 8;
        if (cx + 5 < x + w) {
            g.fill(cx, ly - 1, cx + 3, ly + 2, GOLD);
        }
    }

    /** A slot like the game's own, sunk in: dark above and to the left, light below and to the right. */
    private void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF8B7B5A);
        g.fill(x + size - 1, y, x + size, y + size, 0xFFFFF6DC);
        g.fill(x, y + size - 1, x + size, y + size, 0xFFFFF6DC);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFFC9B88A);
    }

    // ------------------------------------------------------------------ drawing ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        mx = (int) (mx / scale);
        my = (int) (my / scale);
        drawBook(g);
        drawTabs(g);
        drawList(g, mx, my);
        drawEntry(g, mx, my);
        if (!copied.isEmpty() && System.currentTimeMillis() - copiedAt < 1800) {
            Component note = Component.translatable("thief.guide.copied", copied);
            int w = font.width(note) + 10;
            int nx = x0 + BOOK_W / 2 - w / 2, ny = y0 + BOOK_H - 4;
            g.fill(nx - 1, ny - 1, nx + w + 1, ny + 14, GOLD);
            g.fill(nx, ny, nx + w, ny + 13, INK);
            g.drawString(font, note, nx + 5, ny + 3, 0xFFFFFFFF, false);
        }
        g.pose().popPose();
        super.render(g, (int) (mx * scale), (int) (my * scale), partial);
    }

    private int visibleRows() {
        return (BOOK_H - 40) / ROW;
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int lx = leftX(), top = y0 + 12, w = pageW();
        heading(g, Component.translatable("thief.guide.tab." + tab.key), lx, top, w);
        for (int n = 0; n < visibleRows() && listScroll + n < tab.count; n++) {
            int idx = listScroll + n;
            int ry = top + 18 + n * ROW;
            boolean on = idx == selected();
            boolean over = mx >= lx && mx < lx + w && my >= ry && my < ry + ROW;
            if (on) {
                g.fill(lx, ry, lx + w, ry + ROW - 1, INK);
                g.fill(lx, ry, lx + 2, ry + ROW - 1, GOLD);
            } else if (over) {
                g.fill(lx, ry, lx + w, ry + ROW - 1, PAGE_DARK);
            } else if (n % 2 == 0) {
                g.fill(lx, ry, lx + w, ry + ROW - 1, 0x14A8946A);
            }
            g.pose().pushPose();
            g.pose().translate(lx + 4, ry, 0);
            g.pose().scale(0.8F, 0.8F, 1.0F);
            g.renderItem(iconOf(idx), 0, 0);
            g.pose().popPose();
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("thief.guide." + tab.key + "." + (idx + 1) + ".n").getString(), w - 24), lx + 22, ry + 4, on ? 0xFFFFFFFF : INK, false);
        }
        if (tab.count > visibleRows()) {
            g.drawString(font, (listScroll + 1) + "-" + Math.min(tab.count, listScroll + visibleRows()) + " / " + tab.count, lx, y0 + BOOK_H - 14, SOFT, false);
        }
    }

    private ItemStack iconOf(int idx) {
        return new ItemStack(switch (tab) {
            case PEOPLE -> new Item[]{ModItems.THIEF_SPAWN_EGG.get(), Items.GOLD_INGOT, Items.SPYGLASS, Items.OAK_SIGN, ModItems.MAGICIAN_TOKEN.get(), Items.ENDER_PEARL,
                    ModItems.ROPE.get(), Items.OAK_LOG, Items.LEAD, ModItems.RACK.get()}[idx];
            case TOOLS -> new Item[]{ModItems.ROPE.get(), ModItems.WHIP.get(), ModItems.RACK.get(), ModItems.BOUNTY_BOARD.get(), ModItems.THIEF_TRACKER.get(),
                    ModItems.MAGICIAN_TOKEN.get(), Items.BELL, ModItems.THIEF_GUIDE.get()}[idx];
            case PLAY -> new Item[]{Items.CLOCK, Items.BELL, ModItems.ROPE.get(), ModItems.WHIP.get(), Items.BLAZE_POWDER, Items.EMERALD, Items.SPYGLASS, Items.OAK_SIGN,
                    ModItems.MAGICIAN_TOKEN.get(), Items.POTION, Items.ZOMBIE_HEAD, Items.COMMAND_BLOCK, Items.COMPARATOR}[idx];
        });
    }

    private void drawEntry(GuiGraphics g, int mx, int my) {
        int rx = rightX(), w = pageW(), top = y0 + 12;
        int idx = selected();
        heading(g, Component.translatable("thief.guide." + tab.key + "." + (idx + 1) + ".n"), rx, top, w);
        int textFrom = top + 18;
        if (tab == Tab.PEOPLE) {
            // the figure stands in a framed panel, the light falling off toward the foot of it, on a patch of shadow
            int py = top + 18, ph = 100;
            g.fill(rx, py, rx + w, py + ph, EDGE);
            g.fillGradient(rx + 1, py + 1, rx + w - 1, py + ph - 1, 0xFFF1E6C4, 0xFFD3C08E);
            int cx = rx + w / 2, feet = py + ph - 10;
            g.fill(cx - 22, feet, cx + 22, feet + 3, 0x30000000);
            g.fill(cx - 14, feet + 3, cx + 14, feet + 4, 0x20000000);
            drawFigure(g, mx, my, cx, feet, LOOKS[idx]);
            textFrom = py + ph + 6;
        } else if (tab == Tab.TOOLS) {
            drawRecipe(g, rx, top + 20, idx);
            textFrom = top + 20 + 62;
        }
        drawText(g, rx, textFrom, w, Component.translatable("thief.guide." + tab.key + "." + (idx + 1) + ".t").getString());
    }

    /** The figure, as it is drawn in the game, turning to look at the mouse. */
    private void drawFigure(GuiGraphics g, int mx, int my, int cx, int feetY, String look) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        ThiefEntity figure = figures.computeIfAbsent(look, l -> ThiefEntity.forGuide(minecraft.level, l));
        int scale = look.equals("hung") ? 32 : 40;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feetY, scale, (float) (cx - mx), (float) (feetY - 50 - my), figure);
    }

    // The recipes: nine slots and what is in them, then what comes out; the stacks are the ones the recipe is made of.
    private static ItemStack[] grid(Object... items) {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Object o = i < items.length ? items[i] : null;
            out[i] = o == null ? ItemStack.EMPTY : new ItemStack((ItemLike) o);
        }
        return out;
    }

    private void drawRecipe(GuiGraphics g, int rx, int ry, int idx) {
        ItemStack[] slots;
        ItemStack result;
        boolean shapeless = false;
        switch (idx) {
            case 0 -> {
                slots = grid(Items.STRING, Items.STRING, null, Items.STRING, null, Items.STRING, null, Items.STRING, Items.STRING);
                result = new ItemStack(ModItems.ROPE.get(), 2);
            }
            case 1 -> {
                slots = grid(null, null, Items.STRING, null, Items.STRING, Items.LEATHER, Items.STICK, null, null);
                result = new ItemStack(ModItems.WHIP.get());
            }
            case 2 -> {
                slots = grid(Items.OAK_LOG, null, Items.OAK_LOG, Items.STICK, ModItems.ROPE.get(), Items.STICK, Items.OAK_LOG, null, Items.OAK_LOG);
                result = new ItemStack(ModItems.RACK.get());
            }
            case 3 -> {
                slots = grid(Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.PAPER, Items.OAK_PLANKS, null, Items.STICK, null);
                result = new ItemStack(ModItems.BOUNTY_BOARD.get());
            }
            case 4 -> {
                slots = grid(Items.COMPASS, Items.SPYGLASS);
                result = new ItemStack(ModItems.THIEF_TRACKER.get());
                shapeless = true;
            }
            case 7 -> {
                slots = grid(Items.BOOK, Items.STRING);
                result = new ItemStack(ModItems.THIEF_GUIDE.get());
                shapeless = true;
            }
            default -> {
                // not made: a badge is dropped, a bell is the game's own
                ItemStack shown = idx == 5 ? new ItemStack(ModItems.MAGICIAN_TOKEN.get()) : new ItemStack(Items.BELL);
                slot(g, rx, ry, 54);
                g.pose().pushPose();
                g.pose().translate(rx + 7, ry + 7, 0);
                g.pose().scale(2.5F, 2.5F, 1.0F);
                g.renderItem(shown, 0, 0);
                g.pose().popPose();
                if (idx == 5) {
                    g.drawString(font, Component.translatable("thief.guide.recipe.drop"), rx + 62, ry + 22, SOFT, false);
                }
                return;
            }
        }
        for (int i = 0; i < 9; i++) {
            int sx = rx + (i % 3) * 18, sy = ry + (i / 3) * 18;
            slot(g, sx, sy, 18);
            if (!slots[i].isEmpty()) {
                g.renderItem(slots[i], sx + 1, sy + 1);
            }
        }
        // an arrow, drawn from rectangles, and the slot of what comes out
        int ay = ry + 26;
        g.fill(rx + 60, ay, rx + 74, ay + 2, INK);
        g.fill(rx + 72, ay - 3, rx + 74, ay + 5, INK);
        g.fill(rx + 74, ay - 1, rx + 76, ay + 3, INK);
        slot(g, rx + 80, ry + 15, 24);
        g.renderItem(result, rx + 84, ry + 19);
        g.renderItemDecorations(font, result, rx + 84, ry + 19);
        g.drawString(font, Component.translatable(shapeless ? "thief.guide.recipe.shapeless" : "thief.guide.recipe.craft"), rx + 112, ry + 23, SOFT, false);
    }

    // ------------------------------------------------------------------ the text ------------------------------------------------------------------

    /** A paragraph as a component: what is between double braces is a link that copies it, in blue, underlined, and without a break in it. */
    private static MutableComponent rich(String paragraph) {
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < paragraph.length()) {
            int a = paragraph.indexOf("{{", i);
            int b = a < 0 ? -1 : paragraph.indexOf("}}", a);
            if (a < 0 || b < 0) {
                out.append(Component.literal(paragraph.substring(i)));
                break;
            }
            if (a > i) {
                out.append(Component.literal(paragraph.substring(i, a)));
            }
            String copy = paragraph.substring(a + 2, b);
            out.append(Component.literal(copy.replace(' ', ' '))
                    .withStyle(s -> s.withColor(LINK & 0xFFFFFF).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy))));
            i = b + 2;
        }
        return out;
    }

    private void drawText(GuiGraphics g, int rx, int top, int w, String text) {
        int bottom = y0 + BOOK_H - 12;
        textTop = top;
        textBottom = bottom;
        lines.clear();
        int y = 0;
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                y += 5;
                continue;
            }
            for (FormattedCharSequence line : font.split(rich(paragraph), w - 4)) {
                lines.add(new TextLine(line, y));
                y += 10;
            }
            y += 3;                          // a little air between paragraphs
        }
        textHeight = y;
        int visible = bottom - top;
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, textHeight - visible)));
        // the clip is in the window's own units, not the book's
        g.enableScissor((int) ((rx - 2) * scale), (int) (top * scale), (int) ((rx + w + 2) * scale), (int) (bottom * scale));
        for (TextLine line : lines) {
            int ly = top + line.y() - textScroll;
            if (ly + 10 > top && ly < bottom) {
                g.drawString(font, line.text(), rx, ly, INK, false);
            }
        }
        g.disableScissor();
        if (textHeight > visible) {
            int barH = Math.max(12, visible * visible / textHeight);
            int barY = top + (visible - barH) * textScroll / Math.max(1, textHeight - visible);
            g.fill(rx + w, top, rx + w + 3, bottom, PAGE_DARK);
            g.fill(rx + w, barY, rx + w + 3, barY + barH, 0xFF8A7650);
        }
    }

    // ------------------------------------------------------------------ the mouse ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= scale;
        my /= scale;
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        int i = 0;
        for (Tab t : Tab.values()) {
            int tx = x0 + 4 + i * 80;
            if (mx >= tx && mx < tx + 78 && my >= y0 - 20 && my < y0) {
                tab = t;
                listScroll = 0;
                textScroll = 0;
                return true;
            }
            i++;
        }
        int lx = leftX(), top = y0 + 12;
        if (mx >= lx && mx < lx + pageW()) {
            for (int n = 0; n < visibleRows() && listScroll + n < tab.count; n++) {
                int ry = top + 18 + n * ROW;
                if (my >= ry && my < ry + ROW) {
                    SELECTED[tab.ordinal()] = listScroll + n;
                    textScroll = 0;
                    return true;
                }
            }
        }
        // a link in the text: it is copied
        if (mx >= rightX() && mx < rightX() + pageW() && my >= textTop && my < textBottom) {
            for (TextLine line : lines) {
                int ly = textTop + line.y() - textScroll;
                if (my >= ly && my < ly + 10) {
                    Style style = font.getSplitter().componentStyleAtWidth(line.text(), (int) (mx - rightX()));
                    if (style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
                        copied = style.getClickEvent().getValue();
                        copiedAt = System.currentTimeMillis();
                        handleComponentClicked(style);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        mx /= scale;
        if (mx < x0 + BOOK_W / 2) {
            listScroll = Math.max(0, Math.min(Math.max(0, tab.count - visibleRows()), listScroll - (int) Math.signum(delta)));
        } else {
            textScroll = Math.max(0, textScroll - (int) (delta * 20));
        }
        return true;
    }
}
