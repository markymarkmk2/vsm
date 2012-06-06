/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.fsengine;

import de.dimm.vsm.log.Log;
import de.dimm.vsm.Main;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/**
 *
 * @author Administrator
 */
public class VSMFSInputStream extends InputStream
{

    StoragePoolHandler sp;
    FileSystemElemNode node;
    

    StoragePoolWrapper wrapper;
    long pos;
    long fileNo;

    public VSMFSInputStream( StoragePoolHandler sp, FileSystemElemNode node )
    {
        this.sp = sp;
        this.node = node;


        pos = 0;
        wrapper = Main.get_control().getPoolHandlerServlet().getContextManager().createPoolWrapper(sp, "", 0, "");
        fileNo = -2;

    }
    public VSMFSInputStream( StoragePoolHandler sp, long idx  )
    {
        this.sp = sp;
        try
        {
            this.node = sp.resolve_fse_node_from_db(idx);
        }
        catch (SQLException sQLException)
        {
            Log.err("InputStream kann nicht aufgelöst werden", sQLException);
        }

        pos = 0;
        wrapper = Main.get_control().getPoolHandlerServlet().getContextManager().createPoolWrapper(sp, "", 0, "");
        fileNo = -2;

    }
    void ensureOpen() throws IOException
    {
        try
        {
            if (fileNo == -2)
            {
                fileNo = sp.open_fh( node, /*create*/ false);
            }
        }
        catch (Exception exception)
        {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    @Override
    public int read() throws IOException
    {
        ensureOpen();

        byte[] b = new byte[1];
        int len = sp.read( fileNo, b, b.length, pos);
        if (len > 0)
            pos += len;
        return b[0];
    }

    @Override
    public int read( byte[] b ) throws IOException
    {
        ensureOpen();

        int len = sp.read(fileNo, b, b.length, pos);
        if (len > 0)
            pos += len;

        return len;
    }

    @Override
    public int read( byte[] b, int off, int len ) throws IOException
    {
        ensureOpen();

        if (off == 0 && len == b.length)
            return read(b);

        byte[] d = new byte[len];

        len = read(b);
        if (len > 0)
        {
            System.arraycopy(d, 0, b, off, len);
            pos += len;
        }
        return len;
    }

    @Override
    public long skip( long n ) throws IOException
    {
        pos += n;
        return pos;
    }

    @Override
    public int available() throws IOException
    {
        long l = sp.getLength( fileNo );
        if (l - pos > Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return (int)(l - pos);
    }

    @Override
    public void close() throws IOException
    {
        if (fileNo >= 0)
        {
            Main.get_control().getPoolHandlerServlet().close_fh(wrapper, fileNo);
        }
        Main.get_control().getPoolHandlerServlet().getContextManager().removePoolWrapper(wrapper);
    }

}