package com.tivo.kmttg.gui.dialog;

import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class resolveConflict {
   VBox components;
   Label label;
   JSONArray conflicts;
   int tuners;
   
   resolveConflict(JSONArray conflicts, int tuners) {
      this.conflicts = conflicts;
      this.tuners = tuners;
      createComponents();      
   }
   
   private void createComponents() {
      components = new VBox();
      components.getChildren().add(new Label(""));
      try {
         for (int i=0; i<conflicts.length(); ++i) {
            JSONObject json = conflicts.getJSONObject(i);
            String text = rnpl.formatEntry(json);
            CheckBox box = new CheckBox(text);
            box.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  // Limited number of tuners means only that many shows can be enabled at a time
                  checkTuners();
               }
            });
            components.getChildren().add(box);
         }
      } catch (JSONException e) {
         log.error("Conflicts dialog error: " + e.getMessage());
      }
   }
   
   public JSONArray promptUser(String title) {
      try {
         label.setText(title);
         Dialog<?> dialog = new Dialog<>();
         dialog.initOwner(config.gui.getFrame());
         config.gui.setFontSize(dialog, config.FontSize);
         dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
         dialog.setTitle("Resolve conflicts");
         dialog.getDialogPane().setContent(components);
         Optional<?> response = dialog.showAndWait();
         if (response != null && response.get().equals(ButtonType.OK)) {
            for (int i=0; i<conflicts.length(); ++i) {
               JSONObject json = conflicts.getJSONObject(i);
               CheckBox box = (CheckBox)components.getChildren().get(i+1);
               if (box.isSelected()) {
                  json.put("__record__", "yes");
               }
            }
            return conflicts;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("Resolve conflicts dialog error: " + e.getMessage());
         return null;
      }
   }
   
   private void checkTuners() {
      int count = 0;
      for (int i=0; i<conflicts.length(); ++i) {
         CheckBox box = (CheckBox)components.getChildren().get(i+1);
         if (box.isSelected()) {
            count++;
            if (count > tuners) {
               log.warn("Can only record " + tuners + " shows at once on this box");
               box.setSelected(false);
            }
         }
      }
   }
}
