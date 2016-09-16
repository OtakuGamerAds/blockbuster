package mchorse.blockbuster.common.tileentity;

import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.common.block.AbstractBlockDirector;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.Mocap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Abstract Tile Entity Director
 *
 * This class is base class for director block's tile entities. This class
 * provides basic methods for changing state of the block, and defines
 * some abstract methods for playback.
 */
public abstract class AbstractTileEntityDirector extends TileEntity implements ITickable
{
    public List<String> actors = new ArrayList<String>();

    /**
     * This tick used for checking if actors still playing
     */
    private int tick = 0;

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate)
    {
        return false;
    }

    /* Read/write this TE to disk */

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        this.readListFromNBT(compound, "Actors", this.actors);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        this.saveListToNBT(compound, "Actors", this.actors);

        return compound;
    }

    /* NBT list utils */

    /**
     * Read string typed list from NBT
     */
    protected void readListFromNBT(NBTTagCompound compound, String key, List<String> list)
    {
        NBTTagList tagList = compound.getTagList(key, 8);
        list.clear();

        for (int i = 0; i < tagList.tagCount(); i++)
        {
            list.add(tagList.getStringTagAt(i));
        }
    }

    /**
     * Write string typed list from NBT
     */
    protected void saveListToNBT(NBTTagCompound compound, String key, List<String> list)
    {
        NBTTagList tagList = new NBTTagList();

        for (int i = 0; i < list.size(); i++)
        {
            tagList.appendTag(new NBTTagString(list.get(i)));
        }

        compound.setTag(key, tagList);
    }

    /* Public API */

    /**
     * Remove everything
     */
    public void reset()
    {
        this.actors = new ArrayList<String>();
        this.markDirty();
    }

    /**
     * Remove an actor by id.
     */
    public void remove(int id)
    {
        this.actors.remove(id);
        this.markDirty();
    }

    /**
     * Add an actor to this director block (dah, TE is part of the director
     * block)
     */
    public boolean add(EntityActor actor)
    {
        String id = actor.getUniqueID().toString();

        if (!this.actors.contains(id))
        {
            actor.directorBlock = this.getPos();

            this.actors.add(id);
            this.markDirty();

            return true;
        }

        return false;
    }

    /**
     * Get the cast
     *
     * Basically, return all entities/entity ids for display
     */
    public List<String> getCast()
    {
        return this.actors;
    }

    /**
     * Start scene's playback
     */
    public abstract void startPlayback();

    /**
     * Stop scene's playback
     */
    public abstract void stopPlayback();

    /**
     * Toggle scene's playback
     */
    public boolean togglePlayback()
    {
        if (this.isPlaying())
        {
            this.stopPlayback();
        }
        else
        {
            this.startPlayback();
        }

        return this.isPlaying();
    }

    /**
     * Checks every 4 ticks if the actors (that registered by this TE) are
     * still playing their roles.
     */
    @Override
    public void update()
    {
        if (this.worldObj.isRemote || !this.isPlaying() || this.tick-- > 0)
        {
            return;
        }

        this.areActorsStillPlaying();
        this.tick = 4;
    }

    /**
     * Does what it says to do – checking if the actors still playing their
     * roles (not finished playback).
     */
    protected void areActorsStillPlaying()
    {
        int count = 0;

        for (String id : this.actors)
        {
            EntityActor actor = (EntityActor) Mocap.entityByUUID(this.worldObj, id);

            if (!Mocap.playbacks.containsKey(actor))
            {
                count++;
            }
        }

        if (count == this.actors.size())
        {
            this.playBlock(false);
        }
    }

    /**
     * Set the state of the block playing (needed to update redstone thingy-stuff)
     */
    protected void playBlock(boolean isPlaying)
    {
        this.worldObj.setBlockState(this.pos, this.worldObj.getBlockState(this.pos).withProperty(AbstractBlockDirector.PLAYING, isPlaying));
    }

    /**
     * Checks if block's state isPlaying is true
     */
    protected boolean isPlaying()
    {
        return this.worldObj.getBlockState(this.pos).getValue(AbstractBlockDirector.PLAYING);
    }
}