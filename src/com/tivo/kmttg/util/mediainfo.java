package com.tivo.kmttg.util;

import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;

public class mediainfo {
   
   // Use mediainfo cli to get video information from given video file
   // Returns null if undetermined, a hash with video info otherwise
   public static Hashtable<String,String> getVideoInfo(String videoFile) {
      if (! file.isFile(config.mediainfo))
         return null;
      if (! file.isFile(videoFile))
         return null;
      // Use mediainfo command to get video information      
      Stack<String> command = new Stack<String>();
      command.add(config.mediainfo);
      command.add(videoFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         
         // Parse stdout
         Stack<String> l = process.getStdout();
         if (l.size() > 0) {
            Hashtable<String,String> info = new Hashtable<String,String>();
            String[] sections = {"General", "Video", "Audio", "Menu", "Text"};
            String section = "";
            String line;
            info.put("container", "mpeg");
            info.put("video", "mpeg2video");
            for (int i=0; i<l.size(); ++i) {
               line = l.get(i);
               for (int j=0; j<sections.length; ++j) {
                  if (line.matches("^" + sections[j] + "\\s*$")) {
                     section = sections[j];
                  }
               }
               if (section.equals("General") && line.matches("^Format\\s+:.+$")) {
                  // Format                                   : MPEG-TS
                  String fields[] = line.split(":");
                  String container = fields[1].toLowerCase();
                  container = container.replaceAll(" ", "");
                  container = container.replaceFirst("-ps", "");
                  container = container.replaceAll("-", "");
                  info.put("container", container);
                  if (container.equals("mpeg4"))
                     info.put("container", "mp4");
               }
               if (section.equals("General") && line.matches("^Duration\\s+:.+$")) {
                  // Duration                                 : 1h 43mn
                  // Duration                                 : 5mn 0s
                  String fields[] = line.split(":");
                  String duration = fields[1].toLowerCase();
                  duration = duration.replaceFirst(" ", "");
                  fields = line.split(" ");
                  if (fields.length > 0) {
                     int h=0, m=0, s=0;
                     for (int j=0; j<fields.length; ++j) {
                        if (fields[j].matches("^\\d+h$"))
                           h = Integer.parseInt(fields[j].replaceFirst("h", ""));
                        if (fields[j].matches("^\\d+mn$"))
                           m = Integer.parseInt(fields[j].replaceFirst("mn", ""));
                        if (fields[j].matches("^\\d+s$"))
                           s = Integer.parseInt(fields[j].replaceFirst("s", ""));
                     }
                     int dur = 60*60*h + 60*m + s;
                     info.put("duration", "" + dur);
                  }
               }
               if (section.equals("Video") && line.matches("^Format\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String video = fields[1].toLowerCase();
                  video = video.replaceAll(" ", "");
                  info.put("video", video);
                  if (video.equals("mpegvideo"))
                     info.put("video", "mpeg2video");
                  if (video.contains("avc"))
                     info.put("video", "h264");
               }
               if (section.equals("Video") && line.matches("^Width\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String x = fields[1].toLowerCase();
                  x = x.replaceAll(" ", "");
                  x = x.replaceAll("pixels", "");
                  info.put("x", x);
               }
               if (section.equals("Video") && line.matches("^Height\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String y = fields[1].toLowerCase();
                  y = y.replaceAll(" ", "");
                  y = y.replaceAll("pixels", "");
                  info.put("y", y);
               }
               if (section.equals("Video") && line.matches("^Display\\s+aspect.+$")) {
                  // Display aspect ratio                     : 4:3
                  String dar = line.replaceFirst(" ", "");
                  String fields[] = dar.split(":");
                  if (fields.length == 3) {
                     info.put("DAR_x", fields[1]);
                     info.put("DAR_y", fields[2]);
                  }
               }
            }
            if (info.size() == 0)
               info = null;
            return info;
         }
      }
      return null;
   }

}