/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package chargen.pros_cons_skills;

import dsatool.gui.GUIUtil;
import dsatool.util.GraphicTableCell;
import dsatool.util.IntegerSpinnerTableCell;
import dsatool.util.Util;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;

public class ProConSkillUtil {
	static void setupTable(final String type, final int additionalSpace, final TableView<ProConOrSkill> table,
			final TableColumn<ProConOrSkill, String> nameColumn,
			final TableColumn<ProConOrSkill, String> descColumn, final TableColumn<ProConOrSkill, String> variantColumn,
			final TableColumn<ProConOrSkill, Integer> valueColumn,
			final TableColumn<ProConOrSkill, Boolean> validColumn, final TableColumn<ProConOrSkill, Boolean> suggestedColumn) {
		GUIUtil.autosizeTable(table, 0, additionalSpace);
		GUIUtil.cellValueFactories(table, "name", "description", "variant", "value", "cost", "valid", "suggested");

		final boolean isCheaperSkills = "Verbilligte Sonderfertigkeiten".equals(type);

		nameColumn.setText(type);
		nameColumn.setCellFactory(c -> new TextFieldTableCell<ProConOrSkill, String>() {
			@Override
			public void updateItem(final String item, final boolean empty) {
				super.updateItem(item, empty);
				if (getTableRow() != null) {
					final ProConOrSkill proOrCon = (ProConOrSkill) getTableRow().getItem();
					if (proOrCon != null) {
						Util.addReference(this, proOrCon.getProOrCon(), 50);
					}
				}
			}
		});

		descColumn.setCellFactory(c -> new GraphicTableCell<ProConOrSkill, String>(false) {
			@Override
			protected void createGraphic() {
				final ObservableList<String> items = FXCollections
						.<String> observableArrayList(getTableView().getItems().get(getIndex()).getFirstChoiceItems(false));
				switch (getTableView().getItems().get(getIndex()).firstChoiceOrText()) {
				case TEXT:
					if (items.size() > 0) {
						final ComboBox<String> c = new ComboBox<>(items);
						c.setEditable(true);
						createGraphic(c, () -> c.getSelectionModel().getSelectedItem(), s -> c.getSelectionModel().select(s));
					} else {
						final TextField t = new TextField();
						createGraphic(t, () -> t.getText(), s -> t.setText(s));
					}
					break;
				case CHOICE:
					final ComboBox<String> c = new ComboBox<>(items);
					createGraphic(c, () -> c.getSelectionModel().getSelectedItem(), s -> c.getSelectionModel().select(s));
					break;
				case NONE:
					final Label l = new Label();
					createGraphic(l, () -> l.getText(), s -> l.setText(s));
					break;
				}
			}
		});
		descColumn.setOnEditCommit(t -> t.getRowValue().setDescription(t.getNewValue()));
		variantColumn.setCellFactory(c -> new GraphicTableCell<ProConOrSkill, String>(false) {
			@Override
			protected void createGraphic() {
				final ObservableList<String> items = FXCollections
						.<String> observableArrayList(getTableView().getItems().get(getIndex()).getSecondChoiceItems(false));
				switch (getTableView().getItems().get(getIndex()).secondChoiceOrText()) {
				case TEXT:
					if (items.size() > 0) {
						final ComboBox<String> c = new ComboBox<>(items);
						c.setEditable(true);
						createGraphic(c, () -> c.getSelectionModel().getSelectedItem(), s -> c.getSelectionModel().select(s));
					} else {
						final TextField t = new TextField();
						createGraphic(t, () -> t.getText(), s -> t.setText(s));
					}
					break;
				case CHOICE:
					final ComboBox<String> c = new ComboBox<>(items);
					createGraphic(c, () -> c.getSelectionModel().getSelectedItem(), s -> c.getSelectionModel().select(s));
					break;
				case NONE:
					final Label l = new Label();
					createGraphic(l, () -> l.getText(), s -> l.setText(s));
					break;
				}
			}
		});
		variantColumn.setOnEditCommit(t -> t.getRowValue().setVariant(t.getNewValue()));
		valueColumn.setCellFactory(o -> new IntegerSpinnerTableCell<ProConOrSkill>(1, 999, 1, false) {
			@Override
			public void updateItem(final Integer item, final boolean empty) {
				final ProConOrSkill proOrCon = (ProConOrSkill) getTableRow().getItem();
				if (empty || item.intValue() == Integer.MIN_VALUE || proOrCon == null) {
					setText("");
					setGraphic(null);
				} else if (isCheaperSkills) {
					min = proOrCon.getNumCheaper() - proOrCon.getActual().getIntOrDefault("temporary:AdditionalLevels", 0);
					super.updateItem(item, empty);
				} else {
					min = proOrCon.getMinValue();
					max = proOrCon.getMaxValue();
					step = proOrCon.getStep();
					super.updateItem(item, empty);
				}
			}
		});
		validColumn.setCellFactory(tableColumn -> new TextFieldTableCell<ProConOrSkill, Boolean>() {
			@Override
			public void updateItem(final Boolean valid, final boolean empty) {
				super.updateItem(valid, empty);
				@SuppressWarnings("all")
				final TableRow<ProConOrSkill> row = getTableRow();
				row.getStyleClass().remove("invalid");
				if (!empty && !valid) {
					row.getStyleClass().remove("valid");
					row.getStyleClass().add("invalid");
				}
			}
		});
		suggestedColumn.setCellFactory(tableColumn -> new TextFieldTableCell<ProConOrSkill, Boolean>() {
			@Override
			public void updateItem(final Boolean cheaper, final boolean empty) {
				super.updateItem(cheaper, empty);
				@SuppressWarnings("all")
				final TableRow<ProConOrSkill> row = getTableRow();
				final ProConOrSkill item = row.getItem();
				row.getStyleClass().remove("valid");
				if (!empty && item != null && item.isValid() && cheaper) {
					row.getStyleClass().remove("invalid");
					row.getStyleClass().add("valid");
				}
			}
		});
	}
}
