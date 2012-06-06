/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.fsengine;

import de.dimm.vsm.net.interfaces.BootstrapHandle;
import de.dimm.vsm.net.interfaces.FileHandle;
import de.dimm.vsm.Exceptions.PathResolveException;
import de.dimm.vsm.Utilities.Hex;
import de.dimm.vsm.records.AbstractStorageNode;
import de.dimm.vsm.records.DedupHashBlock;
import de.dimm.vsm.records.FileSystemElemNode;
import de.dimm.vsm.records.XANode;
import java.io.File;
import java.io.UnsupportedEncodingException;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 *
 * @author mw
 *
 *
 * From <Path/content/> on we have the database-IDS for the directories / files as Folder / Filenames
 */
public class StorageNodeHandler
{
    public static final int DEDUPBLOCK_IDX_CHARACTERS_PER_DIRLEVEL = 3;

    public static final String PATH_FSNODES_PREFIX = "/fs";
    public static final String PATH_DEDUPNODES_PREFIX = "/dd";

    public static final String BOOTSRAP_PATH = "bootstrap";
    public static final String XA_PATH = "xa";

    AbstractStorageNode storageNode;

    StoragePoolHandler storage_pool_handler;

    public static final String NODECACHE = "NodeCache";
    

    public StorageNodeHandler( AbstractStorageNode storageNode, StoragePoolHandler fsh_handler )
    {
        this.storageNode = storageNode;
        this.storage_pool_handler = fsh_handler;
    }

    public static boolean initNode(AbstractStorageNode storageNode)
    {
        if (storageNode.isVirgin())
        {
            if (storageNode.isFS() && storageNode.getMountPoint() != null)
            {
                File f = new File( storageNode.getMountPoint() );
                f.mkdirs();
                if (!f.exists())
                    return false;

                f = new File( storageNode.getMountPoint() + PATH_FSNODES_PREFIX  );
                f.mkdir();
                f = new File( storageNode.getMountPoint() + PATH_DEDUPNODES_PREFIX );
                f.mkdir();

                storageNode.setNodeMode( AbstractStorageNode.NM_ONLINE);
                return true;
            }
        }
        return false;
    }

    public static Cache getCache()
    {
        CacheManager.create();
        if (!CacheManager.getInstance().cacheExists(NODECACHE))
        {
            Cache memoryOnlyCache = new Cache(NODECACHE, 50000, false, false, 50, 50);
            CacheManager.getInstance().addCache(memoryOnlyCache);
            memoryOnlyCache.setStatisticsEnabled(true);
        }
        return CacheManager.getInstance().getCache(NODECACHE);
    }

    public static String getFullParentPath( FileSystemElemNode file_node ) throws PathResolveException
    {
        // TYPE IS ALLOWED TO CHANGE OVER TIME FROM FILE TO DIR, WE NEED DIFFERENT FS INSTANTIATIONS
        Cache c = getCache();
        Element el = c.get(Long.toString(file_node.getIdx()) + "NodePath");
        if (el != null)
        {
            return (String)el.getValue();
        }

        StringBuilder sb = new StringBuilder();

        int max_depth = 1024;

        while (file_node.getParent() != null)
        {
            file_node = file_node.getParent();
            sb.insert(0, Hex.fromLong(file_node.getIdx()) );
            sb.insert(0, "/");
            if (max_depth-- <= 0)
                throw new PathResolveException("Path_is_too_deep");
        }
        String path = sb.toString();
        c.put( new Element( Long.toString(file_node.getIdx()) + "NodePath", path) );

        return path;
    }

    private static void build_block_path( long idx, StringBuilder sb ) throws PathResolveException
    {
        String val = Hex.fromLong(idx, true);

        sb.append( "/");


        // PATH IS HASHVAL SPLITTED INTO 4-CHAR LENGTH SUBDIRS
        int max_len = val.length();
        int pos = 0;
        while (pos < max_len)
        {
            int end = pos + 2;
            if (end < max_len)
            {
                sb.append(val.substring(pos, end) );
                sb.append( "/");
            }

            pos += 2;
        }
    }

    
    public static void build_node_path( FileSystemElemNode file_node, StringBuilder sb ) throws PathResolveException
    {

        if (!file_node.isDirectory())
        {
            sb.insert(0,'.');
            sb.append(file_node.getTyp());
        }
        sb.insert(0, Hex.fromLong(file_node.getIdx()));
        sb.insert(0, "/");

        sb.insert(0, getFullParentPath( file_node ) );

        sb.insert(0, PATH_FSNODES_PREFIX);
    }

    // CREATE SHORTER PATHS FOR DD FS
    private static boolean reverseHashPath = false;

    public static void build_node_path( DedupHashBlock dhb, StringBuilder sb ) throws PathResolveException, UnsupportedEncodingException
    {
        String val = Hex.fromLong(dhb.getIdx(), true);

        if (!reverseHashPath)
        {
            sb.append(PATH_DEDUPNODES_PREFIX);
            build_block_path( dhb.getIdx(), sb );
            
            sb.append( dhb.getHashvalue());
            return;
        }

        // FILENAME IS INDEX
        sb.insert(0, dhb.getHashvalue());
        sb.insert(0, "/");

        // PATH IS HASHVAL SPLITTED INTO 4-CHAR LENGTH SUBDIRS
        int max_len = val.length();
        int pos = 0;
        while (pos < max_len)
        {
            int end = pos + DEDUPBLOCK_IDX_CHARACTERS_PER_DIRLEVEL;
            if (end < max_len)
                sb.insert(0, val.substring(pos, end) );
            else
                sb.insert(0, val.substring(pos) );

            pos += DEDUPBLOCK_IDX_CHARACTERS_PER_DIRLEVEL;
            sb.insert(0, "/");
        }
        sb.insert(0, PATH_DEDUPNODES_PREFIX);
    }
    
    public static void build_xa_node_path( FileSystemElemNode file_node, StringBuilder sb ) throws PathResolveException, UnsupportedEncodingException
    {
        sb.insert(0, Hex.fromLong(file_node.getIdx()));
        sb.insert(0, "/" + XA_PATH + "/");

        sb.insert(0, getFullParentPath( file_node ) );

//        int max_depth = 1024;
//
//        while (file_node.getParent() != null)
//        {
//            file_node = file_node.getParent();
//            sb.insert(0, Hex.fromLong(file_node.getIdx()) );
//            sb.insert(0, "/");
//            if (max_depth-- <= 0)
//                throw new PathResolveException("Path_is_too_deep");
//        }
        sb.insert(0, PATH_FSNODES_PREFIX);

    }

    static void build_bootstrap_path( FileSystemElemNode file_node, StringBuilder sb ) throws PathResolveException
    {
        sb.insert(0, ".xml" );
        sb.insert(0, Hex.fromLong(file_node.getIdx()));
        sb.insert(0, "/" + BOOTSRAP_PATH + "/fs_");

        sb.insert(0, getFullParentPath( file_node ) );

//        int max_depth = 1024;
//
//        while (file_node.getParent() != null)
//        {
//            file_node = file_node.getParent();
//            sb.insert(0, Hex.fromLong(file_node.getIdx()) );
//            sb.insert(0, "/");
//            if (max_depth-- <= 0)
//                throw new PathResolveException("Path_is_too_deep");
//        }
        sb.insert(0, PATH_FSNODES_PREFIX);
    }

    static void build_bootstrap_path( XANode node, StringBuilder sb ) throws PathResolveException
    {
        sb.insert(0, ".xml" );
        sb.insert(0, Hex.fromLong(node.getIdx()));
        sb.insert(0, "/" + BOOTSRAP_PATH + "/xa_");

        sb.insert(0, getFullParentPath( node.getFileNode() ) );

//        int max_depth = 1024;
//
//        FileSystemElemNode file_node = node.getFileNode();
//        while (file_node.getParent() != null)
//        {
//            file_node = file_node.getParent();
//            sb.insert(0, Hex.fromLong(file_node.getIdx()) );
//            sb.insert(0, "/");
//            if (max_depth-- <= 0)
//                throw new PathResolveException("Path_is_too_deep");
//        }
        sb.insert(0, PATH_FSNODES_PREFIX);
    }

    static void build_bootstrap_path( DedupHashBlock dhb, StringBuilder sb ) throws PathResolveException, UnsupportedEncodingException
    {
        String val = Hex.fromLong(dhb.getIdx(), true);

        if (!reverseHashPath)
        {
            sb.append(PATH_DEDUPNODES_PREFIX);
            build_block_path( dhb.getIdx(), sb );

            sb.append( BOOTSRAP_PATH + "/dd_");
            sb.append( dhb.getHashvalue());
            sb.append(".xml" );
            return;
        }


        // FILENAME IS INDEX
        sb.insert(0, ".xml" );
        sb.insert(0, dhb.getHashvalue());
        sb.insert(0, "/" + BOOTSRAP_PATH + "/dd_");

        // PATH IS HASHVAL SPLITTED INTO 4-CHAR LENGTH SUBDIRS
        int max_len = val.length();
        int pos = 0;
        while (pos < max_len)
        {
            int end = pos + DEDUPBLOCK_IDX_CHARACTERS_PER_DIRLEVEL;
            if (end < max_len)
                sb.insert(0, val.substring(pos, end) );
            else
                sb.insert(0, val.substring(pos) );

            pos += DEDUPBLOCK_IDX_CHARACTERS_PER_DIRLEVEL;
            sb.insert(0, "/");
        }
        sb.insert(0, PATH_DEDUPNODES_PREFIX);
    }

    
    static <T> void build_bootstrap_path( StringBuilder sb, T object ) throws PathResolveException
    {
        sb.insert(0, ".xml" );
        sb.insert(0, object.getClass().getSimpleName());
        sb.insert(0, "/" + BOOTSRAP_PATH + "/");
        sb.insert(0, PATH_FSNODES_PREFIX);
    }



/*
    public boolean remove_fse_node( FileSystemElemNodeHandler f )
    {
        return storage_pool_handler.remove_fse_node( f.getNode() );
        
    }*/

    public long getTotalBlocks()
    {
        if (storageNode.isFS() && storageNode.getMountPoint() != null)
        {
            File f = new File( storageNode.getMountPoint() );
            return f.getTotalSpace() / getBlockSize();
        }
        return 1024;
    }

    public long getUsedBlocks()
    {
        if (storageNode.isFS() && storageNode.getMountPoint() != null)
        {
            File f = new File( storageNode.getMountPoint() );

            return (f.getTotalSpace()- f.getUsableSpace() )/ getBlockSize();
        }
        return 0;
    }
    public int getBlockSize()
    {
        return 1024;
    }
    public static long getUsedSpace(AbstractStorageNode n)
    {
        if (n.isFS() && n.getMountPoint() != null)
        {
            File f = new File( n.getMountPoint() );
            return f.getTotalSpace() - f.getUsableSpace();
        }
        return 0;
    }
    // TAKES LONG!
    public static long getRealUsedSpace(AbstractStorageNode n)
    {
        if (n.isFS() && n.getMountPoint() != null)
        {
            File f = new File( n.getMountPoint() );
            long len = calcRecursiveLen( f );
            return len;
        }
        return 0;
    }
    public static long getFreeSpace(AbstractStorageNode n)
    {
        if (n.isFS() && n.getMountPoint() != null)
        {
            File f = new File( n.getMountPoint() );
            return f.getUsableSpace();
        }
        return 0;
    }
    public static long calcRecursiveLen( File f )
    {
        long len = 0;
        if (f.isDirectory())
        {
            File[] children = f.listFiles();
            for (int i = 0; i < children.length; i++)
            {
                File file = children[i];
                len += calcRecursiveLen(file);
            }
            return len;
        }
        else
        {
            return f.length();
        }
    }
    public static boolean isRoot(AbstractStorageNode n)
    {
        if (n.isFS())
        {
            File[] roots = File.listRoots();
            for (int i = 0; i < roots.length; i++)
            {
                File file = roots[i];
                if (file.getAbsolutePath().equals( new File(n.getMountPoint()).getAbsolutePath() ))
                    return true;
            }
        }
        return false;
    }

    public AbstractStorageNode get_node()
    {
        return storageNode;
    }

    public FileHandle create_file_handle(FileSystemElemNode node, boolean create) throws PathResolveException
    {
        if (storageNode.isFS())
        {
            FileHandle ret = FS_FileHandle.create_fs_handle(this, node, create );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public FileHandle create_xa_node_handle(FileSystemElemNode node, boolean create) throws PathResolveException, UnsupportedEncodingException
    {
        if (storageNode.isFS())
        {
            FileHandle ret = FS_FileHandle.create_xa_handle(this, node, create );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public FileHandle create_file_handle(DedupHashBlock block, boolean create) throws PathResolveException, UnsupportedEncodingException
    {
        if (storageNode.isFS())
        {
            FileHandle ret = FS_FileHandle.create_dedup_handle(this, block, create );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public BootstrapHandle create_bootstrap_handle(FileSystemElemNode node) throws PathResolveException
    {
        if (storageNode.isFS())
        {
            BootstrapHandle ret = new FS_BootstrapHandle(this, node );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public BootstrapHandle create_bootstrap_handle(DedupHashBlock block) throws PathResolveException, UnsupportedEncodingException
    {
        if (storageNode.isFS())
        {
            BootstrapHandle ret = new FS_BootstrapHandle(this, block );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }
    public BootstrapHandle create_bootstrap_handle(AbstractStorageNode node) throws PathResolveException, UnsupportedEncodingException
    {
        if (storageNode.isFS())
        {
            BootstrapHandle ret = new FS_BootstrapHandle(this, node );
            return ret;
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public boolean isReadOnly()
    {
       return storage_pool_handler.isReadOnly();
    }
/*
    void update_filesize( FileSystemElemNode node, long size ) throws PoolReadOnlyException, SQLException, DBConnException
    {
        storage_pool_handler.update_filesize( node, size );
    }*/


}