/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.backup;

import de.dimm.vsm.CS_Constants;
import de.dimm.vsm.Exceptions.PathResolveException;
import de.dimm.vsm.GeneralPreferences;
import de.dimm.vsm.log.Log;
import de.dimm.vsm.Main;
import de.dimm.vsm.Utilities.StatCounter;
import de.dimm.vsm.fsengine.FSEIndexer;
import de.dimm.vsm.fsengine.HashCache;
import de.dimm.vsm.fsengine.StoragePoolHandler;
import de.dimm.vsm.jobs.JobInterface.JOBSTATE;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.ArchiveJob;
import de.dimm.vsm.records.Excludes;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Administrator
 */
public abstract class GenericContext
{
    protected AgentApiEntry apiEntry;
    protected StoragePoolHandler poolhandler;
    protected StatCounter stat;
    protected int hash_block_size;
    private boolean result;
    private boolean abort;
    protected String basePath;
    protected boolean abortOnError;
    protected int errCounter;
    protected long nextCheckByteSize = 0;  // StoargeNodes WERDEN BEI ERRICHEN DIESER GRÖßE GEPR�FT

    protected JOBSTATE state;
    protected String status;
    protected boolean open = false;

    protected HandleWriteRunner writeRunner;

    static int openCounter;

    protected ExecutorService localListDirexecutor = Executors.newFixedThreadPool(1);
    protected ExecutorService remoteListDirexecutor = Executors.newFixedThreadPool(1);


    protected ArchiveJob archiveJob;
    protected FSEIndexer indexer;
    protected HashCache hashCache;


   
     /**
     * Keeps together all data needed for identifing context of ClientCalls
     *
     */

    public GenericContext( AgentApiEntry apiEntry, StoragePoolHandler poolhandler )
    {
        this.apiEntry = apiEntry;
        this.poolhandler = poolhandler;
        this.hash_block_size = Main.get_int_prop(GeneralPreferences.FILE_HASH_BLOCKSIZE, CS_Constants.FILE_HASH_BLOCKSIZE);
        indexer = Main.get_control().getStorageNubHandler().getIndexer(poolhandler.getPool());
        stat = new StatCounter("");
        result = false;
        basePath = getClientInfoRootPath( apiEntry.getAddr() ,apiEntry.getPort());
        
        errCounter = 0;
        result = true;
        abort = false;
        state = JOBSTATE.RUNNING;

         writeRunner = new HandleWriteRunner();

         openCounter++;
         open = true;
         if (indexer != null)
         {
             indexer.open();
         }

         hashCache = Main.get_control().getStorageNubHandler().getHashCache(poolhandler.getPool());


         Log.debug("Opened " + openCounter + " Contexts");
    }

    public void setHashCache( HashCache hashCache )
    {
        this.hashCache = hashCache;
    }

    
    public static String getClientInfoRootPath(InetAddress addr, int port )
    {
        return "/" + addr.getHostAddress() + "/" + port;
    }

    public String getBasePath()
    {
        return basePath;
    }

    public abstract String getRemoteElemAbsPath( RemoteFSElem remoteFSElem ) throws PathResolveException;


    boolean isAbortOnError()
    {
        return abortOnError;
    }

    public void setAbortOnError( boolean abortOnError )
    {
        this.abortOnError = abortOnError;
    }

    public void setAbort( boolean abort )
    {
        this.abort = abort;
    }

    public boolean isAbort()
    {
        return abort;
    }

    public boolean getResult()
    {
        return result;
    }

    public void setResult( boolean b )
    {
        result = b;
    }

    public StatCounter getStat()
    {
        return stat;
    }
    

    public void close()
    {
        if (!open)
            return;

        open = false;
        
        try
        {
            writeRunner.close();
            apiEntry.close();
            if (poolhandler.is_transaction_active())
            {
                poolhandler.commit_transaction();
            }
        }
        catch (Exception exception)
        {
            Log.debug("Fehler beim Schließen des Kontexts", exception);
        }
        if (indexer != null)
        {
            indexer.close();
        }
        openCounter--;
        Log.debug("Closed Context");

    }


    void detach( Object o ) throws SQLException
    {
        poolhandler.em_detach(o);
    }

    void addError( RemoteFSElem remoteFSElem )
    {
        errCounter++;
    }

    public void setJobState( JOBSTATE jOBSTATE )
    {
        state = jOBSTATE;
    }

    public JOBSTATE getJobState()
    {
        return state;
    }

    public void setStatus( String status )
    {
        this.status = status;        
    }

    public String getStatus()
    {
        return status;
    }

    public HandleWriteRunner getWriteRunner()
    {
        return writeRunner;
    }

    void checkStorageNodes()
    {
        // DO WE HAVE TO CHECK SPACE AGAIN?
        if (stat.getByteTransfered() < nextCheckByteSize)
            return;

        // HOW MUCH LEFT ON ACTUAL NODE?
        long space = poolhandler.checkStorageNodeSpace();

        // CALC NEXT LEN
        // THIS WAY WE CHECK MORE OFTEN WHEN WE GET CLOSER TO THE END OF A STORAGENODE
        long diff = space/2;

        // DONT CHECK MORE OFTEN THAN NECESSARY
        
        if (diff < poolhandler.getNodeMinFreeSpace() / 5)
            diff = poolhandler.getNodeMinFreeSpace() / 5;

        nextCheckByteSize = stat.getByteTransfered() + diff;
    }

    public int getHashBlockSize()
    {
        return hash_block_size;
    }

    public StatCounter getStatCounter()
    {
        return stat;
    }

    public ArchiveJob getArchiveJob()
    {
        return archiveJob;
    }

    public void setArchiveJob( ArchiveJob archiveJob )
    {
        this.archiveJob = archiveJob;
    }

    public FSEIndexer getIndexer()
    {
        return indexer;
    }

    public StoragePoolHandler getPoolhandler()
    {
        return poolhandler;
    }

    public List<Excludes> getExcludes()
    {
        return null;
    }
    
    

}