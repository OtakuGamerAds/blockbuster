package mchorse.blockbuster.recording.data;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.actions.MountingAction;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.mclib.utils.MathUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class stores actions and frames states for a recording (to be played
 * back or while recording).
 *
 * There's two list arrays in this class, index in both of these arrays
 * represents the frame position (0 is first frame). Frames list is always
 * populated, but actions list will contain some nulls.
 */
public class Record
{
    public static final FoundAction ACTION = new FoundAction();
    public static final MorphAction MORPH = new MorphAction();

    /**
     * Signature of the recording. If the first short of the record file isn't
     * this file, then the
     */
    public static final short SIGNATURE = 148;

    /**
     * Filename of this record
     */
    public String filename;

    /**
     * Version of this record
     */
    public short version = SIGNATURE;

    /**
     * Pre-delay same thing as post-delay but less useful
     */
    public int preDelay = 0;

    /**
     * Post-delay allows actors to stay longer on the screen before 
     * whoosing into void
     */
    public int postDelay = 0;

    /**
     * Recorded actions.
     * The list contains every frame of the recording.
     * If no action is present at a frame, the frame will be null.
     */
    public List<List<Action>> actions = new ArrayList<List<Action>>();

    /**
     * Recorded frames
     */
    public List<Frame> frames = new ArrayList<Frame>();

    /**
     * Player data which was recorded when player started recording 
     */
    public NBTTagCompound playerData;

    /**
     * Unload timer. Used only on server side.
     */
    public int unload;

    /**
     * Whether this record has changed elements
     */
    public boolean dirty;

    /**
     * Can be null
     */
    private Replay replay;

    public Record(String filename)
    {
        this.filename = filename;
        this.resetUnload();
    }

    /**
     * Set this replay to the reference of the provided replay
     * @param replay
     */
    public void setReplay(Replay replay)
    {
        this.replay = replay;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    /**
     * Get the full length (including post and pre delays) of this record in frames/ticks
     */
    public int getFullLength()
    {
        return this.preDelay + this.getLength() + this.postDelay;
    }

    /**
     * Get the length of this record in frames/ticks
     */
    public int getLength()
    {
        return Math.max(this.actions.size(), this.frames.size());
    }

    /**
     * @return actions at the given tick. Can return null if nothing is present there.
     */
    public List<Action> getActions(int tick)
    {
        if (tick >= this.actions.size() || tick < 0)
        {
            return null;
        }

        return this.actions.get(tick);
    }

    /**
     * @param tick negative values are allowed as they will return null
     * @param index negative values are allowed as they will return null
     * @return action at the given tick and index.
     *         Returns null if none was found or if the tick or index are out of bounds.
     */
    public Action getAction(int tick, int index)
    {
        List<Action> actions = this.getActions(tick);

        if (actions != null && index >= 0 && index < actions.size())
        {
            return actions.get(index);
        }

        return null;
    }

    /**
     * If fromIndex0 and toIndex0 are both -1 every action at the frame in the range will be added.
     * @param fromTick0 from tick, inclusive
     * @param toTick0 to tick, inclusive
     * @param fromIndex0 from action index, inclusive. Can be -1 only together with toIndex0.
     * @param toIndex0 to action index, inclusive. Can be -1 only together with fromIndex0.
     * @return a new list containing the actions in the specified ranges. The list can contain null values.
     * @throws IndexOutOfBoundsException if fromTick < 0 || toTick > size of {@link #actions}
     */
    public List<List<Action>> getActions(int fromTick0, int toTick0, int fromIndex0, int toIndex0)
    {
        int fromIndex = Math.min(fromIndex0, toIndex0);
        int toIndex = Math.max(fromIndex0, toIndex0);
        int fromTick = Math.min(fromTick0, toTick0);
        int toTick = Math.max(fromTick0, toTick0);

        if (fromTick0 < 0 || toTick0 < 0 || ((fromIndex0 != -1 || toIndex0 != -1) && fromIndex0 < 0 && toIndex0 < 0)
            || toTick >= this.actions.size())
        {
            return new ArrayList<>();
        }

        List<List<Action>> actionRange = this.actions.subList(fromTick, toTick + 1);

        if (actionRange != null)
        {
            actionRange = new ArrayList<>(actionRange);

            for (int i = 0; i < actionRange.size(); i++)
            {
                List<Action> frame = actionRange.get(i);

                if (frame != null && !frame.isEmpty())
                {
                    if (fromIndex == -1 && toIndex == -1)
                    {
                        actionRange.set(i, new ArrayList<>(frame));
                    }
                    else if (fromIndex >= frame.size())
                    {
                        actionRange.set(i, null);
                    }
                    else
                    {
                        int i0 = MathUtils.clamp(fromIndex, 0, frame.size() - 1);
                        int i1 = MathUtils.clamp(toIndex + 1, 0, frame.size());

                        actionRange.set(i, new ArrayList<>(frame.subList(i0, i1)));
                    }
                }
                else
                {
                    actionRange.set(i, null);
                }
            }
        }

        return actionRange;
    }


    /**
     * @param fromTick0 from tick, inclusive
     * @param toTick0 to tick, inclusive
     * @return a new list containing the actions in the specified ranges. The list can contain null values.
     * @throws IndexOutOfBoundsException if fromTick < 0 || toTick > size of {@link #actions}
     */
    public List<List<Action>> getActions(int fromTick0, int toTick0)
    {
        return this.getActions(fromTick0, toTick0, -1, -1);
    }

    /**
     * @return convert the given action list into a boolean mask, which can be used for deletion.
     *         Returns an empty list if the specified tick is out of range.
     */
    public List<List<Boolean>> getActionsMask(int fromTick, List<List<Action>> actions)
    {
        if (fromTick < 0 || fromTick >= this.actions.size())
        {
            return new ArrayList<>();
        }

        List<List<Boolean>> mask = new ArrayList<>();

        for (int t = fromTick; t < this.actions.size() && t - fromTick < actions.size(); t++)
        {
            List<Boolean> maskFrame = new ArrayList<>();

            if (actions.get(t - fromTick) != null && this.actions.get(t) != null && !this.actions.get(t).isEmpty())
            {
                for (int a = 0; a < this.actions.get(t).size(); a++)
                {
                    maskFrame.add(actions.get(t - fromTick).contains(this.actions.get(t).get(a)));
                }
            }
            else
            {
                maskFrame.add(false);
            }

            mask.add(maskFrame);
        }

        return mask;
    }

    /**
     * @param tick
     * @param action
     * @return the index of the provided action at the provided tick.
     *         If nothing is found or if the tick is out of range or if the tick has no actions -1 will be returned.
     */
    public int getActionIndex(int tick, Action action)
    {
        if (tick < 0 || tick >= this.actions.size()
            || this.actions.get(tick) == null || this.actions.get(tick).isEmpty() || action == null)
        {
            return -1;
        }

        for (int a = 0; a < this.actions.get(tick).size(); a++)
        {
            if (this.actions.get(tick).get(a) == action)
            {
                return a;
            }
        }

        return -1;
    }

    /**
     * @param action
     * @return int array {tick, index} of the found action. If nothing was found the values will be -1
     */
    public int[] findAction(Action action)
    {
        if (action == null)
        {
            return new int[]{-1, -1};
        }

        for (int t = 0; t < this.actions.size(); t++)
        {
            int i = this.getActionIndex(t, action);

            if (i != -1) return new int[]{t, i};
        }

        return new int[]{-1, -1};
    }

    /**
     * Get frame on given tick 
     */
    public Frame getFrame(int tick)
    {
        if (tick >= this.frames.size() || tick < 0)
        {
            return null;
        }

        return this.frames.get(tick);
    }

    /**
     * Reset unloading timer
     */
    public void resetUnload()
    {
        this.unload = Blockbuster.recordUnloadTime.get();
    }

    public void applyFrame(int tick, EntityLivingBase actor, boolean force)
    {
        this.applyFrame(tick, actor, force, false);
    }

    /**
     * Apply a frame at given tick on the given actor. 
     */
    public void applyFrame(int tick, EntityLivingBase actor, boolean force, boolean realPlayer)
    {
        if (tick >= this.frames.size() || tick < 0)
        {
            return;
        }

        Frame frame = this.frames.get(tick);

        frame.apply(actor, this.replay, force);

        if (realPlayer)
        {
            actor.setLocationAndAngles(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
            actor.motionX = frame.motionX;
            actor.motionY = frame.motionY;
            actor.motionZ = frame.motionZ;

            actor.onGround = frame.onGround;

            if (frame.hasBodyYaw)
            {
                actor.renderYawOffset = frame.bodyYaw;
            }

            if (actor.world.isRemote)
            {
                this.applyClientMovement(actor, frame);
            }

            actor.setSneaking(frame.isSneaking);
            actor.setSprinting(frame.isSprinting);

            if (actor.world.isRemote)
            {
                this.applyFrameClient(actor, null, frame);
            }
        }

        if (actor.world.isRemote && Blockbuster.actorFixY.get())
        {
            actor.posY = frame.y;
        }

        Frame prev = this.frames.get(Math.max(0, tick - 1));

        if (realPlayer || !actor.world.isRemote)
        {
            actor.lastTickPosX = prev.x;
            actor.lastTickPosY = prev.y;
            actor.lastTickPosZ = prev.z;
            actor.prevPosX = prev.x;
            actor.prevPosY = prev.y;
            actor.prevPosZ = prev.z;

            actor.prevRotationYaw = prev.yaw;
            actor.prevRotationPitch = prev.pitch;
            actor.prevRotationYawHead = prev.yawHead;

            if (prev.hasBodyYaw)
            {
                actor.prevRenderYawOffset = prev.bodyYaw;
            }

            if (actor.world.isRemote)
            {
                this.applyFrameClient(actor, prev, frame);
            }
        }
        else if (actor instanceof EntityActor)
        {
            ((EntityActor) actor).prevRoll = prev.roll;
        }

        /* Override fall distance, apparently fallDistance gets reset
         * faster than RecordRecorder can record both onGround and
         * fallDistance being correct for player, so we just hack */
        actor.fallDistance = prev.fallDistance;

        if (tick < this.frames.size() - 1)
        {
            Frame next = this.frames.get(tick + 1);

            /* Walking sounds */
            if (actor instanceof EntityPlayer)
            {
                double dx = next.x - frame.x;
                double dy = next.y - frame.y;
                double dz = next.z - frame.z;

                actor.distanceWalkedModified = actor.distanceWalkedModified + MathHelper.sqrt(dx * dx + dz * dz) * 0.32F;
                actor.distanceWalkedOnStepModified = actor.distanceWalkedOnStepModified + MathHelper.sqrt(dx * dx + dy * dy + dz * dz) * 0.32F;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void applyClientMovement(EntityLivingBase actor, Frame frame)
    {
        if (actor instanceof EntityPlayerSP)
        {
            MovementInput input = ((EntityPlayerSP) actor).movementInput;

            input.sneak = frame.isSneaking;
        }
    }

    @SideOnly(Side.CLIENT)
    private void applyFrameClient(EntityLivingBase actor, Frame prev, Frame frame)
    {
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        if (actor == player)
        {
            CameraHandler.setRoll(prev == null ? frame.roll : prev.roll, frame.roll);
        }
    }

    public Frame getFrameSafe(int tick)
    {
        if (this.frames.isEmpty())
        {
            return null;
        }

        return this.frames.get(MathUtils.clamp(tick, 0, this.frames.size() - 1));
    }

    /**
     * Apply an action at the given tick on the given actor. Don't pass tick
     * value less than 0, otherwise you might experience game crash.
     */
    public void applyAction(int tick, EntityLivingBase actor)
    {
        this.applyAction(tick, actor, false);
    }

    /**
     * Apply an action at the given tick on the given actor. Don't pass tick
     * value less than 0, otherwise you might experience game crash.
     */
    public void applyAction(int tick, EntityLivingBase actor, boolean safe)
    {
        if (tick >= this.actions.size() || tick < 0)
        {
            return;
        }

        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            for (Action action : actions)
            {
                if (safe && !action.isSafe())
                {
                    continue;
                }

                try
                {
                    action.apply(actor);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Seek the nearest morph action
     */
    public FoundAction seekMorphAction(int tick, MorphAction last)
    {
        /* I hope it won't cause a lag...  */
        int threshold = 0;

        boolean canRet = last == null;

        while (tick >= threshold)
        {
            List<Action> actions = this.actions.get(tick);

            if (actions == null)
            {
                tick--;

                continue;
            }

            for (int i = actions.size() - 1; i >= 0; i--)
            {
                Action action = actions.get(i);

                if (!canRet && action == last)
                {
                    canRet = true;
                }
                else if (canRet && action instanceof MorphAction)
                {
                    ACTION.set(tick, (MorphAction) action);

                    return ACTION;
                }
            }

            tick--;
        }

        return null;
    }

    /**
     * Apply previous morph
     */
    public void applyPreviousMorph(EntityLivingBase actor, Replay replay, int tick, MorphType type)
    {
        boolean pause = type != MorphType.REGULAR && Blockbuster.recordPausePreview.get();
        AbstractMorph replayMorph = replay == null ? null : replay.morph;

        /* when the tick is at the end - do not apply replay's morph - stay at the last morph */
        if (tick >= this.actions.size()) return;

        FoundAction found = this.seekMorphAction(tick, null);

        if (found != null)
        {
            try
            {
                MorphAction action = found.action;

                if (pause && action.morph instanceof ISyncableMorph)
                {
                    int foundTick = found.tick;
                    int offset = tick - foundTick;

                    found = this.seekMorphAction(foundTick, action);
                    AbstractMorph previous = found == null ? replayMorph : found.action.morph;
                    int previousOffset = foundTick - (found == null ? 0 : found.tick);

                    action.applyWithOffset(actor, offset, previous, previousOffset, type == MorphType.FORCE);
                }
                else
                {
                    action.apply(actor);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else if (replay != null)
        {
            if (pause && replay.morph != null)
            {
                MORPH.morph = replay.morph;
                MORPH.applyWithOffset(actor, tick, null, 0, type == MorphType.FORCE);
            }
            else if (type == MorphType.FORCE && replay.morph != null)
            {
                MORPH.morph = replay.morph;
                MORPH.applyWithForce(actor);
            }
            else
            {
                replay.apply(actor);
            }
        }
    }

    /**
     * Reset the actor based on this record
     */
    public void reset(EntityLivingBase actor)
    {
        if (actor.isRiding())
        {
            this.resetMount(actor);
        }

        if (actor.getHealth() > 0.0F)
        {
            this.applyFrame(0, actor, true);

            /* Reseting actor's state */
            actor.setSneaking(false);
            actor.setSprinting(false);
            actor.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
            actor.setItemStackToSlot(EntityEquipmentSlot.CHEST, ItemStack.EMPTY);
            actor.setItemStackToSlot(EntityEquipmentSlot.LEGS, ItemStack.EMPTY);
            actor.setItemStackToSlot(EntityEquipmentSlot.FEET, ItemStack.EMPTY);
            actor.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
            actor.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    /**
     * Reset actor's mount
     */
    protected void resetMount(EntityLivingBase actor)
    {
        int index = -1;

        /* Find at which tick player has mounted a vehicle */
        for (int i = 0, c = this.actions.size(); i < c; i++)
        {
            List<Action> actions = this.actions.get(i);

            if (actions == null)
            {
                continue;
            }

            for (Action action : actions)
            {
                if (action instanceof MountingAction)
                {
                    MountingAction act = (MountingAction) action;

                    if (act.isMounting)
                    {
                        index = i + 1;
                        break;
                    }
                }
            }
        }

        actor.dismountRidingEntity();

        if (index != -1)
        {
            Frame frame = this.frames.get(index);

            if (frame != null)
            {
                Entity mount = actor.getRidingEntity();

                if (mount != null && !(mount instanceof EntityActor))
                {
                    mount.setPositionAndRotation(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
                }
            }
        }
    }

    /**
     * Add an action to the record
     */
    public void addAction(int tick, Action action)
    {
        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            actions.add(action);
        }
        else
        {
            actions = new ArrayList<Action>();
            actions.add(action);

            this.actions.set(tick, actions);
        }
    }

    /**
     * Add an action to the recording at the specified tick and index.
     * If the index is greater than the frame's size at the specified tick,
     * the action will just be appended to the actions of the frame.
     * @param tick
     * @param index
     * @param action
     */
    public void addAction(int tick, int index, Action action)
    {
        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            if (index == -1 || index > actions.size())
            {
                actions.add(action);
            }
            else
            {
                actions.add(index, action);
            }
        }
        else
        {
            actions = new ArrayList<Action>();
            actions.add(action);

            this.actions.set(tick, actions);
        }
    }

    /**
     * Adds many ticks of actions beginning at the provided fromTick.
     * @param tick
     * @param actions
     */
    public void addActionCollection(int tick, List<List<Action>> actions)
    {
        this.addActionCollection(tick, -1, actions);
    }

    /**
     * Add an action collection beginning at the specified tick at the specified index.
     * If the index is -1, the actions at the frames will just be added on top.
     * @param tick
     * @param index can be -1
     * @param actions
     */
    public void addActionCollection(int tick, int index, List<List<Action>> actions)
    {
        if (index < -1 || tick < 0 || tick >= this.actions.size() || actions == null)
        {
            return;
        }

        for (int i = tick; i < this.actions.size() && i - tick < actions.size(); i++)
        {
            List<Action> frame = this.actions.get(i);
            List<Action> actionFrame = actions.get(i - tick) != null && !actions.get(i - tick).isEmpty() ? new ArrayList<>(actions.get(i - tick)) : null;

            if (frame == null)
            {
                this.actions.set(i, actionFrame);
            }
            else if (actionFrame != null)
            {
                if (index > frame.size() || index == -1)
                {
                    frame.addAll(actionFrame);
                }
                else
                {
                    frame.addAll(index, actionFrame);
                }
            }
        }
    }

    public void addActions(int tick, List<Action> actions)
    {
        if (tick < 0 || tick >= this.actions.size())
        {
            return;
        }

        List<Action> present = this.actions.get(tick);

        if (present == null)
        {
            this.actions.set(tick, actions);
        }
        else if (actions != null)
        {
            present.addAll(actions);
        }
    }

    /**
     * Remove an action at given tick and index 
     */
    public void removeAction(int tick, int index)
    {
        if (index == -1)
        {
            this.actions.set(tick, null);
        }
        else
        {
            List<Action> actions = this.actions.get(tick);

            if (index >= 0 && index < actions.size())
            {
                actions.remove(index);

                if (actions.isEmpty())
                {
                    this.actions.set(tick, null);
                }
            }
        }
    }

    public void removeActions(int fromTick, List<List<Action>> actions)
    {
        for (int tick = fromTick, c = 0; tick < this.actions.size() && c < actions.size(); tick++, c++)
        {
            if (this.actions.get(tick) != null && actions.get(c) != null)
            {
                this.actions.get(tick).removeAll(actions.get(c));
            }

            if (this.actions.get(tick) != null && this.actions.get(tick).isEmpty())
            {
                this.actions.set(tick, null);
            }
        }
    }

    /**
     * Remove actions based on the boolean mask provided.
     * @param fromTick
     * @param mask
     */
    public void removeActionsMask(int fromTick, List<List<Boolean>> mask)
    {
        for (int tick = fromTick, c = 0; tick < this.actions.size() && c < mask.size(); tick++, c++)
        {
            if (this.actions.get(tick) != null && mask.get(c) != null)
            {
                List<Action> remove = new ArrayList<>();

                for (int a = 0; a < this.actions.get(tick).size() && a < mask.get(c).size(); a++)
                {
                    if (mask.get(c).get(a))
                    {
                        remove.add(this.actions.get(tick).get(a));
                    }
                }

                this.actions.get(tick).removeAll(remove);
            }
        }
    }

    /**
     * Remove actions from tick and to tick inclusive and at every tick
     * remove from index to index inclusive.
     * If both index parameters are -1, all actions at the respective ticks will be deleted.
     * @param fromTick0
     * @param toTick0
     * @param fromIndex0
     * @param toIndex0
     */
    public void removeActions(int fromTick0, int toTick0, int fromIndex0, int toIndex0)
    {
        int fromIndex = Math.min(fromIndex0, toIndex0);
        int toIndex = Math.max(fromIndex0, toIndex0);
        int fromTick = Math.min(fromTick0, toTick0);
        int toTick = Math.max(fromTick0, toTick0);
        int frameCount = this.actions.size();

        if (fromIndex == -1 && toIndex == -1)
        {
            for (int tick = fromTick; tick <= toTick && tick < frameCount; tick++)
            {
                this.actions.set(tick, null);
            }
        }
        else
        {
            for (int tick = fromTick; tick <= toTick && tick < frameCount; tick++)
            {
                List<Action> actions = this.actions.get(tick);

                if (actions == null) continue;

                if (fromIndex != -1 && toIndex != -1)
                {
                    int max = toIndex;

                    while (fromIndex <= max && fromIndex < actions.size())
                    {
                        actions.remove(fromIndex);

                        max--;
                    }
                }
                else
                {
                    int index = fromIndex == -1 ? toIndex : fromIndex;

                    if (index < actions.size())
                    {
                        actions.remove(index);
                    }
                }

                if (actions.isEmpty())
                {
                    this.actions.set(tick, null);
                }
            }
        }
    }

    /**
     * Replace an action at given tick and index
     */
    public void replaceAction(int tick, int index, Action action)
    {
        if (tick < 0 || tick >= this.actions.size())
        {
            return;
        }

        List<Action> actions = this.actions.get(tick);

        if (actions == null || index < 0 || index >= actions.size())
        {
            this.addAction(tick, action);
        }
        else
        {
            actions.set(index, action);
        }
    }

    /**
     * Create a copy of this record
     */
    @Override
    public Record clone()
    {
        Record record = new Record(this.filename);

        record.version = this.version;
        record.preDelay = this.preDelay;
        record.postDelay = this.postDelay;

        for (Frame frame : this.frames)
        {
            record.frames.add(frame.copy());
        }

        for (List<Action> actions : this.actions)
        {
            if (actions == null || actions.isEmpty())
            {
                record.actions.add(null);
            }
            else
            {
                List<Action> newActions = new ArrayList<Action>();

                for (Action action : actions)
                {
                    try
                    {
                        NBTTagCompound tag = new NBTTagCompound();

                        action.toNBT(tag);

                        Action newAction = ActionRegistry.fromType(ActionRegistry.getType(action));

                        newAction.fromNBT(tag);
                        newActions.add(newAction);
                    }
                    catch (Exception e)
                    {
                        System.out.println("Failed to clone an action!");
                        e.printStackTrace();
                    }
                }

                record.actions.add(newActions);
            }
        }

        return record;
    }

    public void save(File file) throws IOException
    {
        this.save(file, true);
    }

    /**
     * Save a recording to given file.
     *
     * This method basically writes the signature of the current version,
     * and then saves all available frames and actions.
     */
    public void save(File file, boolean savePast) throws IOException
    {
        if (savePast && file.isFile())
        {
            this.savePastCopies(file);
        }

        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList frames = new NBTTagList();

        /* Version of the recording */
        compound.setShort("Version", SIGNATURE);
        compound.setInteger("PreDelay", this.preDelay);
        compound.setInteger("PostDelay", this.postDelay);
        compound.setTag("Actions", this.createActionMap());

        if (this.playerData != null)
        {
            compound.setTag("PlayerData", this.playerData);
        }

        int c = this.frames.size();
        int d = this.actions.size() - this.frames.size();

        if (d < 0) d = 0;

        for (int i = 0; i < c; i++)
        {
            NBTTagCompound frameTag = new NBTTagCompound();

            Frame frame = this.frames.get(i);
            List<Action> actions = null;

            if (d + i <= this.actions.size() - 1)
            {
                actions = this.actions.get(d + i);
            }

            frame.toNBT(frameTag);

            if (actions != null)
            {
                NBTTagList actionsTag = new NBTTagList();

                for (Action action : actions)
                {
                    NBTTagCompound actionTag = new NBTTagCompound();

                    action.toNBT(actionTag);
                    actionTag.setByte("Type", ActionRegistry.CLASS_TO_ID.get(action.getClass()));
                    actionsTag.appendTag(actionTag);
                }

                frameTag.setTag("Action", actionsTag);
            }

            frames.appendTag(frameTag);
        }

        compound.setTag("Frames", frames);

        CompressedStreamTools.writeCompressed(compound, new FileOutputStream(file));
    }

    /**
     * This method removes the last file, and renames past versions of a recording files. 
     * This should save countless hours of work in case somebody accidentally overwrote 
     * a player recording.
     */
    private void savePastCopies(File file)
    {
        final int copies = 5;

        int counter = copies;
        String name = FilenameUtils.removeExtension(file.getName());

        while (counter >= 0 && file.exists())
        {
            File current = this.getPastFile(file, name, counter);

            if (current.exists()) 
            {
                if (counter == copies)
                {
                    current.delete();
                }
                else
                {
                    File previous = this.getPastFile(file, name, counter + 1);

                    current.renameTo(previous);
                }
            }

            counter--;
        }
    }

    /**
     * Get a path to the past copy of the file
     */
    private File getPastFile(File file, String name, int iteration)
    {
        return new File(file.getParentFile(), name + (iteration == 0 ? ".dat" : ".dat~" + iteration));
    }

    /**
     * Creates an action map between action name and an action type byte values
     * for compatibility
     */
    private NBTTagCompound createActionMap()
    {
        NBTTagCompound tag = new NBTTagCompound();

        for (Map.Entry<String, Byte> entry : ActionRegistry.NAME_TO_ID.entrySet())
        {
            tag.setString(entry.getValue().toString(), entry.getKey());
        }

        return tag;
    }

    /**
     * Read a recording from given file.
     *
     * This method basically checks if the given file has appropriate short
     * signature, and reads all frames and actions from the file.
     */
    public void load(File file) throws IOException
    {
        this.load(CompressedStreamTools.readCompressed(new FileInputStream(file)));
    }

    public void load(NBTTagCompound compound)
    {
        NBTTagCompound map = null;

        this.version = compound.getShort("Version");
        this.preDelay = compound.getInteger("PreDelay");
        this.postDelay = compound.getInteger("PostDelay");

        if (compound.hasKey("Actions", 10))
        {
            map = compound.getCompoundTag("Actions");
        }

        if (compound.hasKey("PlayerData", 10))
        {
            this.playerData = compound.getCompoundTag("PlayerData");
        }

        NBTTagList frames = (NBTTagList) compound.getTag("Frames");

        for (int i = 0, c = frames.tagCount(); i < c; i++)
        {
            NBTTagCompound frameTag = frames.getCompoundTagAt(i);
            NBTBase actionTag = frameTag.getTag("Action");
            Frame frame = new Frame();

            frame.fromNBT(frameTag);

            if (actionTag != null)
            {
                try
                {
                    List<Action> actions = new ArrayList<Action>();

                    if (actionTag instanceof NBTTagCompound)
                    {
                        Action action = this.actionFromNBT((NBTTagCompound) actionTag, map);

                        if (action != null)
                        {
                            actions.add(action);
                        }
                    }
                    else if (actionTag instanceof NBTTagList)
                    {
                        NBTTagList list = (NBTTagList) actionTag;

                        for (int ii = 0, cc = list.tagCount(); ii < cc; ii++)
                        {
                            Action action = this.actionFromNBT(list.getCompoundTagAt(ii), map);

                            if (action != null)
                            {
                                actions.add(action);
                            }
                        }
                    }

                    this.actions.add(actions);
                }
                catch (Exception e)
                {
                    System.out.println("Failed to load an action at frame " + i);
                    e.printStackTrace();
                }
            }
            else
            {
                this.actions.add(null);
            }

            this.frames.add(frame);
        }
    }

    private Action actionFromNBT(NBTTagCompound tag, NBTTagCompound map) throws Exception
    {
        byte type = tag.getByte("Type");
        Action action = null;

        if (map == null)
        {
            action = ActionRegistry.fromType(type);
        }
        else
        {
            String name = map.getString(String.valueOf(type));

            if (ActionRegistry.NAME_TO_CLASS.containsKey(name))
            {
                action = ActionRegistry.fromName(name);
            }
        }

        if (action != null)
        {
            action.fromNBT(tag);
        }

        return action;
    }

    public void reverse()
    {
        Collections.reverse(this.frames);
        Collections.reverse(this.actions);
    }

    public void fillMissingActions()
    {
        while (this.actions.size() < this.frames.size())
        {
            this.actions.add(null);
        }
    }

    public static class FoundAction
    {
        public int tick;
        public MorphAction action;

        public void set(int tick, MorphAction action)
        {
            this.tick = tick;
            this.action = action;
        }
    }

    public static enum MorphType
    {
        REGULAR, PAUSE, FORCE
    }
}