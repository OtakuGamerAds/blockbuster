package mchorse.blockbuster.client.gui.elements;

import java.util.Collections;
import java.util.Comparator;
import java.util.function.Consumer;

import mchorse.blockbuster.client.gui.dashboard.GuiDashboard;
import mchorse.blockbuster.utils.MultiResourceLocation;
import mchorse.blockbuster.utils.RLUtils;
import mchorse.mclib.client.gui.framework.GuiTooltip;
import mchorse.mclib.client.gui.framework.elements.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.widgets.buttons.GuiTextureButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public class GuiTexturePicker extends GuiElement
{
    public GuiTextElement text;
    public GuiButtonElement<GuiButton> pick;
    public GuiResourceLocationList picker;

    public GuiButtonElement<GuiButton> multi;
    public GuiButtonElement<GuiTextureButton> add;
    public GuiButtonElement<GuiTextureButton> remove;
    public GuiResourceLocationList multiList;

    public Consumer<ResourceLocation> callback;
    public MultiResourceLocation current;

    public GuiTexturePicker(Minecraft mc, Consumer<ResourceLocation> callback)
    {
        super(mc);

        this.text = new GuiTextElement(mc, 1000, (str) -> this.setCurrent(str.isEmpty() ? null : RLUtils.create(str)));
        this.pick = GuiButtonElement.button(mc, "X", (b) -> this.setVisible(false));
        this.picker = new GuiResourceLocationList(mc, (rl) ->
        {
            this.setCurrent(rl);
            this.text.setText(rl == null ? "" : rl.toString());
        });

        this.multi = GuiButtonElement.button(mc, I18n.format("blockbuster.gui.multi_skin"), (b) -> this.toggleMulti());
        this.multiList = new GuiResourceLocationList(mc, (rl) -> this.displayCurrent(this.multiList.getCurrent()));
        this.add = GuiButtonElement.icon(mc, GuiDashboard.ICONS, 32, 32, 32, 48, (b) -> this.addMultiSkin());
        this.remove = GuiButtonElement.icon(mc, GuiDashboard.ICONS, 64, 32, 64, 48, (b) -> this.removeMultiSkin());

        this.createChildren();
        this.text.resizer().set(110, 5, 0, 20).parent(this.area).w(1, -140);
        this.pick.resizer().set(0, 5, 20, 20).parent(this.area).x(1, -25);
        this.picker.resizer().set(110, 30, 0, 0).parent(this.area).w(1, -115).h(1, -35);

        this.multi.resizer().parent(this.area).set(5, 5, 100, 20);
        this.add.resizer().parent(this.area).set(67, 7, 16, 16);
        this.remove.resizer().relative(this.add.resizer()).set(20, 0, 16, 16);
        this.multiList.resizer().set(5, 30, 100, 0).parent(this.area).h(1, -35);

        this.children.add(this.text, this.pick, this.picker, this.multi, this.multiList, this.add, this.remove);
        this.callback = callback;
    }

    private void addMultiSkin()
    {
        ResourceLocation rl = this.picker.getCurrent();

        if (rl == null && !this.text.field.getText().isEmpty())
        {
            rl = RLUtils.create(this.text.field.getText());
        }

        this.multiList.add(rl);
        this.multiList.current = this.multiList.getList().indexOf(rl);

        if (this.multiList.current >= 0)
        {
            this.displayCurrent(this.multiList.getCurrent());
        }
    }

    private void removeMultiSkin()
    {
        if (this.multiList.current >= 0)
        {
            this.multiList.getList().remove(this.multiList.current);
            this.multiList.update();
            this.multiList.current--;

            if (this.multiList.current >= 0)
            {
                this.displayCurrent(this.multiList.getCurrent());
            }
        }
    }

    private void setCurrent(ResourceLocation rl)
    {
        if (this.current != null)
        {
            if (this.multiList.current != -1 && rl != null)
            {
                this.multiList.getList().set(this.multiList.current, rl);
            }

            rl = this.current;
        }

        if (this.callback != null)
        {
            this.callback.accept(rl);
        }
    }

    private void displayCurrent(ResourceLocation rl)
    {
        this.picker.setCurrent(rl);
        this.text.setText(rl == null ? "" : rl.toString());
    }

    private void toggleMulti()
    {
        this.setMultiSkin(this.current == null ? new MultiResourceLocation(this.text.field.getText()) : null);

        if (this.picker.getCurrent() != null)
        {
            this.setCurrent(this.picker.getCurrent());
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        /* Necessary measure to avoid triggering buttons when you press 
         * on a text field, for example */
        return super.mouseClicked(mouseX, mouseY, mouseButton) || (this.isVisible() && this.area.isInside(mouseX, mouseY));
    }

    public void set(ResourceLocation skin)
    {
        this.text.field.setText(skin == null ? "" : skin.toString());
        this.text.field.setCursorPositionZero();
        this.picker.setCurrent(skin);

        this.setMultiSkin(skin);
    }

    private void setMultiSkin(ResourceLocation skin)
    {
        boolean show = skin instanceof MultiResourceLocation;

        if (show)
        {
            this.current = (MultiResourceLocation) skin;

            this.multiList.current = this.current.children.isEmpty() ? -1 : 0;
            this.multiList.setList(this.current.children);

            this.picker.resizer().set(110, 30, 0, 0).parent(this.area).w(1, -115).h(1, -35);
            this.multi.resizer().set(5, 5, 60, 20).parent(this.area);
        }
        else
        {
            this.current = null;
            this.multiList.setList(null);

            this.picker.resizer().set(5, 30, 0, 0).parent(this.area).w(1, -10).h(1, -35);
            this.multi.resizer().set(5, 5, 100, 20).parent(this.area);
        }

        this.multiList.setVisible(show);
        this.add.setVisible(show);
        this.remove.setVisible(show);

        GuiScreen screen = this.mc.currentScreen;

        this.picker.resize(screen.width, screen.height);
        this.multi.resize(screen.width, screen.height);
    }

    @Override
    public void draw(GuiTooltip tooltip, int mouseX, int mouseY, float partialTicks)
    {
        drawRect(this.area.x, this.area.y, this.area.getX(1), this.area.getY(1), 0xaa000000);

        if (this.picker.getList().isEmpty())
        {
            this.drawCenteredString(this.font, I18n.format("blockbuster.gui.no_data"), this.area.getX(0.5F), this.area.getY(0.5F), 0xffffff);
        }

        if (this.multiList.isVisible())
        {
            this.multiList.area.draw(0xaa000000);
        }

        super.draw(tooltip, mouseX, mouseY, partialTicks);
    }

    public static class GuiResourceLocationList extends GuiListElement<ResourceLocation>
    {
        public GuiResourceLocationList(Minecraft mc, Consumer<ResourceLocation> callback)
        {
            super(mc, callback);

            this.scroll.scrollItemSize = 16;
        }

        @Override
        public void sort()
        {
            Collections.sort(this.list, new Comparator<ResourceLocation>()
            {
                @Override
                public int compare(ResourceLocation o1, ResourceLocation o2)
                {
                    return o1.toString().compareToIgnoreCase(o2.toString());
                }
            });
        }

        @Override
        public void drawElement(ResourceLocation element, int i, int x, int y, boolean hover)
        {
            if (this.current == i)
            {
                Gui.drawRect(x, y, x + this.scroll.w, y + this.scroll.scrollItemSize, 0x880088ff);
            }

            this.font.drawStringWithShadow(element.toString(), x + 4, y + 4, hover ? 16777120 : 0xffffff);
        }
    }
}