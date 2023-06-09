package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.recording.PacketFramesLoad;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ClientMessageHandler;
import mchorse.mclib.utils.Consumers;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Consumer;

/**
 * Client handler frames
 *
 * This client handler is responsible for inserting a record received from the
 * server into client's record repository (record manager).
 */
public class ClientHandlerFramesLoad extends ClientMessageHandler<PacketFramesLoad>
{
    private static final Consumers<Record> consumers = new Consumers();

    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketFramesLoad message)
    {
        Record record = null;

        if (message.getState() == PacketFramesLoad.State.LOAD)
        {
            record = new Record(message.filename);
            record.frames = message.frames;
            record.preDelay = message.preDelay;
            record.postDelay = message.postDelay;

            ClientProxy.manager.records.put(message.filename, record);

            if (ClientProxy.panels != null)
            {
                ClientProxy.panels.recordingEditorPanel.reselectRecord(record);
            }
        }
        else if (message.getState() == PacketFramesLoad.State.NOCHANGES)
        {
            record = ClientProxy.manager.records.get(message.filename);
        }

        if (message.getCallbackID().isPresent())
        {
            consumers.consume(message.getCallbackID().get(), record);
        }
    }

    public static int registerConsumer(Consumer<Record> consumer)
    {
        return consumers.register(consumer);
    }
}