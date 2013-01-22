package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

public class createMeta {
   private static HashMap<String,String> tvRatings = null;
   private static HashMap<String,String> humanTvRatings = null;
   private static HashMap<String,String> mpaaRatings = null;
   private static HashMap<String,String> humanMpaaRatings = null;
   
   // Create a pyTivo compatible metadata file from a TiVoVideoDetails xml download
   @SuppressWarnings("unchecked")
   public static Boolean createMetaFile(jobData job, String cookieFile) {
      debug.print("");
      String outputFile = job.metaTmpFile;
      try {
         String[] nameValues = {
               "title", "seriesTitle", "description", "time",
               "mpaaRating", "movieYear", "isEpisode", "recordedDuration",
               "originalAirDate", "episodeTitle", "isEpisodic"
         };
         String[] valuesOnly = {"showingBits", "starRating", "tvRating"};
         String[] arrays = {
               "vActor", "vDirector", "vExecProducer", "vProducer",
               "vProgramGenre", "vSeriesGenre", "vAdvisory", "vHost",
               "vGuestStar", "vWriter", "vChoreographer"
         };
         
         Hashtable<String,Object> data = new Hashtable<String,Object>();
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         Document doc = docBuilder.parse(outputFile);

         // Search for <recordedDuration> elements
         NodeList rdList = doc.getElementsByTagName("recordedDuration");
         if (rdList.getLength() > 0) {
            String value;
            Node n = rdList.item(0);
            if ( n != null) {
               value = n.getTextContent();
               value = Entities.replaceHtmlEntities(value);
               data.put("recordedDuration", value);
               debug.print("recordedDuration" + "=" + value);
            }
         }
         
         // Search for everything under <showing>
         NodeList nlist = doc.getElementsByTagName("showing");
         if (nlist.getLength() > 0) {
            Node showingNode = nlist.item(0);
            String name, value;
            
            // First process nameValues
            for (int k=0; k<nameValues.length; k++) {
               name = nameValues[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  value = n.getTextContent();
                  value = Entities.replaceHtmlEntities(value);
                  data.put(name, value);
                  debug.print(name + "=" + value);
               }
            }
            
            // Process valuesOnly which have a "value" node
            for (int k=0; k<valuesOnly.length; k++) {
               name = valuesOnly[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  value = n.getAttributes().getNamedItem("value").getNodeValue();
                  data.put(name, value);
                  debug.print(name + "=" + value);
               }
            }
            
            // Process arrays which have 1 or more values
            for (int k=0; k<arrays.length; k++) {
               name = arrays[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  Stack<String> values = new Stack<String>();
                  NodeList children = n.getChildNodes();
                  for (int c=0; c<children.getLength(); c++) {
                     value = children.item(c).getTextContent();
                     values.add(value);
                     debug.print(name + "=" + value);
                  }
                  data.put(name, values);
               }
            }
         }
                  
         // Post-process some of the data
         if ( data.containsKey("starRating") )
            data.put("starRating", "x" + data.get("starRating"));
         if ( data.containsKey("tvRating") )
            data.put("tvRating", "x" + data.get("tvRating"));
         // Override isEpisode since it seems to be wrong most of the time
         if ( data.containsKey("isEpisodic") )
            data.put("isEpisode", data.get("isEpisodic"));
         if ( data.containsKey("description") )
            data.put("description", ((String) (data.get("description"))).replaceFirst("Copyright Tribune Media Services, Inc.", ""));
         
         if ( data.containsKey("mpaaRating") ) {
            Hashtable<String,String> map = new Hashtable<String,String>();
            map.put("G", "G1");
            map.put("PG", "P2");
            map.put("PG_13", "P3");
            map.put("R", "R4");
            map.put("X", "X5");
            map.put("NC_17", "N6");
            map.put("NR", "N8");
            String mpaaRating = map.get(data.get("mpaaRating"));
            if (mpaaRating != null)
               data.put("mpaaRating", mpaaRating);            
         }
         
         // Add additional data
         if ( job.episodeNumber != null && job.episodeNumber.length() > 0 )
            data.put("episodeNumber", job.episodeNumber);
         if ( job.displayMajorNumber != null ) {
            // Doesn't like sub-channel #s so strip them out
            // NOTE: New versions of pyTivo are fine with dashes now
            //data.put("displayMajorNumber", job.displayMajorNumber.replaceFirst("^(.+)-.+$", "$1"));
            data.put("displayMajorNumber", job.displayMajorNumber);
         }
         if ( job.callsign != null )
            data.put("callsign", job.callsign);
         if ( job.seriesId != null )
            data.put("seriesId", job.seriesId);
         if ( job.ProgramId != null )
            data.put("programId", job.ProgramId);
         
         // Now write all data to metaFile in pyTivo format
         BufferedWriter ofp = new BufferedWriter(new FileWriter(job.metaFile));
         
         String key;
         String eol = "\r\n";
         for (int i=0; i<nameValues.length; ++i) {
            key = nameValues[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0) {
                  if (key.equals("recordedDuration"))
                     ofp.write("iso_duration : " + data.get(key) + eol);
                  else
                     ofp.write(key + " : " + data.get(key) + eol);
               }
            }
         }
         for (int i=0; i<valuesOnly.length; ++i) {
            key = valuesOnly[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         String[] additional = {"episodeNumber", "displayMajorNumber", "callsign", "seriesId", "programId"};
         for (int i=0; i<additional.length; ++i) {
            key = additional[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         for (int i=0; i<arrays.length; i++) {
            key = arrays[i];
            if (data.containsKey(key)) {
               Stack<String> values = (Stack<String>)data.get(key);
               for (int j=0; j<values.size(); ++j) {
                  ofp.write(key + " : " + values.get(j) + eol);
               }
            }
         }
         
         // Extra name : value data specified in kmttg config
         String extra[] = getExtraMetadata();
         if (extra != null) {
            for (int i=0; i<extra.length; ++i) {
               ofp.write(extra[i] + eol);
            }
         }
         ofp.close();
         
      }
      catch (Exception ex) {
         log.error(ex.toString());
         if (cookieFile != null) file.delete(cookieFile);
         file.delete(outputFile);
         return false;
      }
      
      if (cookieFile != null) file.delete(cookieFile);
      file.delete(outputFile);
      return true;
   }
      
   public static Node getNodeByName(Document doc, Node n, String name) {
      DocumentTraversal docTraversal = (DocumentTraversal)doc;
      TreeWalker iter = docTraversal.createTreeWalker(
         n,
         NodeFilter.SHOW_ALL,                                                 
         null,
         false
      );      
      Node node = null;
      while ( (node=iter.nextNode()) != null ) {
         if (node.getNodeName().equals(name))
            return node;
      }
      return node;
   }
   
   public static void printData(Hashtable<String,Object> data) {
      debug.print("");
      String name;
      Object value;
      log.print("metadata data:");
      for (Enumeration<String> e=data.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         value = data.get(name);
         log.print(name + "=" + value);
      }
   }
   
   public static String[] getExtraMetadata() {
      if (config.metadata_entries != null && config.metadata_entries.length() > 0) {
         String entries = string.removeLeadingTrailingSpaces(config.metadata_entries);
         String tokens[] = entries.split(",");
         String data[] = new String[tokens.length];
         for (int i=0; i<tokens.length; ++i) {
            String nv[] = tokens[i].split(":");
            if (nv.length == 2) {
               String name = string.removeLeadingTrailingSpaces(nv[0]);
               String value = string.removeLeadingTrailingSpaces(nv[1]);
               data[i] = name + " : " + value;
            } else {
               log.error("Invalid setting for 'extra metadata entries': " + config.metadata_entries);
               return null;
            }
         }
         return data;
      }
      return null;
   }
   
   private static void initHashes() {
      if (tvRatings == null) {
         tvRatings = new HashMap<String,String>();
         tvRatings.put("TV-Y7", "1");
         tvRatings.put("TVY7",  "1");
         tvRatings.put("Y7",    "1");
         tvRatings.put("X1",    "1");

         tvRatings.put("TV-Y",  "2");
         tvRatings.put("TVY",   "2");
         tvRatings.put("Y",     "2");
         tvRatings.put("X2",    "2");

         tvRatings.put("TV-G",  "3");
         tvRatings.put("TVG",   "3");
         tvRatings.put("G",     "3");
         tvRatings.put("X3",    "3");

         tvRatings.put("TV-PG", "4");
         tvRatings.put("TVPG",  "4");
         tvRatings.put("PG",    "4");
         tvRatings.put("X4",    "4");

         tvRatings.put("TV-14", "5");
         tvRatings.put("TV14",  "5");
         tvRatings.put("14",    "5");
         tvRatings.put("X5",    "5");

         tvRatings.put("TV-MA", "6");
         tvRatings.put("TVMA",  "6");
         tvRatings.put("MA",    "6");
         tvRatings.put("X6",    "6");

         tvRatings.put("TV-NR", "7");
         tvRatings.put("TVNR",  "7");
         tvRatings.put("NR",    "7");
         tvRatings.put("X7",    "7");
         tvRatings.put("X0",    "7");
      }
      if (humanTvRatings == null) {
         humanTvRatings = new HashMap<String,String>();
         humanTvRatings.put("1", "TV-Y7");
         humanTvRatings.put("2", "TV-Y");
         humanTvRatings.put("3", "TV-G");
         humanTvRatings.put("4", "TV-PG");
         humanTvRatings.put("5", "TV-14");
         humanTvRatings.put("6", "TV-MA");
         humanTvRatings.put("7", "Unrated");
      }
      if (mpaaRatings == null) {
         mpaaRatings = new HashMap<String,String>();
         mpaaRatings.put("G",       "1");
         mpaaRatings.put("G1",      "1");

         mpaaRatings.put("PG",      "2");
         mpaaRatings.put("P2",      "2");

         mpaaRatings.put("PG-13",   "3");
         mpaaRatings.put("PG13",    "3");
         mpaaRatings.put("P3",      "3");

         mpaaRatings.put("R",       "4");
         mpaaRatings.put("R4",      "4");

         mpaaRatings.put("X",       "5");
         mpaaRatings.put("X5",      "5");

         mpaaRatings.put("NC-17",   "6");
         mpaaRatings.put("NC17",    "6");
         mpaaRatings.put("N6",      "6");

         mpaaRatings.put("NR",      "8");
         mpaaRatings.put("UNRATED", "8");
         mpaaRatings.put("N8",      "8");
         mpaaRatings.put("8",       "8");
      }
      if (humanMpaaRatings == null) {
         humanMpaaRatings = new HashMap<String,String>();
         humanMpaaRatings.put("1", "G");
         humanMpaaRatings.put("2", "PG");
         humanMpaaRatings.put("3", "PG-13");
         humanMpaaRatings.put("4", "R");
         humanMpaaRatings.put("5", "X");
         humanMpaaRatings.put("6", "NC-17");
         humanMpaaRatings.put("8", "Unrated");
      }
   }
   
   // This used for mapping to AtomicParsley --contentRating argument
   public static String tvRating2contentRating(String tvRating) {
      initHashes();
      String upperRating = tvRating.toUpperCase();
      String intermediate = null;
      if (tvRatings.containsKey(upperRating))
         intermediate = tvRatings.get(upperRating);
      if (intermediate != null && humanTvRatings.containsKey(intermediate))
         return humanTvRatings.get(intermediate);
      return tvRating;
   }
   
   // This used for mapping to AtomicParsley --contentRating argument   
   public static String mpaaRating2contentRating(String mpaaRating) {
      initHashes();
      String upperRating = mpaaRating.toUpperCase();
      String intermediate = null;
      if (mpaaRatings.containsKey(upperRating))
         intermediate = mpaaRatings.get(upperRating);
      if (intermediate != null && humanMpaaRatings.containsKey(intermediate))
         return humanMpaaRatings.get(intermediate);
      return mpaaRating;
   }

}
