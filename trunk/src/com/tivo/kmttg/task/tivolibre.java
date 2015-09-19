package com.tivo.kmttg.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

import net.straylightlabs.tivolibre.TivoDecoder;

public class tivolibre extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private backgroundProcess process;
   public jobData job;
   
   public tivolibre(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }

   public backgroundProcess getProcess() {
      return process;
   }

   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't decrypt if mpegFile already exists
      if ( file.isFile(job.mpegFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING DECRYPT, FILE ALREADY EXISTS: " + job.mpegFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile);
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   public Boolean start() {
      debug.print("");
      // Obtain some info on input video
      Hashtable<String,String> info = null;
      if (file.isFile(config.mediainfo))
         info = mediainfo.getVideoInfo(job.tivoFile);
      else if (file.isFile(config.ffmpeg))
         info = ffmpeg.getVideoInfo(job.tivoFile);

      // For transport stream container input files change output file suffix from .mpg to .ts
      Boolean isFileChanged = false;
      if (info != null && info.get("container").equals("mpegts")) {
         if (job.mpegFile.endsWith(".mpg")) {
            job.mpegFile = string.replaceSuffix(job.mpegFile, ".ts");
            isFileChanged = true;
         }
      }      
      if (isFileChanged) {            
         // If in GUI mode, update job monitor output field
         if (config.GUIMODE) {
            String output = string.basename(job.mpegFile);
            if (config.jobMonitorFullPaths == 1)
               output = job.mpegFile;
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, output);
         }
         
         // Subsequent jobs need to have mpegFile && mpegFile_cut updated
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile", job.mpegFile);
         String mpegFile_cut = job.mpegFile_cut.replaceFirst("_cut.mpg", "_cut.ts");
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile_cut", mpegFile_cut);
         
         // Rename already created metadata file if relevant
         String meta = string.replaceSuffix(job.mpegFile, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
         meta = string.replaceSuffix(job.mpegFile_cut, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile_cut + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
      }

      // Run download method in a separate thread
      log.print(">> DECRYPTING USING TIVOLIBRE " + job.tivoFile + " ...");
      Runnable r = new Runnable() {
         public void run () {
            try {
               BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(job.tivoFile));
               BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(job.mpegFile));
               TivoDecoder decoder = new TivoDecoder(inputStream, outputStream, config.MAK);
               success = decoder.decode();
               inputStream.close();
               outputStream.close();
               decoder = null;
               thread_running = false;
            }
            catch (Exception e) {
               success = false;
               thread_running = false;
               Thread.currentThread().interrupt();
            }
         }
      };
      thread_running = true;
      thread = new Thread(r);
      thread.start();

      return true;
   }

   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            if ( file.isFile(job.mpegFile) ) {               
               // Update status in job table
               if ( job.tivoFileSize == null ) {
                  job.tivoFileSize = file.size(job.tivoFile);
               }
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile)*100/job.tivoFileSize));
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("tivolibre: %d%% %s", pct, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);
               } else {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + s);
               }
            }
         }
         return true;
      } else {
         // Job finished
         if (config.GUIMODE) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               config.gui.setTitle(config.kmttg);
               config.gui.progressBar_setValue(0);
            }
         }
         
         jobMonitor.removeFromJobList(job);
         
         if ( success ) {
            log.warn("tivolibre job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile);
            
            // Remove .TiVo file if option enabled
            if (config.RemoveTivoFile == 1) {
               if ( file.delete(job.tivoFile) ) {
                  log.print("(Deleted file: " + job.tivoFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.tivoFile);
               }
            }
            
            // TivoWebPlus call to delete show on TiVo if configured
            if (job.twpdelete && ! config.rpcEnabled(job.tivoName)) {
               file.TivoWebPlusDelete(job.url);
            }
            
            // rpc style delete show on TiVo if configured
            if (job.rpcdelete && config.rpcEnabled(job.tivoName)) {
               String recordingId = rnpl.findRecordingId(job.tivoName, job.entry);
               if ( ! file.rpcDelete(job.tivoName, recordingId) )
                  log.error("Failed to delete show on TiVo");
            }
         } else {
            log.error("tivolibre decrypt failed for file: " + job.tivoFile);
         }

         return false;         
      }
   }

   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' file: " + job.tivoFile);
      thread_running = false;
   }

}
