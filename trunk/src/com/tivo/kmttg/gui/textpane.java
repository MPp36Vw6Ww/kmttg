package com.tivo.kmttg.gui;

import java.util.Stack;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.tivo.kmttg.main.config;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

public class textpane {
   private WebView p;
   private int BUFFER_SIZE = 250000; // Limit to this many characters
  
   textpane(WebView p) {
      this.p = p;
      this.p.getEngine().loadContent("<body><div id=\"content\"></div></body>");
   }
   
   public WebView getPane() {
      return p;
   }
  
   public void print(String s) {
      appendText(Color.BLACK, s);
      scroll();
   }
  
   public void warn(String s) {
      appendText(Color.BLUE, s);
      scroll();
   }
  
   public void error(String s) {
      appendText(Color.RED, s);
      java.awt.Toolkit.getDefaultToolkit().beep();
      scroll();
   }
  
   public void print(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.BLACK, s.get(i));
      scroll();
   }
  
   public void warn(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.BLUE, s.get(i));
      scroll();
   }
  
   public void error(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.RED, s.get(i));
      java.awt.Toolkit.getDefaultToolkit().beep();
      scroll();
   }
  
   private void scroll() {
      if (p != null) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               try {
                  p.getEngine().executeScript("window.scrollTo(0,document.body.scrollHeight);");
               } catch (Exception e) {}
            }
         });
      }
   }
  
   public void appendText(Color c, String s) {
      if (p != null) {
         Document doc = p.getEngine().getDocument();
         Element content = doc.getElementById("content");
         limitBuffer(content, s.length());
         // Use <pre> tag so as to preserve whitespace
         Element pre = doc.createElement("pre");
         pre.setTextContent(s);
         // NOTE: display: inline prevents newline from being added for <pre> tag
         // NOTE: white-space: pre-wrap allows horizontal work wrapping to avoid horizontal scrollbar
         pre.setAttribute("style", "white-space: pre-wrap; display: inline; color:" + config.gui.getWebColor(c));
         if (content.getChildNodes().getLength() > 0)
            content.appendChild(doc.createElement("br"));
         content.appendChild(pre);
      }
   }
  
   // Limit text pane buffer size by truncating total data size to
   // BUFFER_SIZE or less if needed
   private void limitBuffer(Element content, int incomingDataSize) {
      if (p != null) {
         int doc_length = content.getTextContent().getBytes().length;
         int overLength = doc_length + incomingDataSize - BUFFER_SIZE;
         if (overLength > 0 && doc_length >= overLength) {
            NodeList list = content.getChildNodes();
            int removed=0;
            while( list.getLength() > 0 && removed < overLength ) {
               removed += list.item(0).getTextContent().length();
               content.removeChild(list.item(0));
            }
         }
      }
   }
   
   public void clear() {
      if (p != null) {
         Element content = p.getEngine().getDocument().getElementById("content");
         NodeList list = content.getChildNodes();
         while (list.getLength() > 0) {
            content.removeChild(list.item(0));
         }
      }

   }
}

