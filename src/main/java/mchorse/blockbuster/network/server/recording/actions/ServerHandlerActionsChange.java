package mchorse.blockbuster.network.server.recording.actions;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordTimeline.Selection;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.actions.PacketActionsChange;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ServerHandlerActionsChange extends ServerMessageHandler<PacketActionsChange>
{
    @Override
    public void run(EntityPlayerMP player, PacketActionsChange message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Record record = null;

        try
        {
            record = CommonProxy.manager.get(message.getFilename());
        }
        catch (Exception e)
        {}

        if (record == null)
        {
            return;
        }

        if (message.getFromTick() >= 0)
        {
            switch (message.getStatus())
            {
                case DELETE:
                    record.removeActionsMask(message.getFromTick(), message.getMask());

                    break;
                case ADD:
                    if (message.getIndex() != -1)
                    {
                        if (message.containsOneAction())
                        {
                            record.addActionCollection(message.getFromTick(), message.getIndex(), message.getActions());
                        }
                    }
                    else
                    {
                        record.addActionCollection(message.getFromTick(), message.getActions());
                    }

                    break;
                case EDIT:
                    if (message.getIndex() != -1)
                    {
                        if (message.containsOneAction())
                        {
                            record.replaceAction(message.getFromTick(), message.getIndex(), message.getActions().get(0).get(0));
                        }
                    }

                    break;
            }

            try
            {
                RecordUtils.saveRecord(record, false, false);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a deletion package to the server
     */
    @SideOnly(Side.CLIENT)
    public static void deleteActions(Record record, int from, List<List<Boolean>> mask)
    {
        record.removeActionsMask(from, mask);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, from, mask));
    }

    /**
     * Send a package to the server to add the given actions at the given tick
     */
    @SideOnly(Side.CLIENT)
    public static void addActions(List<List<Action>> actions, Record record, int tick)
    {
        record.addActionCollection(tick, actions);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, actions, PacketActionsChange.Type.ADD));
    }

    @SideOnly(Side.CLIENT)
    public static void addActions(List<List<Action>> actions, Record record, int tick, int index)
    {
        if (index == -1)
        {
            addActions(actions, record, tick);
        }
        else
        {
            record.addActionCollection(tick, index, actions);
            Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, actions, PacketActionsChange.Type.ADD));
        }
    }

    /**
     * Send a package to the server to add an action at a specific index
     * @param action
     * @param record
     * @param tick
     * @param index
     */
    @SideOnly(Side.CLIENT)
    public static void addAction(Action action, Record record, int tick, int index)
    {
        record.addAction(tick, index, action);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, action, PacketActionsChange.Type.ADD));
    }

    @SideOnly(Side.CLIENT)
    public static void editAction(Action action, Record record, int tick, int index)
    {
        record.replaceAction(tick, index, action);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, action, PacketActionsChange.Type.EDIT));
    }
}