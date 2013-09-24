package com.tivo.kmttg.gui;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class deletedTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"SHOW", "DATE", "CHANNEL", "DUR"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
         
   deletedTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new DeletedTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      scroll = new JScrollPane(TABLE);
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableDate && o2 instanceof sortableDate) {
               sortableDate s1 = (sortableDate)o1;
               sortableDate s2 = (sortableDate)o2;
               long l1 = Long.parseLong(s1.sortable);
               long l2 = Long.parseLong(s2.sortable);
               if (l1 > l2) return 1;
               if (l1 < l2) return -1;
               return 0;
            }
            if (o1 instanceof sortableDuration && o2 instanceof sortableDuration) {
               sortableDuration d1 = (sortableDuration)o1;
               sortableDuration d2 = (sortableDuration)o2;
               if (d1.sortable > d2.sortable) return 1;
               if (d1.sortable < d2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(1).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            ListSelectionModel rowSM = (ListSelectionModel)e.getSource();
            int row = rowSM.getMinSelectionIndex();
            if (row > -1) {
               TABLERowSelected(row);
            }
         }
      });
                        
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify dates
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Center justify duration
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.CENTER);
               
      //TABLE.setFillsViewportHeight(true);
      //TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   
   
   // Override some default table model actions
   class DeletedTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public DeletedTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 1) {
            return sortableDate.class;
         }
         if (col == 3) {
            return sortableDuration.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }
   
   private JSONObject GetRowData(int row) {
      return TableUtil.GetRowData(TABLE, row, "DATE");
   }
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(currentTivo, json);
         }
      }
      else if (keyCode == KeyEvent.VK_R) {
         config.gui.remote_gui.recover_deleted.doClick();
      }
      else if (keyCode == KeyEvent.VK_DELETE) {
         config.gui.remote_gui.permDelete_deleted.doClick();
      }
      else if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.printJSON(json);
      } else if (keyCode == KeyEvent.VK_Q) {
         // Web query currently selected entry
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null && json.has("title")) {
            try {
               String title = json.getString("title");
               if (json.has("subtitle"))
                  title = title + " - " + json.getString("subtitle");
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }
         }
      } else {
         // Pass along keyboard action
         e.consume();
      }
   }
   
   private void TABLERowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      // Get column items for selected row 
      sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
      try {
         // Non folder entry so print single entry info
         sortableDuration dur = (sortableDuration)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DUR"));
         String message = TableUtil.makeShowSummary(s, dur);
         String title = "\nDeleted: ";
         if (s.json.has("title"))
            title += s.json.getString("title");
         if (s.json.has("subtitle"))
            title += " - " + s.json.getString("subtitle");
         log.warn(title);
         log.print(message);

         if (config.gui.show_details.isShowing())
            config.gui.show_details.update(currentTivo, s.json);
      } catch (JSONException e) {
         log.error("TABLERowSelected - " + e.getMessage());
         return;
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i)
            o.add(data.getJSONObject(i));
         
         // Reset local entries to new entries
         Refresh(o);
         TableUtil.packColumns(TABLE, 2);
         tivo_data.put(tivoName, data);
         currentTivo = tivoName;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.setTivoName("deleted", tivoName);
            refreshNumber();
         }
      } catch (JSONException e) {
         log.print("Deleted AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         displayFlatStructure(o);
      }
   }
   
   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      TableUtil.clear(TABLE);
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      debug.print("entry=" + entry);
      int cols = TITLE_cols.length;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      try {
         String startString=null, endString=null;
         long start=0, end=0;
         if (entry.has("scheduledStartTime")) {
            startString = entry.getString("scheduledStartTime");
            start = TableUtil.getLongDateFromString(startString);
            endString = entry.getString("scheduledEndTime");
            end = TableUtil.getLongDateFromString(endString);
         } else {
            start = TableUtil.getStartTime(entry);
            end = TableUtil.getEndTime(entry);
         }
         String title = TableUtil.makeShowTitle(entry);
         String channel = TableUtil.makeChannelName(entry);
   
         data[0] = title;
         data[1] = new sortableDate(entry, start);
         data[2] = channel;
         data[3] = new sortableDuration(end-start, false);
         
         TableUtil.AddRow(TABLE, data);
         
         // Adjust column widths to data
         TableUtil.packColumns(TABLE, 2);
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }      
   }   
   
   // Refresh the # SHOWS label in the ToDo tab
   public void refreshNumber() {
      config.gui.remote_gui.label_deleted.setText("" + tivo_data.get(currentTivo).length() + " SHOWS");
   }
      
   // Undelete selected recordings
   public void recoverSingle(final String tivoName) {
      int[] test = TableUtil.GetSelectedRows(TABLE);
      if (test.length > 0) {
         log.print("Recovering individual recordings on TiVo: " + tivoName);
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               int row;
               JSONObject json;
               String title;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  Boolean cont = true;
                  while (cont) {
                     int[] selected = TABLE.getSelectedRows();
                     if (selected.length > 0) {
                        row = selected[0];
                        try {
                           json = GetRowData(row);
                           title = json.getString("title");
                           if (json != null) {
                              JSONObject o = new JSONObject();
                              JSONArray a = new JSONArray();
                              a.put(json.getString("recordingId"));
                              o.put("recordingId", a);
                              json = r.Command("Undelete", o);
                              if (json == null) {
                                 TableUtil.DeselectRow(TABLE, row);
                                 log.error("Failed to recover recording: '" + title + "'");
                              } else {
                                 log.warn("Recovered recording: '" + title + "' on TiVo: " + tivoName);
                                 TableUtil.RemoveRow(TABLE, row);
                                 tivo_data.get(currentTivo).remove(row);
                                 refreshNumber();
                              }
                           }
                        } catch (JSONException e) {
                           log.error("recoverSingle failed - " + e.getMessage());
                        }
                     } else {
                        cont = false;
                     }
                  }
                  r.disconnect();
               }
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      }
   }
   
   // Permanently delete selected recordings
   public void permanentlyDelete(final String tivoName) {
      int[] test = TableUtil.GetSelectedRows(TABLE);
      if (test.length > 0) {
         log.print("Permanently deleting individual recordings on TiVo: " + tivoName);
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               int row;
               JSONObject json;
               String title;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  Boolean cont = true;
                  while (cont) {
                     int[] selected = TABLE.getSelectedRows();
                     if (selected.length > 0) {
                        row = selected[0];
                        try {
                           json = GetRowData(row);
                           if (json != null) {
                              title = json.getString("title");
                              if (json.has("subtitle"))
                                 title += " - " + json.getString("subtitle");
                              JSONObject o = new JSONObject();
                              JSONArray a = new JSONArray();
                              a.put(json.getString("recordingId"));
                              o.put("recordingId", a);
                              json = r.Command("PermanentlyDelete", o);
                              if (json == null) {
                                 TableUtil.DeselectRow(TABLE, row);
                                 log.error("Failed to permanently delete recording: '" + title + "'");
                              } else {
                                 log.warn("Permanently deleted recording: '" + title + "' on TiVo: " + tivoName);
                                 TableUtil.RemoveRow(TABLE, row);
                                 tivo_data.get(currentTivo).remove(row);
                                 refreshNumber();
                              }
                           }
                        } catch (JSONException e) {
                           log.error("permanentlyDelete failed - " + e.getMessage());
                        }
                     } else {
                        cont = false;
                     }
                  }
                  r.disconnect();
               }
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      }
   }
}