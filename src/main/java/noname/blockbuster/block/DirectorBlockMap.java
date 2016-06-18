package noname.blockbuster.block;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemNameTag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import noname.blockbuster.recording.Mocap;
import noname.blockbuster.tileentity.AbstractDirectorTileEntity;
import noname.blockbuster.tileentity.DirectorMapTileEntity;

/**
 * Descendant of abstract director block
 *
 * This block is the one that responsible for adventure maps scenes.
 */
public class DirectorBlockMap extends AbstractDirectorBlock
{
    public DirectorBlockMap()
    {
        super();
        this.setRegistryName("directorBlockMap");
        this.setUnlocalizedName("directorBlockMap");
    }

    /**
     * Register a replay with information supplied from name tag item
     */
    private boolean handleNameTag(ItemStack item, World world, BlockPos pos)
    {
        if (item.getItem() instanceof ItemNameTag && item.hasDisplayName())
        {
            DirectorMapTileEntity director = (DirectorMapTileEntity) world.getTileEntity(pos);

            if (director.add(item.getDisplayName()))
            {
                Mocap.broadcastMessage(I18n.format("blockbuster.director_map.was_registered"));
            }
            else
            {
                Mocap.broadcastMessage(I18n.format("blockbuster.director_map.already_registered"));
            }

            return true;
        }

        return false;
    }

    @Override
    protected boolean handleItem(ItemStack item, World world, BlockPos pos, EntityPlayer player)
    {
        return this.handleNameTag(item, world, pos) || this.handlePlaybackItem(item, pos, player);
    }

    @Override
    protected void outputCast(EntityPlayer playerIn, World worldIn, BlockPos pos)
    {
        AbstractDirectorTileEntity tile = (AbstractDirectorTileEntity) worldIn.getTileEntity(pos);
        String output = I18n.format("blockbuster.director.cast");
        List<String> cast = tile.getCast();

        for (String replay : cast)
        {
            output += "* " + replay + "\n";
        }

        if (cast.isEmpty())
        {
            output = I18n.format("blockbuster.director.no_cast");
        }

        playerIn.addChatComponentMessage(new TextComponentString(output.trim()));
    }

    /**
     * Create tile entity
     */
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new DirectorMapTileEntity();
    }
}