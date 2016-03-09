package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;
import javafx.concurrent.Task;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.tivoTab;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class AutoSkip {
   private static String ini = config.programDir + File.separator + "AutoSkip.ini";
   private static Remote r = null;
   private static Timer timer = null;
   private static long offset = -1;
   private static long end1 = -1;
   private static String offerId = null;
   private static String contentId = null;
   private static String title = "";
   private static int monitor_count = 1;
   private static int monitor_interval = 6;
   private static String tivoName = null;
   static Boolean monitor = false;
   static Stack<Hashtable<String,Long>> skipData = null;
   static Stack<Hashtable<String,Long>> skipData_orig = null;
   static String recordingId = null;
   
   public static synchronized Boolean skipEnabled() {
      debug.print("");
      // At least 1 TiVo needs to be RPC enabled
      return config.rpcEnabled();
   }
   
   public static synchronized Boolean isMonitoring() {
      debug.print("");
      return monitor;
   }
   
   public static synchronized String offerId() {
      debug.print("");
      return offerId;
   }
   
   public static synchronized void setMonitor(String tivoName, String offerId, String contentId, String title) {
      debug.print("tivoName=" + tivoName + " offerId=" + offerId + " contentId=" + contentId + " title=" + title);
      if (AutoSkip.tivoName != null && ! AutoSkip.tivoName.equals(tivoName)) {
         disable();
      }
      AutoSkip.tivoName = tivoName;
      AutoSkip.offerId = offerId;
      AutoSkip.contentId = contentId;
      AutoSkip.title = title;
      
      if (r == null) {
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return;
         }
      }
      
      startTimer();      
      monitor = true;
   }
   
   // Run this in background mode so as not to block GUI
   public static synchronized void skipPlay(final String tivoName, final Hashtable<String,String> nplData) {
      debug.print("tivoName=" + tivoName + " nplData=" + nplData);
      AutoSkip.tivoName = tivoName;
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            if (! nplData.containsKey("contentId")) {
               error("Missing contentId");
               disable();
               return null;
            }
            if (! nplData.containsKey("offerId")) {
               error("Missing offerId");
               disable();
               return null;
            }
            if (! nplData.containsKey("recordingId")) {
               error("Missing recordingId");
               disable();
               return null;
            }
            if (nplData.containsKey("title"))
               AutoSkip.title = nplData.get("title");
            AutoSkip.contentId = nplData.get("contentId");
            AutoSkip.offerId = nplData.get("offerId");
            AutoSkip.recordingId = nplData.get("recordingId");
            r = new Remote(tivoName);
            if (r.success) {
               if (readEntry(AutoSkip.contentId)) {
                  print("Obtained skip data from file: " + ini);
               } else {
                  error("No skip data available for " + title);
                  disable();
                  return null;
               }
               if (skipData != null) {
                  // Start playback
                  print("play: " + nplData.get("title"));
                  JSONObject json = new JSONObject();
                  try {
                     json.put("id", recordingId);
                     r.Command("Playback", json);
                  } catch (Exception e) {
                     error("skipPlay - " + e.getMessage());
                     disable();
                     return null;
                  }
                  enableMonitor(tivoName, skipData, end1);
               } else {
                  disable();
               }
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   // end1 = -1 => pause mode, else normal skip mode without pause
   public static synchronized void enableMonitor(String tivoName, Stack<Hashtable<String,Long>> points, Long end1) {
      debug.print("tivoName=" + tivoName + " points=" + points);
      AutoSkip.tivoName = tivoName;
      AutoSkip.end1 = end1;
      if (r == null) {
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return;
         }
      }
      skipData_orig = points;
      skipData = hashCopy(skipData_orig);
      showSkipData();
      if (end1 == -1) {
         print("REMINDER: 1st pause press will be saved as exact start of 1st commercial");
         print("REMINDER: Use 'x' bindkey to jump to close to 1st commercial");
      }
      monitor = true;
      // Start timer to monitor playback position
      startTimer();
   }
   
   // Start timer to monitor playback position
   public static synchronized void startTimer() {
      debug.print("");
      if (timer != null)
         timer.cancel();
      timer = new Timer();
      timer.schedule(
         new TimerTask() {
            @Override
            public void run() {
               skipPlayCheck(tivoName, AutoSkip.contentId);
            }
        }
        ,5000,
        1000
      );
   }
   
   // This procedure called constantly in a timer
   // This monitors playback position and skips commercials
   private static synchronized void skipPlayCheck(String tivoName, String contentId) {
      debug.print("tivoName=" + tivoName + " contentId=" + contentId);
      if (monitor && r != null) {
         Boolean skip = true;
         monitor_count++;
         if (monitor_count > monitor_interval) {
            monitor_count = 1;
            shouldStillMonitor();
         }
         long pos = getPosition();
         debug.print("pos=" + pos + " end1=" + end1);
         if (pos == -1)
            return;
         if (end1 == -1)
            return;
         for (Hashtable<String,Long> h : skipData) {
            if (pos > h.get("start") && pos < h.get("end")) {
               skip = false;
            }
         }
         // If pos < first end point then don't skip
         if (skip) {
            if (pos < skipData.get(0).get("end"))
               skip = false;
         }
         // If pos >= last end point then don't skip
         if (skip) {
            if (pos >= skipData.get(skipData.size()-1).get("end"))
               skip = false;
         }
         if (skip) {
            long jumpto = getClosest(pos);
            if (jumpto != -1) {
               print("IN COMMERCIAL. JUMPING TO: " + toMinSec(jumpto));
               jumpTo(jumpto);
            }
         }
      }
   }
   
   // Jump to given position in playback
   private static synchronized void jumpTo(long position) {
      debug.print("position=" + position);
      JSONObject json = new JSONObject();
      try {
         json.put("offset", position);
      } catch (JSONException e) {
         error("jumpTo - " + e.getMessage());
      }
      r.Command("Jump", json);      
   }
   
   // Jump to end of 1st show segment
   public static synchronized void jumpTo1st() {
      debug.print("");
      if (r != null && skipData != null && monitor) {
         long pos = skipData.get(0).get("end");
         print("Jumping to: " + toMinSec(pos));
         jumpTo(pos);
      }
   }
   
   // RPC query to get current playback position
   // NOTE: Returns -1 for speed != 100 to avoid any skipping during trick play
   private static synchronized long getPosition() {
      debug.print("");
      if (r==null || ! monitor) return -1;
      JSONObject json = new JSONObject();
      JSONObject reply = r.Command("Position", json);
      if (reply == null) {
         // RPC session may be corrupted, start a new connection
         r.disconnect();
         print("Attempting to re-connect.");
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return -1;
         }
         reply = r.Command("Position", json);
      }
      if (reply != null && reply.has("position")) {
         try {
            if (reply.has("speed")) {
               int speed = reply.getInt("speed");
               if (speed != 100)
                  return -1;
            }
            return reply.getLong("position");
         } catch (JSONException e) {
            error("getPosition - " + e.getMessage());
         }
      }
      return -1;
   }
   
   // RPC query to determine if show being monitored is still being played
   // If whatsOn query does not have offerId => show no longer being played on TiVo
   private static synchronized void shouldStillMonitor() {
      debug.print("");
      if (r == null)
         return;
      if (monitor) {
         JSONObject result = r.Command("whatsOnSearch", new JSONObject());
         if (result == null) {
            // RPC session may be corrupted, start a new connection
            if (r != null) r.disconnect();
            print("Attempting to re-connect.");
            r = new Remote(tivoName);
            if (! r.success) {
               disable();
               return;
            }
         } else {
            try {
               if (result.has("whatsOn")) {
                  JSONObject what = result.getJSONArray("whatsOn").getJSONObject(0);
                  if (what.has("offerId") && what.getString("offerId").equals(offerId)) {
                     // Still playing back show so do nothing
                  } else {
                     disable();
                  }
               }
            } catch (JSONException e) {
               error("shouldStillMonitor - " + e.getMessage());
            }
         }
      } else {
         disable();
      }
   }
   
   // Stop monitoring and reset all variables
   public static synchronized void disable() {
      debug.print("");
      print("DISABLED");
      monitor = false;
      if (timer != null)
         timer.cancel();
      if (r != null)
         r.disconnect();
      r = null;
      end1 = -1;
      offset = -1;
      offerId = null;
      recordingId = null;
      contentId = null;
      monitor_count = 1;
      title = "";
   }
   
   // Print current skipData to console
   public static synchronized void showSkipData() {
      debug.print("");
      if (skipData == null) {
         error("showSkipData - no skip data available");
         disable();
         return;
      }
         
      int index = 1;
      for (Hashtable<String,Long> h : skipData) {
         String message = "" + index + ": start=";
         message += toMinSec(h.get("start"));
         message += " end=";
         message += toMinSec(h.get("end"));         
         log.print(message);
         index++;
      }
   }
   
   public static synchronized String toMinSec(long msecs) {
      debug.print("msecs=" + msecs);
      int mins = (int)msecs/1000/60;
      int secs = (int)(msecs/1000 - 60*mins);
      return String.format("%02d:%02d", mins, secs);
   }
   
   // Get the closest non-commercial start point to given pos
   // This will be point to jump to when in a commercial segment
   // Return value of -1 => no change wanted
   private static synchronized long getClosest(long pos) {
      debug.print("pos=" + pos);
      long closest = -1;
      // If current pos is within any start-end range then no skip necessary
      for (Hashtable<String,Long> h : skipData) {
         if (pos >= h.get("start") && pos <= h.get("end"))
            return -1;
      }
      
      if (skipData.size() > 1)
         closest = skipData.get(1).get("start");
      long diff = pos;
      for (Hashtable<String,Long> h : skipData) {
         if (pos < h.get("start")) {
            if (h.get("start") - pos < diff) {
               diff = h.get("start") - pos;
               closest = h.get("start");
            }
         }
      }
      return closest;
   }
   
   // Convert RPC data to skipData hash
   public static synchronized Stack<Hashtable<String,Long>> jsonToShowPoints(JSONObject clipData) {
      debug.print("clipData=" + clipData);
      Stack<Hashtable<String,Long>> points = new Stack<Hashtable<String,Long>>();
      try {
         if (clipData.has("segment")) {
            JSONArray segment = clipData.getJSONArray("segment");
            long offset = 0;
            for (int i=0; i<segment.length(); ++i) {
               JSONObject seg = segment.getJSONObject(i);
               long start = Long.parseLong(seg.getString("startOffset"));
               if (i == 0) {
                  offset = start;
               }
               start -= offset;
               long end = Long.parseLong(seg.getString("endOffset"));
               end -= offset;
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               h.put("start", start);
               h.put("end", end);
               points.push(h);
            }
         } else {
            error("jsonToShowPoints - segment data missing");
         }
      } catch (JSONException e) {
         error("jsonToShowPoints - " + e.getMessage());
      }
      return points;
   }
   
   // Adjust skipData segment 1 end position and
   // all subsequent segment points relative to it
   private static synchronized void adjustPoints(long point) {
      debug.print("point=" + point);
      //print("Adjusting commercial points");
      end1 = point;
      if (skipData.size() > 0) {
         offset = point - skipData.get(0).get("end");
         for (int i=0; i<skipData.size(); ++i) {
            if (i == 0) {
               skipData.get(i).put("end", point);
            } else {
               skipData.get(i).put("start", skipData.get(i).get("start") + offset);
               skipData.get(i).put("end", skipData.get(i).get("end") + offset);
            }
         }
      }
   }
   
   // Save commercial points for current entry to ini file
   public static synchronized void saveEntry(final String contentId, String offerId, long offset,
         String title, final String tivoName, Stack<Hashtable<String,Long>> data) {
      debug.print("contentId=" + contentId + " offerId=" + offerId + " offset=" + offset);
      print("Saving AutoSkip entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(ini, true));
         ofp.write("<entry>" + eol);
         ofp.write("contentId=" + contentId + eol);
         ofp.write("offerId=" + offerId + eol);
         ofp.write("offset=" + offset + eol);
         ofp.write("tivoName=" + tivoName + eol);
         ofp.write("title=" + title + eol);
         for (Hashtable<String,Long> entry : data) {
            ofp.write(entry.get("start") + " " + entry.get("end") + eol);
         }
         ofp.close();
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.getTab(tivoName).getTable().updateSkipStatus(contentId);
               }
            });
         }
      } catch (IOException e) {
         error("saveEntry - " + e.getMessage());
      }
   }
   
   public static synchronized Boolean hasEntry(String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  line = ifp.readLine();
                  if (line.startsWith("contentId")) {
                     String[] l = line.split("=");
                     if (l[1].equals(contentId)) {
                        ifp.close();
                        return true;
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            error("readEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   // Obtain commercial points for given contentId if it exists
   // Returns true if contentId found, false otherwise
   // NOTE: Reading assumes file entries are structured just like they were originally written
   static synchronized Boolean readEntry(String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  line = ifp.readLine();
                  if (line.startsWith("contentId")) {
                     String[] l = line.split("=");
                     if (l[1].equals(contentId)) {
                        skipData_orig = new Stack<Hashtable<String,Long>>();
                        while (( line = ifp.readLine()) != null) {
                           if (line.equals("<entry>"))
                              break;
                           if (line.startsWith("offerId")) {
                              l = line.split("=");
                              offerId = l[1];
                           }
                           if (line.startsWith("offset")) {
                              l = line.split("=");
                              offset = Long.parseLong(l[1]);
                           }
                           if (line.matches("^[0-9]+.*")) {
                              Hashtable<String,Long> h = new Hashtable<String,Long>();
                              l = line.split("\\s+");
                              h.put("start", Long.parseLong(l[0]));
                              h.put("end", Long.parseLong(l[1]));
                              skipData_orig.push(h);
                           }
                        }
                        ifp.close();
                        skipData = hashCopy(skipData_orig);
                        if (skipData.size() > 0) {
                           adjustPoints(skipData_orig.get(0).get("end") + offset);
                           end1 = skipData.get(0).get("end"); // Don't need the pause adjustment
                           //print("Using existing saved AutoSkip entry for: " + title);
                           return true;
                        } else {
                           error("NOTE: Failed to read skip data for: " + title);
                           return false;
                        }
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            error("readEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   // Remove any entries matching given contentId from ini file
   public static synchronized Boolean removeEntry(final String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            Boolean itemRemoved = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            Boolean include = true;
            String tivoName = null;
            String title = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  include = true;
                  String nextline = ifp.readLine();
                  String[] l = nextline.split("=");
                  if (l[1].equals(contentId)) {
                     include = false;
                     itemRemoved = true;
                     ifp.readLine(); // offerId
                     ifp.readLine(); // offset
                     tivoName = ifp.readLine().split("=")[1];
                     title = ifp.readLine().split("=")[1];
                  }
                  if (include) {
                     lines.push(line);
                     lines.push(nextline);
                  }
               } else {
                  if (include)
                     lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemRemoved) {
               print("Removed entry for " + tivoName + ": " + title);
               if (tivoName != null && config.GUIMODE) {
                  // Remove asterisk from associated table
                  final String final_tivoName = tivoName;
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        tivoTab t = config.gui.getTab(final_tivoName);
                        if (t != null) {
                           t.getTable().updateSkipStatus(contentId);
                        }
                     }
                  });
               }
            }
            else
               print("No entry found for: " + contentId);
            return itemRemoved;
         } catch (Exception e) {
            error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Change offset for given contentId
   public static synchronized Boolean changeEntry(String contentId, String offset, String title) {
      debug.print("contentId=" + contentId + " offset=" + offset + " title=" + title);
      if (file.isFile(ini)) {
         try {
            Boolean itemChanged = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("contentId")) {
                  Boolean changed = false;
                  String[] l = line.split("=");
                  if (l[1].equals(contentId)) {
                     itemChanged = true;
                     changed = true;
                  }
                  String offerId = ifp.readLine(); // offerId
                  String nextline = ifp.readLine(); // offset
                  l = nextline.split("=");
                  lines.push(line);
                  lines.push(offerId);
                  if (changed)
                     lines.push(l[0] + "=" + offset);
                  else
                     lines.push(nextline);
               } else {
                  lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemChanged)
               print("'" + title + "' offset updated to: " + offset);
            else
               print("'" + title + "' not updated.");
            return itemChanged;
         } catch (Exception e) {
            error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Return entries for use by SkipDialog table
   public static synchronized JSONArray getEntries() {
      debug.print("");
      JSONArray entries = new JSONArray();
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line=null, contentId="", title="", offset="", offerId="", tivoName="";
            JSONArray cuts = new JSONArray();
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  if (cuts.length() > 0) {
                     JSONObject json = new JSONObject();
                     json.put("contentId", contentId);
                     json.put("offerId", offerId);
                     json.put("offset", offset);
                     json.put("tivoName", tivoName);
                     json.put("title", title);
                     json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
                     json.put("cuts", cuts);
                     entries.put(json);
                  }
                  cuts = new JSONArray();
               }
               if (line.contains("contentId="))
                  contentId = line.replaceFirst("contentId=", "");
               if (line.contains("offerId="))
                  offerId = line.replaceFirst("offerId=", "");
               if (line.contains("offset="))
                  offset = line.replaceFirst("offset=", "");
               if (line.contains("tivoName="))
                  tivoName = line.replaceFirst("tivoName=", "");
               if (line.contains("title="))
                  title = line.replaceFirst("title=", "");
               if (line.matches("^[0-9]+.*")) {
                  String[] l = line.split("\\s+");
                  JSONObject j = new JSONObject();
                  j.put("start", Long.parseLong(l[0]));
                  j.put("end", Long.parseLong(l[1]));
                  cuts.put(j);
               }
            } // while
            if (cuts.length() > 0) {
               JSONObject json = new JSONObject();
               json.put("contentId", contentId);
               json.put("offerId", offerId);
               json.put("offset", offset);
               json.put("tivoName", tivoName);
               json.put("title", title);
               json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
               json.put("cuts", cuts);
               entries.put(json);
            }
            ifp.close();
         } catch (Exception e) {
            error("getEntries - " + e.getMessage());
         }
      }
      return entries;
   }
   
   // Remove AutoSkip entries that no longer have corresponding NPL entries
   public static synchronized void pruneEntries(String tivoName, Stack<Hashtable<String,String>> nplEntries) {
      debug.print("tivoName=" + tivoName + " nplEntries=" + nplEntries);
      if (nplEntries == null)
         return;
      if (nplEntries.size() == 0)
         return;
      
      try {
         int count = 0;
         JSONArray skipEntries = getEntries();
         for (int i=0; i<skipEntries.length(); ++i) {
            JSONObject json = skipEntries.getJSONObject(i);
            if (json.getString("tivoName").equals(tivoName)) {
               Boolean exists = false;
               for (Hashtable<String,String> nplEntry : nplEntries) {
                  if (nplEntry.containsKey("offerId")) {
                     if (nplEntry.get("offerId").equals(json.getString("offerId")))
                        exists = true;
                  }
               }
               if (! exists) {
                  removeEntry(json.getString("contentId"));
                  count++;
               }
            }
         }
         if (count == 0)
            log.warn("No entries found to prune");
      } catch (JSONException e) {
         error("pruneEntries - " + e.getMessage());
      }
   }
   
   // For Skip enabled program use a D press to detect end of 1st commercial point
   /*public static synchronized Long visualDetect(String tivoName) {
      Long point = -1L;
      Remote r2 = new Remote(tivoName);
      if (r2.success) {
         try {
            long starting = 0;
            
            // Save current position
            JSONObject result = r2.Command("Position", new JSONObject());
            if (result != null && result.has("position")) {
               starting = result.getLong("position");
            }
            
            // Jump to time 0
            JSONObject json = new JSONObject();
            json.put("offset", 0);
            result = r2.Command("Jump", json);
            Thread.sleep(900);
            if (result != null) {
               json.remove("offset");
               json.put("event", "actionD");
               // Send D press and collect time information
               result = r2.Command("keyEventSend", json);
               
               // Get position
               Thread.sleep(100);
               result = r2.Command("Position", new JSONObject());
               if (result != null && result.has("position"))
                  point = result.getLong("position");
               
               if (point < 60000) {
                  log.print("Too small - jumping again");
                  // Time likely too small - jump again
                  Thread.sleep(900);
                  // Send D press and collect time information
                  r2.Command("keyEventSend", json);
                  
                  // Get position
                  Thread.sleep(100);
                  result = r2.Command("Position", new JSONObject());
                  if (result != null && result.has("position"))
                     point = result.getLong("position");
               }
               
               // Jump back to starting position
               json.remove("event");
               json.put("offset", starting);
               result = r2.Command("Jump", json);
            }
            
         } catch (Exception e) {
            error("visualDetect - " + e.getMessage());
         }
         r2.disconnect();
      }
      print("visualDetect point: " + toMinSec(point));

      return point;
   }*/
   
   private static synchronized Stack<Hashtable<String,Long>> hashCopy(Stack<Hashtable<String,Long>> orig) {
      debug.print("orig=" + orig);
      Stack<Hashtable<String,Long>> copy = new Stack<Hashtable<String,Long>>();
      for (Hashtable<String,Long> h : orig) {
         Hashtable<String,Long> h_copy = new Hashtable<String,Long>(h);
         copy.push(h_copy);
      }
      return copy;
   }
   
   private static synchronized void print(String message) {
      log.print("AutoSkip: " + message);
   }
   
   private static synchronized void error(String message) {
      log.error("AutoSkip: " + message);
   }

}